package com.example.charactersheet

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
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

    private lateinit var maxDice: String
    private var numOfDice  = 1 // the total number of dice the player wants to roll
    private var numResults = 1 //size of each batch to detect
    private var resultsString = ""

    private var noPerm = false

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                Log.d(TAG, "permissionganted")
                startCamera()
                false
            } else {
                Log.d(TAG, "no permiss")
                true
            }
        }
        //theses two sections both check that the application has the required permissions
        //I've left both in for now because I can't remember which one works

        //  ** DELETE ONE OF THESE LATER **

        if(noPerm)
            cameraPermissionResultReceiver.launch(Manifest.permission.CAMERA)

        if (allPermissionsGranted()) {
            Log.d(TAG, "permissionganted")
            startCamera()
        } else {
            Log.d(TAG, "no permiss")
            this.activity?.let {
                ActivityCompat.requestPermissions(
                    it, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        }
        fragmentCameraBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        fragmentCameraBinding.removeLastButton.setOnClickListener { removeLast() }

        fragmentCameraBinding.thresholdPlus.setOnClickListener{   //increase the number of dice the user will roll
            if(numOfDice < 10){
                numOfDice +=1
            fragmentCameraBinding.thresholdValue.text = numOfDice.toString()
            }
        }

        fragmentCameraBinding.thresholdMinus.setOnClickListener{
            if ( numOfDice > 1) {
                numOfDice -= 1
                fragmentCameraBinding.thresholdValue.text = numOfDice.toString()
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = fragmentCameraBinding.root

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        fragmentCameraBinding.imageCaptureButton.isClickable = false
        val imageCapture = imageCapture ?: return
        if(numOfDice == 0){
            Log.d(TAG, "here  ")
            val action = CameraFragmentDirections.actionCameraFragmentToPopUpFragment(resultsString)
            view?.findNavController()?.navigate(action)
        }

        Log.d(TAG, "take photo called")
        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
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
                    Log.d(TAG, msg)

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
        fragmentCameraBinding.imageCaptureButton.isClickable = true
    }

    //private var resultList: MutableList<Bitmap> = mutableListOf() //list of bitmaps containing top face image

    private fun isPixelIn(img: Bitmap, results: List<Detection>): Bitmap {
        val list: MutableList<Rect> = mutableListOf()
        if(results.size > 1)//if there is more than one result
            for (r in results)
                list.add( Rect(r.boundingBox.left.toInt(), r.boundingBox.top.toInt()
                    , r.boundingBox.right.toInt(), r.boundingBox.bottom.toInt()))

        for (y  in 0 until img.height)  //loop from 0 to img.height-1
            for (c  in 0 until img.width){//loop from 0 to img.width-1

                if(results.size == 1){//if theres only one bounding box to check against
                    val vectorAB = listOf(results[0].boundingBox.right.toInt() -results[0].boundingBox.left.toInt(), 0)
                    val vectorAC = listOf(0, results[0].boundingBox.bottom.toInt() -results[0].boundingBox.top.toInt())
                    val vectorAM =  listOf(c - results[0].boundingBox.left.toInt(), y - results[0].boundingBox.top.toInt())

                    //if AM.AB > AB.AB
                    if(vectorAM[0] * vectorAB[0] + vectorAM[1] * vectorAB[1] > vectorAB[0] * vectorAB[0] + vectorAB[1] * vectorAB[1] ) {
                        img.setPixel(c, y, Color.BLACK)
                        continue
                    }// or AM.AC > AC.AC
                    if(vectorAM[0] * vectorAC[0] + vectorAM[1] * vectorAC[1] > vectorAC[0] * vectorAC[0] + vectorAC[1] * vectorAC[1]) {
                        img.setPixel(c, y, Color.BLACK) //pixel is outside detected object bounding box, set colour to black for text recognition algorithm
                        continue
                    }
                    //if AM.AB < 0
                    if((vectorAM[0] * vectorAB[0] + vectorAM[1] * vectorAB[1]) < 0 ) {  //outside bounding box
                        img.setPixel(c, y, Color.BLACK) //set to black
                        continue //go to next pixel
                    }
                    //or AM.AC < 0
                    if(vectorAM[0] * vectorAC[0] + vectorAM[1] * vectorAC[1] <0 )
                        img.setPixel(c, y, Color.BLACK)
                }

                if(list.size > 1)//if there is more than one result in the image
                    for(i in list){
                        if(c> i.left || c < i.right &&
                            y > i.top|| y < i.bottom) //if pixel is inside a bounding box then continue to next loop
                                continue

                        else    //else set colour to black
                            img.setPixel(c, y, Color.BLACK)
                }
            }


        saveMediaToStorage(img)
        return img
    }

    //debug function for saving edited bitmaps to device gallery **DELETE LATER**
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

    private fun DetectObjs(image: Bitmap, rot: Int) {
        val options = ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build()).setScoreThreshold(0.80f)
            .setMaxResults(numResults)
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
            return
        }
            val image1: Bitmap = isPixelIn(image.copy(Bitmap.Config.ARGB_8888, true), results)  //all pixels outside the detected object's bounding box have their colour set to black
            textRecog(image1, rot)//edited bitmap is passed to text recognition algorithm
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun startCamera() {
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

    private fun removeLast(){
        numOfDice += 1
        resultsString = resultsString.substringBeforeLast(',')
        fragmentCameraBinding.resultsTextCam.text  =resultsString
    }

    private fun textRecog (image: Bitmap, rot: Int ) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        for (x in 0 until 4){
            val rotation = x * 90
        val img = InputImage.fromBitmap(image, rotation)
            val result = recognizer.process(img)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                // ...
                if (visionText.text == "" && x == 3) {
                    Toast.makeText(
                        requireContext(),
                        "no text found, please try again",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "no text in image ")
                }
                else if (visionText.text != "")
                {
                    if (resultsString != "") {    //if the results string already has a result in it then the next result is added with a dividing comma
                        resultsString += ",${visionText.text}"
                        Toast.makeText(
                            requireContext(),
                            "I got ${visionText.text}",
                            Toast.LENGTH_SHORT
                        ).show()
                        numOfDice -= 1
//                        fragmentCameraBinding.resultsTextCam.text = resultsString
                    }
                    else {
                        Toast.makeText(
                            requireContext(),
                            "I got ${visionText.text}",
                            Toast.LENGTH_SHORT
                        ).show()
                        resultsString = visionText.text //otherwise assign the value
                        Log.d(TAG, "herea gain $visionText")
                        numOfDice -= 1
                        if(numResults == 1) {
                            val action = CameraFragmentDirections.actionCameraFragmentToPopUpFragment(
                                visionText.text
                            )
                            view?.findNavController()?.navigate(action)
                        }
                    }
                    if(numOfDice == 0){
                        fragmentCameraBinding.imageCaptureButton.text = "Proceed"
                    }
                }
            }
            .addOnFailureListener { e -> Log.d(TAG, "no text $e ")
            }
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        val NUMBER = "1"
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