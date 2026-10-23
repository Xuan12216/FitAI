package com.xuan.fitai.data.model

object ContextTokenLimits {
    const val MIN = 256
    const val MAX = 8192
    const val DEFAULT = 4096
    val range = MIN..MAX
    val presets = listOf(512, 1024, 2048, 4096, 8192)
}
