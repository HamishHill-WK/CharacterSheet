package com.example.charactersheet

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentCameraBinding
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


//this fragment handles camera and image analysis operations -hh
class CameraFragment : Fragment() {

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var recyclerView: FrameLayout

    private var resultsString = ""

    private var noPerm = false

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        // Shut down our background executor
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

        val cameraPermissionResultReceiver = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            noPerm = if (it) {
                // permission granted
                startCamera()
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
            startCamera()
        } else {
            this.activity?.let {
                ActivityCompat.requestPermissions(
                    it, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
        fragmentCameraBinding.imageCaptureButton.setOnClickListener {
            fragmentCameraBinding.imageCaptureButton.visibility = View.INVISIBLE

            takePhoto()

        }
        fragmentCameraBinding.removeLastButton.setOnClickListener { removeLast() }
        fragmentCameraBinding.proceedButton.setOnClickListener{ proceed() }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = fragmentCameraBinding.root

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        //fragmentCameraBinding.imageCaptureButton.isClickable = false

        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){

                    val msg = "Photo capture succeeded" // : ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

                    val image: InputImage
                    try {
                        image = InputImage.fromFilePath(requireContext(), output.savedUri!!)
                        val bitmap =
                            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, output.savedUri!!)//creates bitmap from image saved in gallery
                        val imageRotation = image.rotationDegrees

                        DetectObjs(bitmap, imageRotation)
                        //textRecog(image)
                        requireContext().contentResolver.delete(output.savedUri!!, null, null)//remove save image from gallery
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        )
    }

    private fun isPixelIn(img: Bitmap, results: List<Detection>): Bitmap {

        var cropLeft = (results[0].boundingBox.left - 500).toInt()
        var cropTop = (results[0].boundingBox.top - 500).toInt()
        var cropH = (results[0].boundingBox.height() + 1000).toInt()
        var cropW = (results[0].boundingBox.width() + 1000).toInt()
        if (cropLeft < 0)
            cropLeft = 1

        if (cropTop < 0)
            cropTop = 1

        if(cropH + cropTop > img.height)
            cropH = img.height - cropTop

        if(cropW + cropLeft > img.width)
            cropW = img.width - cropLeft

        for (y in cropTop until cropTop+cropH)  //loop from 0 to img.height-1
            for (c in cropLeft until cropLeft+cropW) {//loop from 0 to img.width-1
                if (results.size == 1) {//if theres only one bounding box to check against
                    val vectorAB = listOf(
                        results[0].boundingBox.right.toInt() - results[0].boundingBox.left.toInt(),
                        0
                    )
                    val vectorAC = listOf(
                        0,
                        results[0].boundingBox.bottom.toInt() - results[0].boundingBox.top.toInt()
                    )
                    val vectorAM = listOf(
                        c - results[0].boundingBox.left.toInt(),
                        y - results[0].boundingBox.top.toInt()
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

        saveMediaToStorage(img2)
        return img2
    }

    private var textChanged = false;
    private fun DetectObjs(image: Bitmap, rot: Int) {
        val options = ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build()).setScoreThreshold(0.80f)
            .setMaxResults(1)
            .build()
        val objectDetector = ObjectDetector.createFromFileAndOptions(
            context, "android(6).tflite", options
        )

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
            val image1: Bitmap = isPixelIn(image.copy(Bitmap.Config.ARGB_8888, true), results)  //all pixels outside the detected object's bounding box have their colour set to black
            textRecog(image1, rot)//edited bitmap is passed to text recognition algorithm
    }

    private fun getRes(): String { return resultsString }
    private fun setRes(str: String) {
        if(!textChanged){
            textChanged = true
            resultsString= str
            fragmentCameraBinding.resultsTextCam.text = getRes()
        }
    }

    private fun proceed()
    {
        val action = CameraFragmentDirections.actionCameraFragmentToPopUpFragment(
            getRes()
        )
        view?.findNavController()?.navigate(action)
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()
            // Select back camera as a default
            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                val cam = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))

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
        resultsString = resultsString.substringBeforeLast(',')
        fragmentCameraBinding.resultsTextCam.text  =resultsString
    }

    private fun textRecog(image: InputImage): Task<Text> {
        Log.d(TAG, "starting thread")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return recognizer.process(image)
    }

    private fun textRecog (image: Bitmap, rot: Int ) {
        var text1 = ""
        for( x in 0 until 4){
            val img = InputImage.fromBitmap(image, x * rot)
            val y = textRecog(img)
            y.addOnSuccessListener {
                Log.d(TAG, "$x thread finished")
                text1 = y.result.text
                if (text1 != "") {
                    if (text1 == "A") //added for common case where `4` is often detected as capital `A`
                        text1 = "4"

                    if(text1 in (1..20).toString()){
                        if (resultsString != "" )
                            setRes("$resultsString,$text1")

                        else if (resultsString == "") {
                            setRes(text1)
                        }
                    }
                }
                if (x == 3){
                    fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE

                    if (textChanged)
                        textChanged = false

                    if (!textChanged){

                        text1 = classifyObjects(image, rot)
                        if(text1 in (1..6).toString()) {
                            if (resultsString != "")
                                setRes("$resultsString,$text1")
                            else if (resultsString == "")
                                setRes(text1)
                        }
                        else
                            Toast.makeText(requireContext(), "No text found, please try again", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun classifyObjects (box: Bitmap, rot: Int): String {

        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-rot / 90))
                .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(box))

        val options = ImageClassifier.ImageClassifierOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build())//.setScoreThreshold(0.65F)
            .setMaxResults(1)
            .build()

        val modelFile= "D6E20_NoAug_model2_89_fp16.tflite"
        val modelFile1= "E10_model10_fp16.tflite"

        val imageClassifier = ImageClassifier.createFromFileAndOptions(
            context, modelFile, options
        )

  val imageClassifier1 = ImageClassifier.createFromFileAndOptions(
            context, modelFile1, options
        )

        val results: List<Classifications> = imageClassifier.classify(tensorImage)
        val results1: List<Classifications> = imageClassifier1.classify(tensorImage)

        if(results!=null) {
            Log.d(TAG,"results: $results")
            Log.d(TAG,"results: $results1")

        }
        return results1[0].categories.toString()
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