package com.omniplayer.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.omniplayer.app.model.OmniMedia
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaToolResult(val uri: Uri, val message: String)

class MediaToolsRepository(private val context: Context) {
    suspend fun extractAudio(video: OmniMedia): MediaToolResult = withContext(Dispatchers.IO) {
        val name = "${safeStem(video.title)} - audio.m4a"
        val output = remuxAac(video.uri, 0L, Long.MAX_VALUE, name)
        MediaToolResult(output, "Saved $name in Music/Omni/Tools")
    }

    suspend fun trimAudio(audio: OmniMedia, startMs: Long, endMs: Long): MediaToolResult = withContext(Dispatchers.IO) {
        require(endMs - startMs >= 1_000L) { "Choose a clip that is at least one second long." }
        val sourceMime = audioTrackMime(audio.uri)
        val stem = "${safeStem(audio.title)} - clip"
        val output = if (sourceMime == "audio/mpeg") {
            copyMp3Samples(audio.uri, startMs * 1_000L, endMs * 1_000L, "$stem.mp3")
        } else {
            remuxAac(audio.uri, startMs * 1_000L, endMs * 1_000L, "$stem.m4a")
        }
        MediaToolResult(output, "Saved ${if (sourceMime == "audio/mpeg") "$stem.mp3" else "$stem.m4a"} in Music/Omni/Tools")
    }

    private fun remuxAac(source: Uri, startUs: Long, endUs: Long, displayName: String): Uri {
        val extractor = MediaExtractor()
        var target: OutputTarget? = null
        var handle: MuxerHandle? = null
        var started = false
        var stopped = false
        try {
            extractor.setDataSource(context, source, null)
            val track = findAudioTrack(extractor)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime !in MUXABLE_AUDIO_TYPES) {
                throw IOException("This tool currently supports AAC/M4A audio tracks. The selected file uses $mime.")
            }
            target = createAudioOutput(displayName, "audio/mp4")
            handle = createMuxer(target)
            val outputTrack = handle.muxer.addTrack(format)
            handle.muxer.start()
            started = true
            extractor.selectTrack(track)
            extractor.seekTo(startUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val maximum = format.integerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceIn(64 * 1_024, 16 * 1_024 * 1_024)
                ?: 1 * 1_024 * 1_024
            val buffer = ByteBuffer.allocateDirect(maximum)
            val info = MediaCodec.BufferInfo()
            var wroteSample = false
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time < 0 || time > endUs) break
                if (extractor.sampleTrackIndex == track && time >= startUs) {
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) {
                        throw IOException("Encrypted audio tracks cannot be remuxed.")
                    }
                    val codecFlags = if (
                        extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                    ) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(
                        0,
                        size,
                        (time - startUs).coerceAtLeast(0L),
                        codecFlags,
                    )
                    handle.muxer.writeSampleData(outputTrack, buffer, info)
                    wroteSample = true
                }
                extractor.advance()
            }
            if (!wroteSample) throw IOException("No audio samples were found in the selected range.")
            handle.muxer.stop()
            stopped = true
            completeOutput(target)
            return target.uri
        } catch (error: Exception) {
            target?.let(::deleteOutput)
            throw if (error is IOException || error is IllegalArgumentException) error
            else IOException("The audio could not be created from this file.", error)
        } finally {
            if (started && !stopped) runCatching { handle?.muxer?.stop() }
            runCatching { handle?.muxer?.release() }
            runCatching { handle?.descriptor?.close() }
            extractor.release()
        }
    }

    private fun copyMp3Samples(source: Uri, startUs: Long, endUs: Long, displayName: String): Uri {
        val extractor = MediaExtractor()
        var target: OutputTarget? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = findAudioTrack(extractor)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime != "audio/mpeg") throw IOException("The selected file is not MP3 audio.")
            target = createAudioOutput(displayName, "audio/mpeg")
            extractor.selectTrack(track)
            extractor.seekTo(startUs.coerceAtLeast(0L), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val maximum = format.integerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                ?.coerceIn(64 * 1_024, 4 * 1_024 * 1_024)
                ?: 256 * 1_024
            val buffer = ByteBuffer.allocateDirect(maximum)
            val bytes = ByteArray(maximum)
            var wroteSample = false
            openOutputStream(target).use { output ->
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val time = extractor.sampleTime
                    if (time < 0 || time > endUs) break
                    if (extractor.sampleTrackIndex == track && time >= startUs) {
                        buffer.position(0)
                        buffer.get(bytes, 0, size)
                        output.write(bytes, 0, size)
                        wroteSample = true
                    }
                    extractor.advance()
                }
            }
            if (!wroteSample) throw IOException("No MP3 audio was found in the selected range.")
            completeOutput(target)
            return target.uri
        } catch (error: Exception) {
            target?.let(::deleteOutput)
            throw if (error is IOException || error is IllegalArgumentException) error
            else IOException("The ringtone clip could not be created.", error)
        } finally {
            extractor.release()
        }
    }

    private fun audioTrackMime(source: Uri): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, source, null)
            val track = findAudioTrack(extractor)
            extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (index in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return index
            }
        }
        throw IOException("The selected file does not contain an audio track.")
    }

    private fun createAudioOutput(displayName: String, mimeType: String): OutputTarget {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Omni/Tools")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IOException("Android could not create the output audio file.")
            return OutputTarget(uri = uri, path = null, pending = true)
        }
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Omni/Tools").apply { mkdirs() }
        val file = uniqueFile(folder, displayName)
        return OutputTarget(Uri.fromFile(file), file.absolutePath, pending = false)
    }

    private fun createMuxer(target: OutputTarget): MuxerHandle {
        if (target.path != null) {
            return MuxerHandle(MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4), null)
        }
        val descriptor = context.contentResolver.openFileDescriptor(target.uri, "rw")
            ?: throw IOException("Android could not open the output audio file.")
        return MuxerHandle(
            MediaMuxer(descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
            descriptor,
        )
    }

    private fun openOutputStream(target: OutputTarget): OutputStream = if (target.path != null) {
        File(target.path).outputStream().buffered()
    } else {
        context.contentResolver.openOutputStream(target.uri, "w")?.buffered()
            ?: throw IOException("Android could not write the output audio file.")
    }

    private fun completeOutput(target: OutputTarget) {
        if (target.pending && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.update(
                target.uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        }
    }

    private fun deleteOutput(target: OutputTarget) {
        if (target.path != null) File(target.path).delete()
        else context.contentResolver.delete(target.uri, null, null)
    }

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        val stem = name.substringBeforeLast('.', name)
        val suffix = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var index = 1
        while (candidate.exists()) candidate = File(folder, "$stem ($index)$suffix").also { index++ }
        return candidate
    }

    private fun safeStem(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._ ()-]"), "_")
        .trim()
        .take(80)
        .ifBlank { "Omni media" }

    private fun MediaFormat.integerOrNull(key: String): Int? = runCatching {
        if (containsKey(key)) getInteger(key) else null
    }.getOrNull()

    private data class OutputTarget(val uri: Uri, val path: String?, val pending: Boolean)
    private data class MuxerHandle(val muxer: MediaMuxer, val descriptor: ParcelFileDescriptor?)

    companion object {
        private val MUXABLE_AUDIO_TYPES = setOf("audio/mp4a-latm", "audio/3gpp", "audio/amr-wb")
    }
}
