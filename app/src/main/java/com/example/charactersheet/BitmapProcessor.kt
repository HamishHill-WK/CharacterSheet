package com.example.charactersheet

import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.task.vision.detector.Detection

class BitmapProcessor {

    fun isPixelIn(img: Bitmap, results: List<Detection>, cropBuffer: Float): Bitmap {
        var cropLeft = (results[0].boundingBox.left - cropBuffer).toInt()
        var cropTop = (results[0].boundingBox.top - cropBuffer).toInt()
        var cropH = (results[0].boundingBox.height() + cropBuffer*2).toInt()
        var cropW = (results[0].boundingBox.width() + cropBuffer*2).toInt()
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

        return img2
    }

}