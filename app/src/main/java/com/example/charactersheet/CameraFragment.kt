package com.example.charactersheet

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.IOException
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
    private lateinit var cameraHandler: CameraHandler
    private lateinit var bitmapProcessor: BitmapProcessor
    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        Log.d(TAG, "destroy called")
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
        cameraHandler = CameraHandler(requireContext(), viewLifecycleOwner)

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

    private lateinit var objectDetector : ObjectDetector
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = fragmentCameraBinding.root

        val options = ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build()).setScoreThreshold(0.80f)
            .setMaxResults(1)
            .build()
        objectDetector = ObjectDetector.createFromFileAndOptions(
            context, "android(6).tflite", options
        )

        bitmapProcessor = BitmapProcessor(requireContext())
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

    private var textChanged = false
    private fun DetectObjs(image: Bitmap, rot: Int) {

        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-rot / 90))
                .build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        Log.d(TAG, " here ")

        val results: List<Detection> = objectDetector.detect(tensorImage)
        if(results.isEmpty()){
            Toast.makeText(requireContext(), "no Dice detected, please try again", Toast.LENGTH_SHORT).show()
            fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
            return
        }
           textRecognitionTask(InputImage.fromBitmap(image, rot)){Text->
                mlkitResults = Text
               Log.d(TAG, mlkitResults.text)
                Log.d(TAG, "task started ${mlkitResults.textBlocks.size}")
                if (mlkitResults.textBlocks.size in 1..99){
                for (x in Text.textBlocks){
                    var out =false
                    if(x.boundingBox?.top!! < results[0].boundingBox.top - 100f){
                            out = true
                        }
                    if(x.boundingBox?.left!! < results[0].boundingBox.left -100f){
                        out = true
                    }
                    if(x.boundingBox?.right!! > results[0].boundingBox.right + 100f){
                        out = true
                    }
                    if(x.boundingBox?.bottom!! > results[0].boundingBox.bottom + 100f ) {
                        out = true
                    }

                    if(out) {
                        Log.d(TAG, "${x.text} ${x.boundingBox} not inside box $results ")

                        Log.d(TAG, "out")
                    }
                    if(!out){
                        Log.d(TAG, "${x.text} ${x.boundingBox} inside box $results ")
                        setRes(letterFilter(x.text))
                        fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                        textChanged = false
                        //return@addOnSuccessListener
                        return@textRecognitionTask
                    }
                    //fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                    }
                }
               else{
                   Log.d(TAG, "no text")
                    textChanged = false
               }

            if(!textChanged) {
                Log.d(TAG, "no text inside box ")
                val image1: Bitmap = bitmapProcessor.isPixelIn(
                    image.copy(Bitmap.Config.ARGB_8888, true),
                    results
                ,100F)  //all pixels outside the detected object's bounding box have their colour set to black

                bitmaps.add(image1)
                for(n in 1.. 9) {
                    bitmaps.add(bitmapProcessor.RotateBitmap(image1, (n * 40).toFloat()))
                }
                //for (i in bitmaps) {

                  //  saveMediaToStorage(i)
                    textRecog(bitmaps, rot)//edited bitmap is passed to text recognition algorithm
                    //}
                }
               textChanged = false
               fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
            }
    }

    private var bitmaps: MutableList<Bitmap> = mutableListOf()

    private lateinit var mlkitResults : Text
    private fun getRes(): String { return resultsString }
    private fun setRes(str: String) {
        if(textChanged)
            Log.d(TAG, "textChanged")
        if(!textChanged){
            textChanged = true

            if(resultsString.isNotEmpty()) {
                resultsString += ",$str"
                fragmentCameraBinding.resultsTextCam.text = getRes()
            }
            else{
                resultsString = str
                fragmentCameraBinding.resultsTextCam.text = getRes()
            }
            fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
        }
    }

    private fun proceed() {
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
                cameraProvider.bindToLifecycle(
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

    private fun removeLast(){
        resultsString = resultsString.substringBeforeLast(',')
        fragmentCameraBinding.resultsTextCam.text  =resultsString
    }

    private fun textRecognitionTask(image: InputImage, callback: (Text) -> Unit) {
        textRecognizer.process(image)
            .addOnSuccessListener { result ->
                callback(result)
            }
            .addOnFailureListener {
            }
    }

    private fun letterFilter(text: String): String {
        val filteredText = StringBuilder()

        if(text == "Sl")
            return "15"

        for (c in text) {
            if(c.toString() == "!")
                filteredText.append("1")

            if(c.toString() == "L")
                filteredText.append("7")

            if(c.toString() == "A")
                filteredText.append("4")

            if(c.toString() == "S")
                filteredText.append("5")

            if (c.isDigit()) {
                filteredText.append(c)
            }
        }
        var returnString: String
        returnString = filteredText.toString()
        if(returnString == "02" || returnString == "05")
            returnString = "20"
        else
            for( i in 0..9)
                if(filteredText.toString() == "${i}1" || filteredText.toString() == "${i}!")
                    returnString ="1${i}"

        return returnString
    }

    private var resultsText: MutableList<String> = mutableListOf()

    private fun textRecog(images: MutableList<Bitmap>, rot: Int) {
        var text1: String
        var taskCount = 0
        for (image in images) {
            val img = InputImage.fromBitmap(image, rot)
            textRecognitionTask(img) { mlkitResults ->
                if (mlkitResults.text != "") {
                    text1 = mlkitResults.text

                    text1 = letterFilter(text1) // removes any letter characters from string

                    if(text1.isNotEmpty())
                        if(text1.toInt() in 0..20)
                            resultsText.add(text1)
                }
                taskCount++
                // Check if all tasks are complete
                if (taskCount == images.size) {
                    if(resultsText.size >=2){
                        val mostCommonString = resultsText.groupingBy { it }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key
                        if (mostCommonString != null) {
                            Log.d(TAG, "")
                            textChanged = false
                            setRes(mostCommonString)
                        }
                    }
                    else
                        setRes(resultsText[0])

                    // Do something when all images are processed
                    if (!textChanged)
                        Toast.makeText(
                            requireContext(),
                            "No text found, please try again",
                            Toast.LENGTH_SHORT
                        ).show()

                    textChanged = false
                    fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                    resultsText.clear()
                }
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