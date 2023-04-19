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
import androidx.camera.core.*
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
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


//this fragment handles camera and image analysis operations -hh
typealias LumaListener = (luma: Double) -> Unit

class CameraFragment : Fragment() {

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var recyclerView: FrameLayout


    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    var noPerm = false
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        val view = fragmentCameraBinding.root

        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraPermissionResultReceiver = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                // permission granted
                Log.d(TAG, "permissionganted")
                startCamera()
                noPerm =false
            } else {
                Log.d(TAG, "no permiss")
                noPerm = true
            }
        }

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

        _fragmentCameraBinding!!.imageCaptureButton.setOnClickListener { takePhoto() }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = fragmentCameraBinding.root

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

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
                    val msg = "Photo capture succeeded: ${output.savedUri}"
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
    }

    var resultList: MutableList<Bitmap> = mutableListOf()

    private fun DetectObjs(image: Bitmap, rot: Int) {
        var options = ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build()).setScoreThreshold(0.70f)
            .setMaxResults(1)
            .build()
        var objectDetector = ObjectDetector.createFromFileAndOptions(
            context, "android(6).tflite", options
        )

        val imageProcessor =
            ImageProcessor.Builder()
                .add(Rot90Op(-rot / 90))
                .build()
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        var results: List<Detection> = objectDetector.detect(tensorImage)
        if(results.isEmpty())
            Log.d(TAG, "no face")
        for((x,d) in results.withIndex()) {
            Log.d(TAG, ("${results[x].boundingBox.left  /*+ (results[x].boundingBox.height()/2).toInt()*/}, "+
                     "${(results[x].boundingBox.top) /*- (results[x].boundingBox.width()/2)).toInt()*/}"))

            resultList.add(Bitmap.createScaledBitmap(Bitmap.createBitmap(image, //0, 0,
                (results[x].boundingBox.left.toInt()),
                (results[x].boundingBox.top.toInt() ),
                results[x].boundingBox.width().toInt(),
                results[x].boundingBox.height().toInt() ),
                results[x].boundingBox.width().toInt() ,
                results[x].boundingBox.height().toInt(),true ))
        }

        for((x,d) in resultList.withIndex()) {
            Log.d(TAG, "${d.width}, ${d.height}")


            val image1 = InputImage.fromBitmap(d, rot)
            textRecog(image1)
        }

        resultList.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()
            // Select back camera as a default
            //val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
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

    private fun textRecog (img: InputImage) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val result = recognizer.process(img)
            .addOnSuccessListener { visionText ->
                // Task completed successfully
                // ...
                if (visionText.text == "") {
                    Toast.makeText(
                        requireContext(),
                        "no text found, please try again",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "no text in image ")
                }
                else
                {
                    Log.d(TAG, "detected: " + visionText.text)
                    val action = CameraFragmentDirections.actionCameraFragmentToPopUpFragment(visionText.text)

                    view?.findNavController()?.navigate(action)
                }
            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
                Log.d(TAG, "no text $e ")
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

private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {

        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        listener(luma)

        image.close()
    }
}
