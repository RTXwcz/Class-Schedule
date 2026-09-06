package com.kebiao.app.ocr

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class OcrModelManager(context: Context) {
    private val root = File(context.filesDir, "ocr").apply { mkdirs() }

    fun modelFile(modelId: String): File = File(root, "$modelId.onnx")

    fun download(modelId: String, url: String, expectedSha256: String): File {
        val target = modelFile(modelId)
        val temp = File(root, "$modelId.tmp")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 60_000
        connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        val actual = sha256(temp)
        require(actual.equals(expectedSha256, ignoreCase = true)) { "OCR model checksum mismatch" }
        check(temp.renameTo(target)) { "Unable to activate OCR model" }
        return target
    }

    fun delete(modelId: String) { modelFile(modelId).delete() }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
