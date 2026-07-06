package com.xuan.fitai.ai

import com.xuan.fitai.data.model.ModelDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader {

    suspend fun downloadModel(
        url: String,
        token: String?,
        destinationFile: File,
        onProgress: (ModelDownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tempFile = File("${destinationFile.absolutePath}.download")
        var connection: HttpURLConnection? = null

        try {
            destinationFile.parentFile?.mkdirs()
            tempFile.delete()
            destinationFile.delete()

            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                tempFile.delete()
                onProgress(ModelDownloadState.Failed("Server response error: $responseCode"))
                return@withContext
            }

            val totalSize = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L

            onProgress(ModelDownloadState.Downloading(0f, 0L, totalSize))

            connection.inputStream.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    while (true) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        val progress = if (totalSize > 0) totalBytesRead.toFloat() / totalSize else 0f
                        onProgress(ModelDownloadState.Downloading(progress, totalBytesRead, totalSize))
                    }
                    outputStream.flush()
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                onProgress(ModelDownloadState.Failed("File write failed: empty file"))
                return@withContext
            }

            if (totalSize > 0 && totalBytesRead < totalSize) {
                tempFile.delete()
                onProgress(ModelDownloadState.Failed("Download interrupted: incomplete file (${totalBytesRead} / ${totalSize} bytes)"))
                return@withContext
            }

            if (tempFile.renameTo(destinationFile)) {
                onProgress(ModelDownloadState.Completed)
            } else {
                tempFile.delete()
                onProgress(ModelDownloadState.Failed("Failed to save model file"))
            }
        } catch (e: Exception) {
            tempFile.delete()
            destinationFile.delete()
            onProgress(ModelDownloadState.Failed(e.localizedMessage ?: "Download failed"))
        } finally {
            connection?.disconnect()
        }
    }
}
