package com.example.specklerng

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.specklerng.data.MappingProof
import com.example.specklerng.data.Receipt
import com.example.specklerng.data.Status
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.math3.distribution.ChiSquaredDistribution
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import kotlin.math.roundToInt

class RngService : LifecycleService() {

    private val binder = RngBinder()
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val _status = MutableStateFlow<Status?>(null)
    val status = _status.asStateFlow()

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady = _isCameraReady.asStateFlow()

    private val receipts = LruCache<String, Receipt>(1000)
    private val diffImages = LruCache<String, ByteArray>(100)
    @Volatile private var lastRoi: ByteArray? = null
    @Volatile private var lastDiff: ByteArray? = null
    private var serviceStartTime = 0L
    private var serverStartTime = 0L

    private lateinit var logFile: File
    private lateinit var duplicatesDir: File
    private val pin = "123456"

    val apiHelpText = """
        API Endpoints:
        - /: Shows status and live images.
        - /status: Raw JSON status.
        - /rng?min=X&max=Y: Get a random number.
        - /log?pin=PIN: View the receipt log.
        - /analysis?pin=PIN: View randomness analysis.
        - /clear_log?pin=PIN: Clear all logs and duplicates.
        - /duplicates?pin=PIN: View saved duplicate images.
        - /help: This help text.

        D&D Dice Endpoints:
        - /d4, /d6, /d8, /d10, /d12, /d20, /d100
        - /coin (returns 1 or 2)
        """.trimIndent()

    private val server by lazy {
        embeddedServer(Netty, port = 8080) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/") {
                    call.respondText(createHtml(), ContentType.Text.Html)
                }
                get("/help") {
                    call.respondText(apiHelpText, ContentType.Text.Plain)
                }
                get("/status") {
                    _status.value?.let { call.respond(it) }
                }
                get("/rng") {
                    val min = call.parameters["min"]?.toIntOrNull() ?: 1
                    val max = call.parameters["max"]?.toIntOrNull() ?: 10
                    try {
                        call.respond(generateAndLog(min, max))
                    } catch (e: IllegalStateException) {
                        call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy")
                    }
                }
                get("/d4") { try { call.respond(generateAndLog(1, 4)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/d6") { try { call.respond(generateAndLog(1, 6)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/d8") { try { call.respond(generateAndLog(1, 8)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/d10") { try { call.respond(generateAndLog(1, 10)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/d12") { try { call.respond(generateAndLog(1, 12)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/d20") { try { call.respond(generateAndLog(1, 20)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/d100") { try { call.respond(generateAndLog(1, 100)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }
                get("/coin") { try { call.respond(generateAndLog(1, 2)) } catch (e: IllegalStateException) { call.respond(HttpStatusCode.ServiceUnavailable, e.message ?: "Not enough entropy") } }

                get("/log") {
                    if (call.parameters["pin"] != pin) {
                        return@get call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
                    }
                    if (logFile.exists()) {
                        call.respondText(logFile.readText(), ContentType.Text.Plain)
                    } else {
                        call.respondText("Log file not found.", ContentType.Text.Plain)
                    }
                }
                get("/analysis") {
                     if (call.parameters["pin"] != pin) {
                        return@get call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
                    }
                    val analysisHtml = performAnalysis()
                    call.respondText(analysisHtml, ContentType.Text.Html)
                }
                get("/clear_log") {
                    if (call.parameters["pin"] != pin) {
                        return@get call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
                    }
                    if (logFile.exists()) logFile.delete()
                    if (duplicatesDir.exists()) duplicatesDir.deleteRecursively().also{ duplicatesDir.mkdirs() }
                    receipts.evictAll()
                    call.respondRedirect("/analysis?pin=$pin")
                }
                get("/duplicates") {
                    if (call.parameters["pin"] != pin) {
                        return@get call.respond(HttpStatusCode.Unauthorized, "Invalid PIN")
                    }
                    val duplicateFiles = duplicatesDir.listFiles()?.map { it.name } ?: emptyList()
                     val duplicateLinks = if (duplicateFiles.isNotEmpty()) {
                        duplicateFiles.joinToString("") { "<a href=\"/duplicates/$it\" target=\"_blank\">$it</a><br>" }
                    } else {
                        "No duplicates detected."
                    }
                    call.respondText("<html><body><h1>Duplicate Frames</h1>$duplicateLinks</body></html>", ContentType.Text.Html)
                }
                get("/duplicates/{name}") {
                    val name = call.parameters["name"] ?: return@get
                    val file = File(duplicatesDir, name)
                    if (file.exists()) {
                        call.respondBytes(file.readBytes(), ContentType.Image.PNG)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                get("/receipt/{id}") {
                    val id = call.parameters["id"]!!
                    receipts[id]?.let { call.respond(it) } ?: call.respond(HttpStatusCode.NotFound)
                }
                get("/receipt/{id}/diff.png") {
                    val id = call.parameters["id"]!!
                    diffImages[id]?.let {
                        val bitmap = toBitmap(it, 256, 256)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        call.respondBytes(stream.toByteArray(), ContentType.Image.PNG) 
                    } ?: call.respond(HttpStatusCode.NotFound)
                }
                get("/live.jpg") {
                    lastRoi?.let {
                        val bitmap = toBitmap(it, 256, 256)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        call.respondBytes(stream.toByteArray(), ContentType.Image.JPEG)
                    }
                }
                get("/diff.jpg") {
                    lastDiff?.let {
                        val bitmap = toBitmap(it, 256, 256)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        call.respondBytes(stream.toByteArray(), ContentType.Image.JPEG)
                    }
                }
            }
        }
    }

    /**
     * Public function that can be called directly from a bound activity.
     */
    fun getNumber(min: Int, max: Int): Int {
        return generateAndLog(min, max).value
    }

    private fun generateAndLog(min: Int, max: Int): Receipt {
        val receipt = generateReceipt(min, max)
        receipts.put(receipt.receipt_id, receipt)
        logReceipt(receipt)
        return receipt
    }

    override fun onCreate() {
        super.onCreate()
        serviceStartTime = System.currentTimeMillis()
        logFile = File(cacheDir, "random_log.csv")
        duplicatesDir = File(cacheDir, "duplicates")
        if (!duplicatesDir.exists()) duplicatesDir.mkdirs()
        createNotificationChannel()
        startForeground(1, createNotification())
        acquireWifiLock()
        startCamera()
        Thread {
            serverStartTime = System.currentTimeMillis()
            server.start(wait = true)
        }.start()
    }

    private fun acquireWifiLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SpeckleRng:WifiLock")
        wifiLock?.acquire()
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    inner class RngBinder : Binder() {
        fun getService(): RngService = this@RngService
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            _isCameraReady.value = true
        }, ContextCompat.getMainExecutor(this))
    }

    fun bindCameraUseCases(surfaceProvider: Preview.SurfaceProvider): androidx.camera.core.Camera? {
        return cameraProvider?.let {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, SpeckleAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                it.unbindAll()
                it.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("RngService", "Use case binding failed", exc)
                null
            }
        }
    }

    private inner class SpeckleAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalysisTime = 0L
        private val roiSize = 256

        override fun analyze(image: androidx.camera.core.ImageProxy) {
            val yBuffer = image.planes[0].buffer
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)

            val currentRoi = centerCrop(yBytes, image.width, image.height, roiSize)

            if (lastRoi != null) {
                val diff = ByteArray(roiSize * roiSize)
                var isAllZeros = true
                for (i in diff.indices) {
                    val difference = currentRoi[i].toInt() - lastRoi!![i].toInt()
                    if (difference != 0) isAllZeros = false
                    diff[i] = difference.toByte()
                }

                if (isAllZeros) {
                    Log.w("RngService", "Detected zero-diff frame, possible duplicate.")
                    saveDuplicateFrames(lastRoi!!, currentRoi)
                    // Do not update lastRoi or lastDiff. Discard this frame and wait for the next.
                    image.close()
                    return
                }

                lastDiff = diff

                val sha256 = MessageDigest.getInstance("SHA-256").digest(diff)
                val hexHash = sha256.take(16).joinToString("") { "%02x".format(it) }

                var sum = 0.0
                for (b in diff) {
                    sum += b
                }
                val mean = sum / diff.size

                var sumOfSquares = 0.0
                for (b in diff) {
                    sumOfSquares += (b - mean).pow(2)
                }
                val stdDev = Math.sqrt(sumOfSquares / diff.size)
                val diffStd = if (stdDev > 0) mean / stdDev else 0.0

                val currentTime = System.currentTimeMillis()
                val fps = if (lastAnalysisTime > 0) 1000.0 / (currentTime - lastAnalysisTime) else 0.0
                lastAnalysisTime = currentTime

                _status.value = Status(
                    uptime_ms = System.currentTimeMillis() - serviceStartTime,
                    server_uptime_ms = if (serverStartTime > 0) System.currentTimeMillis() - serverStartTime else 0,
                    fps = fps,
                    diff_std = diffStd,
                    last_sha256_diff_prefix = hexHash,
                    total_requests = 0,
                    total_receipts = receipts.size(),
                    entropy_ok = diffStd > 0.1,
                    stuck = diffStd < 0.01,
                    roi_size = roiSize,
                    mask_radius = 0
                )
            }

            lastRoi = currentRoi
            image.close()
        }

        private fun saveDuplicateFrames(frameA: ByteArray, frameB: ByteArray) {
            val timestamp = System.currentTimeMillis()
            val fileA = File(duplicatesDir, "duplicate_${timestamp}_A.png")
            val fileB = File(duplicatesDir, "duplicate_${timestamp}_B.png")

            toBitmap(frameA, roiSize, roiSize).compress(Bitmap.CompressFormat.PNG, 100, fileA.outputStream())
            toBitmap(frameB, roiSize, roiSize).compress(Bitmap.CompressFormat.PNG, 100, fileB.outputStream())
        }

        private fun centerCrop(data: ByteArray, width: Int, height: Int, roiSize: Int): ByteArray {
            val cropLeft = (width - roiSize) / 2
            val cropTop = (height - roiSize) / 2
            val cropped = ByteArray(roiSize * roiSize)

            for (y in 0 until roiSize) {
                for (x in 0 until roiSize) {
                    cropped[y * roiSize + x] = data[(cropTop + y) * width + (cropLeft + x)]
                }
            }
            return cropped
        }
    }

    private fun logReceipt(receipt: Receipt) {
        val range = receipt.max - receipt.min
        val normalizedValue = if (range > 0) 1 + ((99 * (receipt.value - receipt.min).toDouble()) / range).roundToInt() else 1
        val logEntry = "${System.currentTimeMillis()},${receipt.receipt_id},${receipt.value},${receipt.min},${receipt.max},${normalizedValue},${receipt.receipt_sha256}\n"
        logFile.appendText(logEntry)
    }

    private fun generateReceipt(min: Int, max: Int): Receipt {
        val diff = lastDiff ?: throw IllegalStateException("No diff available yet")

        val paramsJson = "{\"min\":$min,\"max\":$max,\"evidence\":\"grayscale\"}"
        val receiptSha256 = MessageDigest.getInstance("SHA-256").digest(diff + paramsJson.toByteArray())

        val range = max.toLong() - min.toLong() + 1
        var randomWord: Long
        var wordIndex = 0
        var result: Long

        do {
            if ((wordIndex + 1) * 4 > receiptSha256.size) throw IllegalStateException("Not enough entropy from SHA256 to generate number")
            val wordBytes = receiptSha256.sliceArray(wordIndex * 4 until (wordIndex + 1) * 4)
            randomWord = ByteBuffer.wrap(wordBytes).int.toLong() and 0xFFFFFFFFL
            result = min.toLong() + randomWord % range
            wordIndex++
        } while (randomWord >= (0xFFFFFFFFL / range) * range)

        val receiptId = "${System.currentTimeMillis()}-${frameCounter.getAndIncrement()}"
        diffImages.put(receiptId, diff)

        return Receipt(
            value = result.toInt(),
            min = min,
            max = max,
            receipt_id = receiptId,
            receipt_sha256 = receiptSha256.joinToString("") { "%02x".format(it) },
            mapping_proof = MappingProof(
                algorithm = "rejection-sampling-sha256-32bit",
                word_index = wordIndex - 1,
                word_value = randomWord,
                limit = (0xFFFFFFFFL / range) * range,
                result = result.toInt()
            ),
            urls = mapOf(
                "receipt" to "/receipt/$receiptId",
                "diff.png" to "/receipt/$receiptId/diff.png",
                "live.jpg" to "/live.jpg"
            )
        )
    }

    private fun toBitmap(grayscaleBytes: ByteArray, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val gray = grayscaleBytes[i].toInt() and 0xFF
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "rng_service_channel",
            "RNG Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification() = NotificationCompat.Builder(this, "rng_service_channel")
        .setContentTitle("SpeckleRng")
        .setContentText("RNG Service is running")
        .setSmallIcon(R.mipmap.ic_launcher)
        .build()

    private fun createHtml(): String {
        return """
        <html>
            <head>
                <title>SpeckleRng</title>
                <meta http-equiv="refresh" content="1">
            </head>
            <body>
                <h1>SpeckleRng Status</h1>
                <pre id="status">Loading...</pre>
                 <p><a href="/help" target="_blank">API Help</a></p>
                 <p><a href="/analysis?pin=$pin" target="_blank">View Analysis</a></p>
                <img src="/live.jpg" width="256" height="256">
                <img src="/diff.jpg" width="256" height="256">
                <script>
                    fetch('/status')
                        .then(response => response.json())
                        .then(data => {
                            document.getElementById('status').textContent = JSON.stringify(data, null, 2);
                        })
                        .catch(err => document.getElementById('status').textContent = 'Error loading status: ' + err);
                </script>
            </body>
        </html>
        """.trimIndent()
    }

    private fun performAnalysis(): String {
        if (!logFile.exists()) return "<html><body>Log file not found. Generate some numbers first.</body></html>"

        val lines = logFile.readLines()
        if (lines.size < 100) return "<html><body><h2>Need at least 100 events for a meaningful analysis.</h2><p>Currently have: ${lines.size}</p></body></html>"

        val totalEvents = lines.size
        val distribution = LongArray(101) { 0 }
        val values = mutableListOf<Int>()
        val hashes = mutableSetOf<String>()
        var duplicateCount = 0

        lines.forEach {
            val parts = it.split(",")
            if (parts.size >= 7) {
                val normalizedValue = parts[5].toIntOrNull() ?: 0
                val value = parts[2].toIntOrNull() ?: 0
                val hash = parts[6]
                if (normalizedValue in 1..100) {
                    distribution[normalizedValue]++
                }
                values.add(value)
                if (!hashes.add(hash)) {
                    duplicateCount++
                }
            }
        }

        val expectedFrequency = totalEvents / 100.0
        val chiSquared = distribution.indices.sumOf { i ->
            if (i == 0) 0.0 else {
                val observed = distribution[i]
                val deviation = observed - expectedFrequency
                deviation * deviation / expectedFrequency
            }
        }

        val chiSquaredDist = ChiSquaredDistribution(99.0) // 99 degrees of freedom (100-1)
        val pValue = 1.0 - chiSquaredDist.cumulativeProbability(chiSquared)

        val grade = when {
            pValue >= 0.1 -> "A (Excellent)"
            pValue >= 0.05 -> "B (Good)"
            pValue >= 0.01 -> "C (Acceptable)"
            else -> "F (Failed - Distribution is likely not uniform)"
        }

        val mean = values.average()
        val stdDev = Math.sqrt(values.map { (it - mean).pow(2) }.average())

        val duplicateFiles = duplicatesDir.listFiles()?.map { it.name } ?: emptyList()
        val duplicateHtml = if (duplicateFiles.isNotEmpty()) {
            """
            <div class="duplicates">
                <h2>Detected Glitched Frames</h2>
                <p>The following frames were detected as identical to their predecessor and were discarded. Click to view.</p>
                <a href="/duplicates?pin=$pin">View Duplicates</a>
            </div>
            """.trimIndent()
        } else ""

        val maxFreq = distribution.maxOrNull() ?: 1
        val chartHtml = (1..100).joinToString("") { i ->
            val freq = distribution[i]
            val barWidth = (freq.toDouble() / maxFreq * 100).toInt()
            "<div class='bar-container'><div class='bar-label'>$i</div><div class='bar' style='width: $barWidth%;'>$freq</div></div>"
        }

        return """
        <html>
            <head>
                <title>RNG Analysis</title>
                <style>
                    body { font-family: sans-serif; }
                    .grade { font-size: 2em; font-weight: bold; margin-bottom: 1em; }
                    .stats { background: #eee; padding: 1em; margin-bottom: 1em; }
                    .chart { border: 1px solid #ccc; padding: 10px; }
                     .duplicates { border: 1px solid #ffcccc; background: #fff0f0; padding: 10px; margin-top: 1em; }
                    .bar-container { display: flex; align-items: center; margin-bottom: 2px; font-size: 12px; }
                    .bar-label { width: 30px; text-align: right; margin-right: 5px; }
                    .bar { background: #4CAF50; height: 18px; line-height: 18px; color: white; text-align: right; padding-right: 5px; min-width: 1px; }
                </style>
            </head>
            <body>
                <h1>RNG Analysis</h1>
                <a href="/clear_log?pin=$pin">Clear Log</a>
                <div class="grade">Randomness Grade: $grade</div>
                <div class="stats">
                    <h2>Statistics</h2>
                    <p><strong>Total Events Logged:</strong> $totalEvents</p>
                    <p><strong>Duplicate Receipts (in log):</strong> $duplicateCount</p>
                     <p><strong>Glitched Frames Detected:</strong> ${duplicatesDir.listFiles()?.size ?: 0}</p>
                    <p><strong>P-Value (Chi-Squared):</strong> ${String.format("%.4f", pValue)} (Higher is better. > 0.05 is generally considered good)</p>
                    <p><strong>Mean (Original Value):</strong> ${String.format("%.2f", mean)}</p>
                    <p><strong>Std. Dev (Original Value):</strong> ${String.format("%.2f", stdDev)}</p>
                </div>
                $duplicateHtml
                <div class="chart">
                    <h2>Normalized Distribution (1-100)</h2>
                    $chartHtml
                </div>
            </body>
        </html>
        """.trimIndent()
    }


    override fun onDestroy() {
        super.onDestroy()
        wifiLock?.release()
        server.stop(1000, 5000)
        cameraExecutor.shutdown()
    }

    companion object {
        private val frameCounter = AtomicLong(0)
    }
}
