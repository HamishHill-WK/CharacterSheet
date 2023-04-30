package com.example.charactersheet

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentCameraBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


//this fragment handles camera and image analysis operations -hh
class CameraFragment : Fragment() {
    private lateinit var cameraExecutor: ExecutorService
    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!
    private lateinit var recyclerView: FrameLayout

    private lateinit var cameraHandler: CameraHandler
    lateinit var objectDetector: ObjectDetector
    lateinit var textRecognizer: TextRecognizer
    lateinit var bitmapEditor: BitmapProcessor

    private var resultsString = ""
    private var noPerm = false

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        val view = fragmentCameraBinding.root

        cameraExecutor = Executors.newSingleThreadExecutor()
        cameraHandler = CameraHandler(requireContext(), viewLifecycleOwner)

        val cameraPermissionResultReceiver = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            noPerm = if (it) {
                // permission granted
                cameraHandler.startCamera(fragmentCameraBinding.viewFinder)
                false
            } else {
                true
            }
        }
        //theses two sections both check that the application has the required permissions
        //I've left both in for now because I can't remember which one works

        //  ** DELETE ONE OF THESE LATER **

        if(noPerm)
            cameraPermissionResultReceiver.launch(Manifest.permission.CAMERA)

        if (allPermissionsGranted()) {
            cameraHandler.startCamera(fragmentCameraBinding.viewFinder)
        } else {
            this.activity?.let {
                ActivityCompat.requestPermissions(
                    it, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }

        fragmentCameraBinding.imageCaptureButton.setOnClickListener {
            fragmentCameraBinding.imageCaptureButton.visibility = View.INVISIBLE
            textChanged = false
            takePhoto()
        }
        fragmentCameraBinding.removeLastButton.setOnClickListener { removeLast() }
        fragmentCameraBinding.proceedButton.setOnClickListener{ proceed() }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = fragmentCameraBinding.root

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build())
            .setScoreThreshold(0.80f)
            .setMaxResults(1)
            .build()
        objectDetector = ObjectDetector.createFromFileAndOptions(
            context, "android(6).tflite", options
        )

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        bitmapEditor = BitmapProcessor()
        //handler = MLhandler(requireContext())
        //handler.initMLKit()

    }

    private fun takePhoto() {
        Log.d(TAG, "options ")

        cameraHandler.takePhoto { image, rot->
            saveMediaToStorage(image)
            DetectObjs(image, rot)
        }
    }

    private fun isPixelIn(img: Bitmap, faces: List<Detection>, cropBuffer: Float): Bitmap {


        Log.d(TAG, "here ${faces[0].boundingBox.top + 100}, ${faces[0].boundingBox.left + 100}" +
                "${faces[0].boundingBox.right + 200} ${faces[0].boundingBox.bottom +200} ${faces.size}")

        var cropLeft = faces[0].boundingBox.left.toInt() -100
        var cropTop = faces[0].boundingBox.top.toInt() -100
        var cropH = faces[0].boundingBox.height().toInt() +200
        var cropW = faces[0].boundingBox.width().toInt() +200

        Log.d(TAG, "$cropH, $cropW, $cropLeft, $cropTop")

        Log.d(TAG, "$cropH, $cropW, $cropTop, $cropLeft")


        Log.d(TAG, "${img.height} ${img.width}")

        if (cropLeft < 0)
            cropLeft = 1

        if (cropTop < 0)
            cropTop = 1

        if(cropH + cropTop > img.height)
            cropH = img.height - cropTop

        if(cropW + cropLeft > img.width)
            cropW = img.width - cropLeft

        Log.d(TAG, "$cropH, $cropW, $cropLeft, $cropTop")

        for (y in cropTop until cropTop+cropH)  //loop from 0 to img.height-1
            for (c in cropLeft until cropLeft+cropW) {//loop from 0 to img.width-1
                if (faces.size == 1) {//if theres only one bounding box to check against
                    val vectorAB = listOf(
                        faces[0].boundingBox.right.toInt() - faces[0].boundingBox.left.toInt(),
                        0
                    )
                    val vectorAC = listOf(
                        0,
                        faces[0].boundingBox.bottom.toInt() - faces[0].boundingBox.top.toInt()
                    )
                    val vectorAM = listOf(
                        c - faces[0].boundingBox.left.toInt(),
                        y - faces[0].boundingBox.top.toInt()
                    )
                    //if AM.AB > AB.AB
                    if (vectorAM[0] * vectorAB[0] + vectorAM[1] * vectorAB[1] > vectorAB[0] * vectorAB[0] + vectorAB[1] * vectorAB[1]) {
                        img.setPixel(c, y, Color.BLACK)
                        continue
                    }// or AM.AC > AC.AC
                    if (vectorAM[0] * vectorAC[0] + vectorAM[1] * vectorAC[1] > vectorAC[0] * vectorAC[0] + vectorAC[1] * vectorAC[1]) {
                        img.setPixel(
                            c,
                            y,
                            Color.BLACK
                        ) //pixel is outside detected object bounding box, set colour to black for text recognition algorithm
                        continue
                    }
                    //if AM.AB < 0
                    if ((vectorAM[0] * vectorAB[0] + vectorAM[1] * vectorAB[1]) < 0) {  //outside bounding box
                        img.setPixel(c, y, Color.BLACK) //set to black
                        continue //go to next pixel
                    }
                    //or AM.AC < 0
                    if (vectorAM[0] * vectorAC[0] + vectorAM[1] * vectorAC[1] < 0)
                        img.setPixel(c, y, Color.BLACK)
                }
            }

        val img2 = Bitmap.createBitmap(
            img, cropLeft, cropTop,
            cropW, cropH
        )

        return img
    }

    private var textChanged = false
    private fun DetectObjs(image: Bitmap, rot: Int) {
        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-rot / 90))
                .build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        val results: List<Detection> = objectDetector.detect(tensorImage)
        if(results.isEmpty()){
            Toast.makeText(requireContext(), "no Dice detected, please try again", Toast.LENGTH_SHORT).show()
            fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
            return
        }

        textRecognitionTask(InputImage.fromBitmap(image, rot)){mlkitResults ->
            Log.d(TAG, "task started ${mlkitResults.textBlocks.size}")
            if (mlkitResults.textBlocks.size < 100) {
                for (x in mlkitResults.textBlocks) {
                    var out =false
                    if(x.boundingBox?.top!! < results[0].boundingBox.top ){
                        out = true
                    }
                    if(x.boundingBox?.left!! < results[0].boundingBox.left ){
                        out = true
                    }
                    if(x.boundingBox?.right!! > results[0].boundingBox.right ){
                        out = true
                    }
                    if(x.boundingBox?.bottom!! > results[0].boundingBox.bottom ) {
                        out = true
                    }
                    if(out) {
                        Log.d(TAG, "out ${results[0].boundingBox.top + 100}, ${results[0].boundingBox.left + 100}" +
                                "${results[0].boundingBox.right + 200} ${results[0].boundingBox.bottom +200}")
                    }
                    if(!out){
                        Log.d(TAG, "${x.text} inside box $results ")
                        setRes(x.text)
                        fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                        //latch.countDown()
                        return@textRecognitionTask
                    }
                    //fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                    Log.d(TAG, "end loop $x")
                }
            }
            else{
                Log.d(TAG, "no text ")
            }

            Log.d(TAG, " done detection task ")

            if(!textChanged) {
                Log.d(TAG, "no text inside box ")
                Log.d(TAG, "out ${results[0].boundingBox.top + 100}, ${results[0].boundingBox.left + 100}" +
                        "${results[0].boundingBox.right + 200} ${results[0].boundingBox.bottom +200}")

                val image1: Bitmap =bitmapEditor.isPixelIn(
                    image.copy(Bitmap.Config.ARGB_8888, true),
                    results
                    ,100F)  //all pixels outside the detected object's bounding box have their colour set to black

                bitmaps.add(image1)
                for(n in 1.. 9) {
                    bitmaps.add(RotateBitmap(image1, (n * 40).toFloat()))
                }
                textRecog(bitmaps, rot)//edited bitmap is passed to text recognition algorithm

            }
            textChanged = false
        }
    }

    fun textRecognitionTask(image: InputImage, callback: (Text) -> Unit) {
        textRecognizer.process(image)
            .addOnSuccessListener { result ->
                callback(result)
            }
            .addOnFailureListener { exec->
                Log.d(MLhandler.TAG, exec.toString())
            }
    }

    private var bitmaps: MutableList<Bitmap> = mutableListOf()

    fun RotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun getRes(): String { return resultsString }
    private fun setRes(str: String) {
        if(!textChanged){
            textChanged = true
            if(resultsString.isNotEmpty())
                resultsString += str
            else
                resultsString= str
            fragmentCameraBinding.resultsTextCam.text = resultsString
            fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
        }
    }

    private fun proceed() {
        val action = CameraFragmentDirections.actionCameraFragmentToPopUpFragment(
            getRes()
        )
        view?.findNavController()?.navigate(action)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    fun saveMediaToStorage(bitmap: Bitmap) {
        //Generating a file name
        val filename = "${System.currentTimeMillis()}.jpg"

        //Output stream
        var fos: OutputStream? = null

        //For devices running android >= Q
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //getting the contentResolver
            context?.contentResolver?.also { resolver ->

                //Content resolver will process the contentvalues
                val contentValues = ContentValues().apply {

                    //putting file information in content values
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                //Inserting the contentValues to contentResolver and getting the Uri
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                //Opening an outputstream with the Uri that we got
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            //These for devices running on android < Q
            //So I don't think an explanation is needed here
            val imagesDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }

        fos?.use {
            //Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            //context?.toast("Saved to Photos")
        }
    }

    private fun removeLast(){
        if(resultsString.length == 1){
            resultsString = ""
            fragmentCameraBinding.resultsTextCam.text  = ""
        }
        if(resultsString.length > 1){
        resultsString = resultsString.substringBeforeLast(',')
        fragmentCameraBinding.resultsTextCam.text  =resultsString
        }
    }

    private fun letterFilter(text: String): String { //this function filters out any characters which are not a digit from 0-9
        val filteredText = StringBuilder()
        for (c in text)
            if (c.isDigit())
                filteredText.append(c)

        return filteredText.toString()
    }

    private var resultsText: MutableList<String> = mutableListOf()

    private fun textRecog (images: MutableList<Bitmap>, rot: Int ) {
    var text1 = ""
    var taskCount = 0
        for (image in images) {
            saveMediaToStorage(image)
            val img = InputImage.fromBitmap(image, rot)
            textRecognitionTask(img) { mlkitResults ->
                if (mlkitResults.text != "") {
                    text1 = if (mlkitResults.text == "A") { // added for common case where `4` is often detected as capital `A`
                        "4"
                    } else {
                        mlkitResults.text
                    }

                    if (text1.length > 1) {
                        text1 = letterFilter(text1) // removes any letter characters from string
                    }
                }

                taskCount++;

                if(taskCount == images.size)
                {
                    if (!textChanged) {
                        Toast.makeText(
                            requireContext(),
                            "No text found, please try again",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    textChanged = false
                    fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                    resultsText.clear()
                }
                Log.d(TAG,"$taskCount $text1")
            }
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}