package com.lyse.mediaplayer

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class LuminosityAnalyzer(private val onLuma: (Double) -> Unit) : ImageAnalysis.Analyzer {

    private var lastReportMs = 0L

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastReportMs >= 500) {
            lastReportMs = now
            val buffer = image.planes[0].buffer
            buffer.rewind()
            val size = buffer.remaining()
            var sum = 0L
            var count = 0
            var i = 0
            while (i < size) {
                sum += buffer.get(i).toInt() and 0xFF
                count++
                i += 16
            }
            if (count > 0) onLuma(sum.toDouble() / count)
        }
        image.close()
    }
}