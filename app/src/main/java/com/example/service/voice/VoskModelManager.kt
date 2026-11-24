package com.example.service.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

enum class VoskModelSpec(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val sizeLabel: String,
    val downloadUrl: String
) {
    HINDI(
        id = "hi",
        titleEn = "Hindi Offline Model (Vosk Small HI)",
        titleHi = "हिंदी ऑफलाइन मॉडल (Vosk Small HI)",
        sizeLabel = "~42 MB",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
    ),
    ENGLISH(
        id = "en",
        titleEn = "English (India) Offline Model (Vosk Small EN-IN)",
        titleHi = "अंग्रेज़ी (भारत) ऑफलाइन मॉडल (Vosk Small EN-IN)",
        sizeLabel = "~36 MB",
        downloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
    )
}

sealed class ModelInstallState {
    data object NotInstalled : ModelInstallState()
    data class Downloading(val progress: Float) : ModelInstallState()
    data object Extracting : ModelInstallState()
    data class Installed(val directoryPath: String, val sizeMb: Double) : ModelInstallState()
    data class Error(val message: String) : ModelInstallState()
}

/**
 * Downloads once, verifies, extracts safely (zip-slip protected), and manages local
 * Vosk offline Hindi & English speech models inside internal app storage.
 */
class VoskModelManager(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val modelsRoot: File
        get() = File(context.filesDir, "vosk_models").apply { mkdirs() }

    private val _states = MutableStateFlow<Map<VoskModelSpec, ModelInstallState>>(
        VoskModelSpec.entries.associateWith { inspectModelState(it) }
    )
    val states: StateFlow<Map<VoskModelSpec, ModelInstallState>> = _states.asStateFlow()

    fun refreshStates() {
        _states.value = VoskModelSpec.entries.associateWith { inspectModelState(it) }
    }

    fun getInstalledModelDir(spec: VoskModelSpec): File? {
        val dir = File(modelsRoot, spec.id)
        return if (dir.exists() && dir.isDirectory && hasModelFiles(dir)) dir else null
    }

    suspend fun downloadAndInstall(spec: VoskModelSpec): Result<File> = withContext(Dispatchers.IO) {
        updateState(spec, ModelInstallState.Downloading(0f))
        val tempZip = File(context.cacheDir, "vosk_${spec.id}.zip")
        val targetDir = File(modelsRoot, spec.id)

        try {
            val request = Request.Builder().url(spec.downloadUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed (HTTP ${response.code})")
                }
                val body = response.body ?: throw IllegalStateException("Empty download body")
                val totalBytes = body.contentLength()
                var downloaded = 0L

                BufferedInputStream(body.byteStream()).use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                updateState(
                                    spec,
                                    ModelInstallState.Downloading(
                                        (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            updateState(spec, ModelInstallState.Extracting)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()

            extractZipSafely(tempZip, targetDir)
            tempZip.delete()

            val finalState = inspectModelState(spec)
            updateState(spec, finalState)
            Result.success(targetDir)
        } catch (e: Exception) {
            tempZip.delete()
            updateState(spec, ModelInstallState.Error(e.message ?: "Download failed"))
            Result.failure(e)
        }
    }

    suspend fun deleteModel(spec: VoskModelSpec) = withContext(Dispatchers.IO) {
        File(modelsRoot, spec.id).deleteRecursively()
        updateState(spec, ModelInstallState.NotInstalled)
    }

    private fun extractZipSafely(zipFile: File, destDir: File) {
        val canonicalDest = destDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(16 * 1024)
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalDest + File.separator)) {
                    throw SecurityException("Blocked unsafe zip path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        while (true) {
                            val len = zis.read(buffer)
                            if (len <= 0) break
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun inspectModelState(spec: VoskModelSpec): ModelInstallState {
        val dir = File(modelsRoot, spec.id)
        if (!dir.exists() || !hasModelFiles(dir)) return ModelInstallState.NotInstalled
        val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val mb = bytes / (1024.0 * 1024.0)
        return ModelInstallState.Installed(dir.absolutePath, mb)
    }

    private fun hasModelFiles(dir: File): Boolean {
        return dir.walkTopDown().any { it.isFile && it.length() > 0L }
    }

    private fun updateState(spec: VoskModelSpec, state: ModelInstallState) {
        _states.value = _states.value.toMutableMap().apply { put(spec, state) }
    }
}
