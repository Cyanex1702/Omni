package com.omniplayer.app.mood

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import com.omniplayer.app.model.AcousticMoodProfile
import com.omniplayer.app.model.BuiltInMoods
import com.omniplayer.app.model.OmniMedia
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class AcousticMoodAnalyzer(private val context: Context) {
    fun analyze(media: OmniMedia, stopped: () -> Boolean = { false }): AcousticMoodProfile? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, media.uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = inputFormat.getLongOrDefault(MediaFormat.KEY_DURATION, media.durationMs * 1_000L)
            if (durationUs > 90_000_000L) extractor.seekTo(durationUs / 5L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            codec = createSafeDecoder(mime)?.apply {
                configure(inputFormat, null, null, 0)
                start()
            } ?: return null
            val stats = AudioStats()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputFormat = inputFormat
            var firstOutputUs = -1L
            var idleOutputs = 0
            val deadline = SystemClock.elapsedRealtime() + MAX_ANALYSIS_RUNTIME_MS
            while (!outputEnded && !stopped() && SystemClock.elapsedRealtime() < deadline) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        idleOutputs++
                        if (inputEnded && idleOutputs >= MAX_IDLE_OUTPUTS) outputEnded = true
                    }
                    else -> if (outputIndex >= 0) {
                        idleOutputs = 0
                        if (firstOutputUs < 0) firstOutputUs = info.presentationTimeUs
                        codec.getOutputBuffer(outputIndex)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            stats.consume(
                                buffer.slice().order(ByteOrder.LITTLE_ENDIAN),
                                outputFormat.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100),
                                outputFormat.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2),
                                outputFormat.getIntOrDefault(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT),
                            )
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 ||
                            (firstOutputUs >= 0 && info.presentationTimeUs - firstOutputUs >= ANALYSIS_WINDOW_US)
                    }
                }
            }
            stats.toProfile(media)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun createSafeDecoder(mime: String): MediaCodec? {
        val safeCodec = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .asSequence()
                .filterNot { it.isEncoder }
                .filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
                .map { it.name }
                .filter { name ->
                    val normalized = name.lowercase()
                    normalized.startsWith("c2.android.") || normalized.startsWith("omx.google.")
                }
                .sortedBy { name -> if (name.lowercase().startsWith("c2.android.")) 0 else 1 }
                .firstOrNull()
        }.getOrNull() ?: return null
        return runCatching { MediaCodec.createByCodecName(safeCodec) }.getOrNull()
    }

    private class AudioStats {
        private var sampleCount = 0L
        private var squareSum = 0.0
        private var peak = 0f
        private var crossings = 0L
        private var previous = 0f
        private var blockSum = 0.0
        private var blockCount = 0
        private var blockSize = 882
        private val envelope = mutableListOf<Float>()

        fun consume(buffer: java.nio.ByteBuffer, sampleRate: Int, channels: Int, encoding: Int) {
            blockSize = (sampleRate.coerceAtLeast(8_000) / 50 * channels.coerceAtLeast(1)).coerceAtLeast(160)
            when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> while (buffer.remaining() >= 4) add(buffer.float.coerceIn(-1f, 1f))
                AudioFormat.ENCODING_PCM_8BIT -> while (buffer.hasRemaining()) add(((buffer.get().toInt() and 0xff) - 128) / 128f)
                else -> while (buffer.remaining() >= 2) add(buffer.short / 32768f)
            }
        }

        private fun add(sample: Float) {
            if ((sample >= 0f) != (previous >= 0f)) crossings++
            previous = sample
            val magnitude = abs(sample)
            peak = maxOf(peak, magnitude)
            squareSum += sample * sample
            sampleCount++
            blockSum += magnitude
            blockCount++
            if (blockCount >= blockSize) {
                envelope += (blockSum / blockCount).toFloat()
                blockSum = 0.0
                blockCount = 0
            }
        }

        fun toProfile(media: OmniMedia): AcousticMoodProfile? {
            if (sampleCount < 8_000) return null
            val rms = sqrt(squareSum / sampleCount).toFloat()
            val energy = normalize(rms, 0.025f, 0.22f)
            val zcr = crossings.toFloat() / sampleCount
            val brightness = normalize(zcr, 0.025f, 0.16f)
            val tempo = estimateTempo(envelope)
            val fast = normalize(tempo, 82f, 158f)
            val dynamic = (peak / (rms + 0.0001f)).coerceIn(1f, 12f)
            val steadiness = 1f - normalize(dynamic, 2.2f, 8f)
            val lowEnergy = 1f - energy
            val dark = 1f - brightness
            val moderate = 1f - abs(energy - 0.48f) / 0.48f
            val scores = mapOf(
                BuiltInMoods.CHILL to score(0.42f * lowEnergy + 0.22f * (1f - fast) + 0.22f * dark + 0.14f * steadiness),
                BuiltInMoods.WORKOUT to score(0.48f * energy + 0.32f * fast + 0.20f * brightness),
                BuiltInMoods.FOCUS to score(0.34f * steadiness + 0.30f * dark + 0.22f * moderate + 0.14f * (1f - fast)),
                BuiltInMoods.PARTY to score(0.43f * energy + 0.38f * fast + 0.19f * brightness),
                BuiltInMoods.ROMANCE to score(0.38f * moderate + 0.27f * dark + 0.22f * (1f - fast) + 0.13f * steadiness),
                BuiltInMoods.SLEEP to score(0.53f * lowEnergy + 0.25f * (1f - fast) + 0.22f * dark),
            )
            return AcousticMoodProfile(
                mediaUri = media.uri.toString(),
                sizeBytes = media.sizeBytes,
                dateModifiedSeconds = media.dateModifiedSeconds,
                analyzedAtMillis = System.currentTimeMillis(),
                energy = energy,
                brightness = brightness,
                tempoBpm = tempo,
                dynamicRange = dynamic,
                moodScores = scores,
            )
        }

        private fun estimateTempo(values: List<Float>): Float {
            if (values.size < 120) return 100f
            val mean = values.average().toFloat()
            var bestLag = 25
            var bestCorrelation = Float.NEGATIVE_INFINITY
            for (lag in 17..50) {
                var correlation = 0f
                for (index in lag until values.size) {
                    correlation += (values[index] - mean) * (values[index - lag] - mean)
                }
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation
                    bestLag = lag
                }
            }
            return (3_000f / bestLag).coerceIn(60f, 180f)
        }

        private fun normalize(value: Float, low: Float, high: Float) = ((value - low) / (high - low)).coerceIn(0f, 1f)
        private fun score(value: Float) = (0.08f + value * 0.9f).coerceIn(0f, 0.98f)
    }

    private fun MediaFormat.getIntOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default

    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long =
        if (containsKey(key)) getLong(key) else default

    companion object {
        private const val ANALYSIS_WINDOW_US = 12_000_000L
        private const val MAX_ANALYSIS_RUNTIME_MS = 25_000L
        private const val MAX_IDLE_OUTPUTS = 120
    }
}
