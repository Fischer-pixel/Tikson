package com.example.nvbar.ui.home

import android.graphics.Bitmap
import android.graphics.Color.rgb
import kotlin.math.roundToInt

class Process {

    public fun floatArrayToBitmap(floatArray: FloatArray, width: Int, height: Int) : Bitmap {

        // Create empty bitmap in RGBA format
        val bmp: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height * 4)

        // mapping smallest value to 0 and largest value to 255
        val maxValue = floatArray.max() ?: 1.0f
        val minValue = floatArray.min() ?: -1.0f
        val delta = maxValue-minValue

        // Define if float min..max will be mapped to 0..255 or 255..0
        val conversion = { v: Float -> ((v-minValue)/delta*255.0f).roundToInt()}

        // copy each value from float array to RGB channels and set alpha channel
        for (i in 0 until width * height) {
            val r = conversion(floatArray[i])
            val g = conversion(floatArray[i+width*height])
            val b = conversion(floatArray[i+2*width*height])
            pixels[i] = rgb(r, g, b)
        }
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)

        return bmp
    }
}