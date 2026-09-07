package com.kebiao.app.ocr

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

class OcrModelManager(context: Context) {
    enum class ModelId(val wireName: String, val displayName: String) {
        TINY("pp-ocrv6-tiny", "PP-OCRv6 tiny"),
        SMALL("pp-ocrv6-small", "PP-OCRv6 small"),
    }

    data class Asset(val filename: String, val url: String, val sizeBytes: Long, val sha256: String)
    data class ModelSpec(val id: ModelId, val revision: String, val assets: List<Asset>) {
        val sizeBytes: Long get() = assets.sumOf { it.sizeBytes }
    }
    data class ModelFiles(val spec: ModelSpec, val directory: File) {
        val detection: File get() = File(directory, "det.onnx")
        val recognition: File get() = File(directory, "rec.onnx")
        val dictionary: File get() = File(directory, "rec.yml")
    }
    data class DownloadProgress(val modelId: ModelId, val downloadedBytes: Long, val totalBytes: Long)

    private val root = File(context.applicationContext.noBackupFilesDir, "ocr-models")

    fun spec(id: ModelId): ModelSpec = MODEL_SPECS.getValue(id)

    /** Cheap UI check. Inference verifies every file's hash before loading it. */
    fun isInstalled(id: ModelId): Boolean {
        val spec = spec(id)
        val dir = directory(spec)
        return File(dir, "READY").isFile && spec.assets.all { File(dir, it.filename).length() == it.sizeBytes }
    }

    /** Call only in response to the user's explicit model download command. */
    suspend fun download(id: ModelId, onProgress: (DownloadProgress) -> Unit = {}): ModelFiles =
        withContext(Dispatchers.IO) {
            modelMutex.withLock {
                val spec = spec(id)
                require(spec.sizeBytes in 1..MAX_PACKAGE_BYTES)
                if (isInstalled(id) && verify(directory(spec), spec)) {
                    onProgress(DownloadProgress(id, spec.sizeBytes, spec.sizeBytes))
                    return@withLock ModelFiles(spec, directory(spec))
                }
                check(root.isDirectory || root.mkdirs()) { "Unable to create OCR model directory" }
                // Only inactive remnants can exist while the process-wide model mutex is held.
                root.listFiles()?.filter { it.name.startsWith(".staging-") }?.forEach { it.deleteRecursively() }
                check(root.usableSpace >= spec.sizeBytes + MIN_FREE_BYTES) { "Insufficient storage for OCR model" }
                val staging = File(root, ".staging-${UUID.randomUUID()}")
                check(staging.mkdir()) { "Unable to create OCR download directory" }
                try {
                    var completed = 0L
                    onProgress(DownloadProgress(id, 0, spec.sizeBytes))
                    for (asset in spec.assets) {
                        downloadAsset(asset, File(staging, asset.filename)) { bytes ->
                            onProgress(DownloadProgress(id, completed + bytes, spec.sizeBytes))
                        }
                        completed += asset.sizeBytes
                    }
                    currentCoroutineContext().ensureActive()
                    File(staging, "READY").writeText(spec.revision, Charsets.US_ASCII)
                    val destination = directory(spec)
                    // A previously valid package is returned above; this only replaces invalid data.
                    if (destination.exists()) check(destination.deleteRecursively()) { "Unable to replace invalid OCR model" }
                    check(staging.renameTo(destination)) { "Unable to activate OCR model" }
                    ModelFiles(spec, destination)
                } finally {
                    if (staging.exists()) staging.deleteRecursively()
                }
            }
        }

    suspend fun delete(id: ModelId): Unit = withContext(Dispatchers.IO) {
        modelMutex.withLock {
            val dir = directory(spec(id))
            check(!dir.exists() || dir.deleteRecursively()) { "Unable to delete OCR model" }
        }
    }

    /** Serializes model removal with inference and closes the TOCTOU gap after verification. */
    internal suspend fun <T> withInstalledModel(id: ModelId, block: suspend (ModelFiles) -> T): T =
        withContext(Dispatchers.IO) {
            modelMutex.withLock {
                val spec = spec(id)
                check(isInstalled(id)) { "Download the selected OCR model first" }
                check(verify(directory(spec), spec)) { "OCR model is damaged; delete and download it again" }
                block(ModelFiles(spec, directory(spec)))
            }
        }

    private fun directory(spec: ModelSpec): File = File(root, "${spec.id.wireName}-${spec.revision}")

    private suspend fun verify(dir: File, spec: ModelSpec): Boolean {
        for (asset in spec.assets) {
            currentCoroutineContext().ensureActive()
            val file = File(dir, asset.filename)
            if (!file.isFile || file.length() != asset.sizeBytes) return false
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            if (digest.digest().toHex() != asset.sha256) return false
        }
        return true
    }

    private suspend fun downloadAsset(asset: Asset, target: File, onProgress: (Long) -> Unit) {
        val connection = openHttps(asset.url)
        try {
            val length = connection.contentLengthLong
            check(length < 0 || length == asset.sizeBytes) { "Unexpected OCR model download size" }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    downloaded += count
                    check(downloaded <= asset.sizeBytes) { "OCR model exceeds expected size" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    onProgress(downloaded)
                }
                output.fd.sync()
            } }
            check(downloaded == asset.sizeBytes) { "Incomplete OCR model download" }
            check(digest.digest().toHex() == asset.sha256) { "OCR model checksum mismatch" }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun openHttps(address: String): HttpsURLConnection {
        var url = URL(address)
        repeat(6) {
            currentCoroutineContext().ensureActive()
            require(url.protocol == "https" && url.userInfo == null) { "OCR models require HTTPS" }
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                when (val status = connection.responseCode) {
                    200 -> return connection
                    301, 302, 303, 307, 308 -> {
                        // Official Hugging Face URLs redirect to signed HTTPS model storage.
                        val location = connection.getHeaderField("Location") ?: throw IOException("Missing OCR redirect")
                        url = URL(url, location)
                        connection.disconnect()
                    }
                    else -> throw IOException("OCR model download failed (HTTP $status)")
                }
            } catch (t: Throwable) {
                connection.disconnect()
                throw t
            }
        }
        throw IOException("Too many OCR model redirects")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        private val modelMutex = Mutex()
        private const val BUFFER_BYTES = 64 * 1024
        private const val MIN_FREE_BYTES = 16L * 1024 * 1024
        private const val MAX_PACKAGE_BYTES = 64L * 1024 * 1024
        private val MODEL_SPECS = mapOf(
            ModelId.TINY to ModelSpec(ModelId.TINY, "official-20260611", listOf(
                Asset("det.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_det_onnx/resolve/2ba1506c0380b8f0b03dd142459aac66d4421f6c/inference.onnx", 1780590, "193bab7a04fca699a6c82e6abb5b81bdb28177f0abd4062552b04908dafb19f8"),
                Asset("rec.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx/resolve/2612ab37152ae0a677521bae4e1e3d4fb4cf7c30/inference.onnx", 4462639, "9ef676d6ed3c88256a2d92c640c44f25b0c40947e111b14b8be8f594091563e6"),
                Asset("rec.yml", "https://huggingface.co/PaddlePaddle/PP-OCRv6_tiny_rec_onnx/resolve/2612ab37152ae0a677521bae4e1e3d4fb4cf7c30/inference.yml", 55571, "66170210bad538e83fff3c4a3867e547d6bf20b50d64b20347c4b913f3034ea1"),
            )),
            ModelId.SMALL to ModelSpec(ModelId.SMALL, "official-20260611", listOf(
                Asset("det.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_det_onnx/resolve/28fe5895c24fd108c19eb3e8479f4ab385fbfc62/inference.onnx", 9880512, "d73e0058b7a8086bbd57f3d10b8bcd4ff95363f67e06e2762b5e814fe9c9410e"),
                Asset("rec.onnx", "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.onnx", 21159378, "5435fd747c9e0efe15a96d0b378d5bd157e9492ed8fd80edf08f30d02fa24634"),
                Asset("rec.yml", "https://huggingface.co/PaddlePaddle/PP-OCRv6_small_rec_onnx/resolve/b8f84f0b80c529de40b4fbb3544b84fa7233a513/inference.yml", 150579, "ab078671bb49f06228eadccd34f1bb501e157f7a047095ffb943ba81512c77d1"),
            )),
        )
    }
}
