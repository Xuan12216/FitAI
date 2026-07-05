package com.xuan.fitai.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class ImageAnalyzer : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        // Close the image to prevent camera freezing. 
        // Real-time classification can be implemented here if needed.
        image.close()
    }
}
