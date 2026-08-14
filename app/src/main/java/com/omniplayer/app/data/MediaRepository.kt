package com.omniplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    suspend fun loadAll(): List<OmniMedia> = withContext(Dispatchers.IO) {
        (queryAudio() + queryVideo()).sortedByDescending { it.dateAddedSeconds }
    }

    private fun queryAudio(): List<OmniMedia> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()

        return buildList {
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Audio.Media.SIZE} > 0",
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val date = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val displayName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.GENRE)
                    } else {
                        -1
                    }
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(id)
                        val artwork = cursor.getLong(albumId).takeIf { it > 0 }?.let {
                            android.net.Uri.parse("content://media/external/audio/albumart/$it")
                        }
                        add(
                            OmniMedia(
                                id = mediaId,
                                title = cursor.getString(title).orEmpty().ifBlank { "Untitled" },
                                artist = cursor.getString(artist).orEmpty().ifBlank { "Unknown artist" },
                                album = cursor.getString(album).orEmpty().ifBlank { "Unknown album" },
                                genre = if (genre >= 0) {
                                    cursor.getString(genre).orEmpty().ifBlank { "Unknown genre" }
                                } else {
                                    "Unknown genre"
                                },
                                durationMs = cursor.getLong(duration),
                                sizeBytes = cursor.getLong(size),
                                dateAddedSeconds = cursor.getLong(date),
                                dateModifiedSeconds = cursor.getLong(modified),
                                uri = ContentUris.withAppendedId(collection, mediaId),
                                kind = MediaKind.AUDIO,
                                mimeType = cursor.getString(mime),
                                artworkUri = artwork,
                                displayName = cursor.getString(displayName).orEmpty(),
                            )
                        )
                    }
                }
            }
        }
    }

    private fun queryVideo(): List<OmniMedia> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DISPLAY_NAME,
        )

        return buildList {
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Video.Media.SIZE} > 0",
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val id = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val title = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                    val duration = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val size = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val date = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val modified = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                    val mime = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val displayName = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(id)
                        val uri = ContentUris.withAppendedId(collection, mediaId)
                        add(
                            OmniMedia(
                                id = -mediaId,
                                title = cursor.getString(title).orEmpty().ifBlank { "Untitled video" },
                                durationMs = cursor.getLong(duration),
                                sizeBytes = cursor.getLong(size),
                                dateAddedSeconds = cursor.getLong(date),
                                dateModifiedSeconds = cursor.getLong(modified),
                                uri = uri,
                                kind = MediaKind.VIDEO,
                                artist = "Video",
                                album = "Videos",
                                mimeType = cursor.getString(mime),
                                artworkUri = uri,
                                displayName = cursor.getString(displayName).orEmpty(),
                            )
                        )
                    }
                }
            }
        }
    }
}
