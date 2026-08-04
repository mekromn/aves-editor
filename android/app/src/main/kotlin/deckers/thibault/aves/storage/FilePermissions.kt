package deckers.thibault.aves.storage

import android.content.Context
import android.os.Build

object FilePermissions {
    fun getAccessibleDirectories(context: Context): Set<String> {
        return hashSetOf<String>().apply {
            // /storage/{volume}/Android/data/{package_name}/files
            addAll(context.getExternalFilesDirs(null).filterNotNull().map { it.path })
            // /data/user/0/{package_name}/files
            add(context.filesDir.path)

            // from API 21 / Android 5.0 / Lollipop, removable storage requires access permission, but directory access grant is possible
            // from API 30 / Android 11 / R, any storage requires access permission
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                add(StorageUtils.getPrimaryVolumePath(context))
            }
        }
    }

    fun canEdit(context: Context, anyPath: String): Boolean {
        val dirs = getAccessibleDirectories(context)
        return dirs.any { anyPath.startsWith(it) }
    }
}