package com.klipers.video

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity() {
    private var selectedVideoUri: Uri? = null

    private lateinit var tvSelectedVideo: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etVideoUrl: EditText
    private lateinit var etStart: EditText
    private lateinit var etEnd: EditText
    private lateinit var etRecipe: EditText

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        selectedVideoUri = uri
        tvSelectedVideo.text = getString(R.string.selected_video, uri.toString())
    }

    private val pickRecipeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val content = readTextFromUri(uri)
            if (content.isBlank()) {
                appendStatus("Recipe file kosong atau tidak bisa dibaca")
            } else {
                etRecipe.setText(content)
                appendStatus("Recipe berhasil dimuat dari file")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSelectedVideo = findViewById(R.id.tvSelectedVideo)
        tvStatus = findViewById(R.id.tvStatus)
        etVideoUrl = findViewById(R.id.etVideoUrl)
        etStart = findViewById(R.id.etStart)
        etEnd = findViewById(R.id.etEnd)
        etRecipe = findViewById(R.id.etRecipe)

        findViewById<Button>(R.id.btnPickVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        findViewById<Button>(R.id.btnDownload).setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isEmpty()) {
                appendStatus("URL wajib diisi")
            } else {
                downloadVideo(url)
            }
        }

        findViewById<Button>(R.id.btnManualCut).setOnClickListener {
            lifecycleScope.launch {
                runManualCut()
            }
        }

        findViewById<Button>(R.id.btnLoadRecipe).setOnClickListener {
            pickRecipeLauncher.launch(arrayOf("text/*"))
        }

        findViewById<Button>(R.id.btnAutoCut).setOnClickListener {
            lifecycleScope.launch {
                runAutoCut()
            }
        }
    }

    private suspend fun runManualCut() {
        val sourceUri = selectedVideoUri
        if (sourceUri == null) {
            appendStatus("Pilih video sumber terlebih dahulu")
            return
        }

        val start = RecipeParser.parseTimestamp(etStart.text.toString())
        val end = RecipeParser.parseTimestamp(etEnd.text.toString())

        if (start == null || end == null || end <= start) {
            appendStatus("Format waktu manual tidak valid. Contoh: 00:01:00")
            return
        }

        val outputDir = ensureOutputDir()
        val outputFile = File(outputDir, "manual_${timestamp()}.mp4")
        val inputFile = copyUriToCache(sourceUri)

        appendStatus("Memproses manual cut...")
        val command = listOf(
            "-y",
            "-ss", start.toString(),
            "-to", end.toString(),
            "-i", inputFile.absolutePath,
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-c:a", "aac",
            "-movflags", "+faststart",
            outputFile.absolutePath
        ).joinToString(" ")

        val success = executeFfmpeg(command)
        if (success) {
            appendStatus("Manual cut selesai: ${outputFile.absolutePath}")
        } else {
            appendStatus("Manual cut gagal")
        }
    }

    private suspend fun runAutoCut() {
        val sourceUri = selectedVideoUri
        if (sourceUri == null) {
            appendStatus("Pilih video sumber terlebih dahulu")
            return
        }

        val recipe = etRecipe.text.toString()
        val segments = try {
            RecipeParser.parseRecipe(recipe)
        } catch (e: IllegalArgumentException) {
            appendStatus(e.message ?: "Recipe tidak valid")
            return
        }

        if (segments.isEmpty()) {
            appendStatus("Recipe belum diisi")
            return
        }

        val outputDir = ensureOutputDir()
        val inputFile = copyUriToCache(sourceUri)
        appendStatus("Memproses ${segments.size} segmen...")

        for ((index, segment) in segments.withIndex()) {
            val outputFile = File(outputDir, "autocut_${index + 1}_${timestamp()}.mp4")
            val command = listOf(
                "-y",
                "-ss", segment.startSeconds.toString(),
                "-to", segment.endSeconds.toString(),
                "-i", inputFile.absolutePath,
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-c:a", "aac",
                "-movflags", "+faststart",
                outputFile.absolutePath
            ).joinToString(" ")

            val success = executeFfmpeg(command)
            if (!success) {
                appendStatus("Gagal pada segmen ${index + 1}")
                return
            }
            appendStatus("Segmen ${index + 1} selesai: ${outputFile.name}")
        }

        appendStatus("AutoCut selesai. File output ada di ${outputDir.absolutePath}")
    }

    private fun downloadVideo(url: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Klipers Download")
            .setDescription("Mengunduh video sumber")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val fileName = "download_${timestamp()}.mp4"
        request.setDestinationInExternalFilesDir(
            this,
            Environment.DIRECTORY_DOWNLOADS,
            fileName
        )

        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        appendStatus("Download dijalankan ke folder app external Downloads/$fileName")
    }

    private fun ensureOutputDir(): File {
        val base = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val outDir = File(base, "AutoCutOutputs")
        if (!outDir.exists()) {
            outDir.mkdirs()
        }
        return outDir
    }

    private suspend fun copyUriToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val target = File(cacheDir, "source_${timestamp()}.mp4")
        contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Gagal membaca file video")
        target
    }

    private suspend fun readTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private suspend fun executeFfmpeg(command: String): Boolean = suspendCancellableCoroutine { cont ->
        FFmpegKit.executeAsync(command) { session ->
            val success = ReturnCode.isSuccess(session.returnCode)
            cont.resume(success)
        }
    }

    private fun appendStatus(message: String) {
        val old = tvStatus.text.toString()
        val merged = if (old.isBlank()) message else "$old\n$message"
        tvStatus.text = merged
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
