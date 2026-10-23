package com.xuan.fitai.ai
import com.xuan.fitai.data.model.ModelLoadState
fun canScanFood(classifier: ModelLoadState, gemma: ModelLoadState, vision: Boolean): Boolean =
    classifier == ModelLoadState.Loaded || (gemma == ModelLoadState.Loaded && vision)
