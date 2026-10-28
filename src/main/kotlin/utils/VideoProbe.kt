package com.github.purofle.remakebot.utils

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class VideoDimensions(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Video dimensions must be positive" }
    }
}

/** Use FFmpeg's format support to inspect the actual downloaded file without transcoding it. */
object VideoProbe {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(file: File): VideoDimensions {
        val output = File.createTempFile("ffprobe-", ".json")
        try {
            val process = ProcessBuilder(
                "ffprobe", "-v", "error", "-select_streams", "v:0",
                "-show_streams", "-show_entries", "stream=width,height,sample_aspect_ratio",
                "-of", "json", file.absolutePath,
            ).redirectOutput(output).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            try {
                check(process.waitFor(15, TimeUnit.SECONDS)) { "ffprobe timed out" }
                check(process.exitValue() == 0) { "ffprobe failed (exit code ${process.exitValue()})" }
                val stream = json.decodeFromString<ProbeResult>(output.readText()).streams.firstOrNull()
                    ?: error("No video stream found")
                val aspect = stream.sampleAspectRatio.split(':').mapNotNull { it.toDoubleOrNull() }
                val scale = if (aspect.size == 2 && aspect.all { it > 0 && it.isFinite() }) aspect[0] / aspect[1] else 1.0
                val width = (stream.width * scale).roundToInt()
                val height = stream.height
                val rotation = stream.sideData.firstNotNullOfOrNull { it.rotation } ?: 0.0
                return if (abs(rotation).roundToInt() % 180 == 90) VideoDimensions(height, width)
                    else VideoDimensions(width, height)
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            output.delete()
        }
    }

    @Serializable
    private data class ProbeResult(val streams: List<Stream> = emptyList())

    @Serializable
    private data class Stream(
        val width: Int,
        val height: Int,
        @SerialName("sample_aspect_ratio") val sampleAspectRatio: String = "1:1",
        @SerialName("side_data_list") val sideData: List<SideData> = emptyList(),
    )

    @Serializable
    private data class SideData(val rotation: Double? = null)
}
