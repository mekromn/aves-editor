package deckers.thibault.aves.storage.apis

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.TransactionTooLargeException
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import deckers.thibault.aves.MainActivity
import deckers.thibault.aves.model.FieldMap
import deckers.thibault.aves.storage.StorageUtils
import deckers.thibault.aves.utils.LogUtils
import java.io.File
import java.util.Locale
import java.util.concurrent.CompletableFuture

object MediaStorePermissions : StoragePermissions {
    private val LOG_TAG = LogUtils.createTag<MediaStorePermissions>()
    private val MEDIA_STORE_INSERTION_PRIMARY_DIRS = listOf(
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_DOWNLOADS,
        Environment.DIRECTORY_PICTURES,
    )

    fun isMediaManagementGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaStore.canManageMedia(context) else false
    }

    fun canRequestBulkAccess(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    fun canInsert(directories: List<FieldMap>): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insertionDirsLower = MEDIA_STORE_INSERTION_PRIMARY_DIRS.map { it.lowercase(Locale.ROOT) }
            directories.all {
                val relativeDir = it["relativeDir"] as String
                val segments = relativeDir.split(File.separator)
                segments.isNotEmpty() && insertionDirsLower.contains(segments.first().lowercase(Locale.ROOT))
            }
        } else {
            true
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun requestFileAccess(activity: Activity, uris: List<Uri>, mimeTypes: List<String>): Boolean {
        val safeUris = uris.mapIndexed { index, uri -> StorageUtils.getMediaStoreScopedStorageSafeUri(uri, mimeTypes[index]) }

        val todoUris = ArrayList<Uri>()
        val pid = Binder.getCallingPid()
        val uid = Binder.getCallingUid()
        val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.checkUriPermissions(safeUris, pid, uid, flags)
        } else {
            safeUris.map { activity.checkUriPermission(it, pid, uid, flags) }.toIntArray()
        }.forEachIndexed { index, permission ->
            if (permission != PackageManager.PERMISSION_GRANTED) {
                todoUris.add(safeUris[index])
            }
        }
        if (todoUris.isEmpty()) return true

        Log.i(LOG_TAG, "request user to select and grant access permission to uris=$todoUris")
        try {
            val intentSender = MediaStore.createWriteRequest(activity.contentResolver, safeUris).intentSender
            MainActivity.pendingScopedStoragePermissionCompleter = CompletableFuture<Boolean>()
            activity.startIntentSenderForResult(intentSender, MainActivity.MEDIA_WRITE_BULK_PERMISSION_REQUEST, null, 0, 0, 0, null)
        } catch (e: IllegalArgumentException) {
            if (e.message == "URI list restricted to 2000 per request") {
                throw TransactionTooLargeException(e.message)
            }
            throw e
        }

        val granted = MainActivity.pendingScopedStoragePermissionCompleter!!.join()
        MainActivity.pendingScopedStoragePermissionCompleter = null

        return granted
    }

    fun canEdit(context: Context, uri: Uri, mimeType: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val safeUri = StorageUtils.getMediaStoreScopedStorageSafeUri(uri, mimeType)

            val pid = Binder.getCallingPid()
            val uid = Binder.getCallingUid()
            val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.checkUriPermission(safeUri, pid, uid, flags) == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    override fun canEditWithUserInteraction(context: Context, dirPath: String): Boolean {
        if (!canRequestBulkAccess()) return false
        if (StorageUtils.isInAppStorage(context, dirPath)) return false
        return true
    }
}