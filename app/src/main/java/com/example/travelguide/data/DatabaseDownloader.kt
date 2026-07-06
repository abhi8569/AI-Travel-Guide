package com.example.travelguide.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object DatabaseDownloader {
    fun downloadFile(urlStr: String, destinationFile: File): Flow<DownloadState> = flow {
        emit(DownloadState.Progress(0f))
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                return@flow
            }

            val fileLength = connection.contentLength
            val directory = destinationFile.parentFile
            if (directory != null && !directory.exists()) {
                directory.mkdirs()
            }

            val input = BufferedInputStream(url.openStream(), 8192)
            val output = FileOutputStream(destinationFile)

            val data = ByteArray(1024)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                if (fileLength > 0) {
                    emit(DownloadState.Progress(total.toFloat() / fileLength.toFloat()))
                }
            }

            output.flush()
            output.close()
            input.close()

            emit(DownloadState.Success)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(DownloadState.Error(e.localizedMessage ?: "Unknown download error"))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)
}

sealed class DownloadState {
    data class Progress(val progress: Float) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
