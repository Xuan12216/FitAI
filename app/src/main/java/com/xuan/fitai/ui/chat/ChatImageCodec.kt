package com.xuan.fitai.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Encodes the selected images into one Room BLOB so they can be rendered after sending. */
object ChatImageCodec {
    private const val MAGIC = 0x46494131 // FIA1
    private const val MAX_IMAGES = 16
    private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

    fun encode(images: List<Bitmap>): ByteArray? {
        if (images.isEmpty()) return null

        val output = ByteArrayOutputStream()
        val imagesToEncode = images.take(MAX_IMAGES)
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(imagesToEncode.size)
            imagesToEncode.forEach { bitmap ->
                val imageOutput = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, imageOutput)
                val bytes = imageOutput.toByteArray()
                data.writeInt(bytes.size)
                data.write(bytes)
            }
        }
        return output.toByteArray()
    }

    fun decode(encoded: ByteArray?): List<Bitmap> {
        if (encoded == null || encoded.isEmpty()) return emptyList()

        return try {
            DataInputStream(ByteArrayInputStream(encoded)).use { data ->
                if (data.readInt() != MAGIC) return@use emptyList<Bitmap>()
                val count = data.readInt()
                if (count !in 1..MAX_IMAGES) return@use emptyList<Bitmap>()

                buildList {
                    repeat(count) {
                        val size = data.readInt()
                        if (size !in 1..MAX_IMAGE_BYTES || size > data.available()) {
                            return@use emptyList<Bitmap>()
                        }
                        val bytes = ByteArray(size)
                        data.readFully(bytes)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::add)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
