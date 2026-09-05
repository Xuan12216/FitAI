package com.xuan.fitai.data.model

object ContextTokenLimits {
    const val MIN = 256
    const val MAX = 32_000
    const val DEFAULT = 1_024
    val range = MIN..MAX
    val presets = listOf(256, 512, 1024, 2000)
}
