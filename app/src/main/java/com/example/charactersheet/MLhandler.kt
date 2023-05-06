package com.example.charactersheet

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector

class MLhandler (private val context: Context){

    lateinit var objectDetector: ObjectDetector
    lateinit var textRecognizer: TextRecognizer

    fun initMLKit() {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().useGpu().build())
            .setScoreThreshold(0.80f)
            .setMaxResults(1)
            .build()
        objectDetector = ObjectDetector.createFromFileAndOptions(
            context, "android(6).tflite", options
        )

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun getTextFromImages(images: List<Bitmap>, rot: Int, callback: (List<String>) -> Unit) {
        val textResults = mutableListOf<String>()
        var taskCount = 0
        for (image in images) {
            val img = InputImage.fromBitmap(image, rot)
            textRecognizer.process(img)
                .addOnSuccessListener { visionText ->
                    if (!visionText.textBlocks.isNullOrEmpty()) {
                        var text = ""
                        for (block in visionText.textBlocks) {
                            text += block.text + " "
                        }
                        text = text.trim()
                        textResults.add(text)
                        Log.d(TAG, "Text found: $text")
                    }
                    taskCount++
                    if (taskCount == images.size) {
                        callback(textResults)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed: ${e.localizedMessage}")
                    taskCount++
                    if (taskCount == images.size) {
                        callback(textResults)
                    }
                }
        }
    }

    fun textRecognitionTask(image: InputImage, callback: (Text) -> Unit) {
        textRecognizer.process(image)
            .addOnSuccessListener { result ->
                callback(result)
            }
            .addOnFailureListener { exec->
                Log.d(TAG, exec.toString())
            }
    }

    /*fun findText (detectionRes: Rect){
        textRecognitionTask(InputImage.fromBitmap(image, rot)){mlkitResults ->
            if (mlkitResults.textBlocks.size < 100) {
                for (x in mlkitResults.textBlocks) {
                    var out =false
                    if(x.boundingBox?.top!! < detectionRes.boundingBox.top ){
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
                        Log.d(CameraFragment.TAG, "out")
                    }
                    if(!out){
                        setRes(x.text)
                        fragmentCameraBinding.imageCaptureButton.visibility = View.VISIBLE
                        return@textRecognitionTask
                    }
                    Log.d(TAG, "end loop $x")
                }
            }
            else{
                Log.d(TAG, "no text ")
            }

            Log.d(TAG, " done detection task ")

            if(!textChanged) {
                Log.d(TAG, "no text inside box ")
                val image1: Bitmap = isPixelIn(
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
    }*/

    companion object{
        const val TAG = "MLhandler.kt"
    }
}