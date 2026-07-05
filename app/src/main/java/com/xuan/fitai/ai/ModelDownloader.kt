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
        try {
            destinationFile.parentFile?.mkdirs()

            val connectionUrl = URL(url)
            val connection = connectionUrl.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            
            // Add Hugging Face token to header if provided
            if (!token.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                onProgress(ModelDownloadState.Failed("伺服器回應錯誤: $responseCode"))
                return@withContext
            }

            val totalSize = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            onProgress(ModelDownloadState.Downloading(0f, 0L, totalSize))

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                val progress = if (totalSize > 0) totalBytesRead.toFloat() / totalSize else 0f
                onProgress(ModelDownloadState.Downloading(progress, totalBytesRead, totalSize))
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            if (destinationFile.exists() && destinationFile.length() > 0) {
                if (totalSize > 0 && totalBytesRead < totalSize) {
                    destinationFile.delete()
                    onProgress(ModelDownloadState.Failed("下載中斷: 檔案大小不完整 (${totalBytesRead} / ${totalSize} bytes)"))
                } else {
                    onProgress(ModelDownloadState.Completed)
                }
            } else {
                onProgress(ModelDownloadState.Failed("檔案寫入失敗，大小為0"))
            }

        } catch (e: Exception) {
            onProgress(ModelDownloadState.Failed(e.localizedMessage ?: "下載出錯"))
        }
    }
}
