package deckers.thibault.aves.storage

import android.content.Context
import deckers.thibault.aves.storage.StorageUtils.ensureTrailingSeparator
import deckers.thibault.aves.storage.apis.FilePermissions
import deckers.thibault.aves.storage.apis.SafPermissions
import deckers.thibault.aves.storage.apis.StorageApi
import java.util.regex.Pattern

object PermissionManager {
    private val VOLUME_USER_ID_PATTERN = Pattern.compile("(?i)^/storage/emulated/([0-9]+)")
    const val USER_ID_DUAL_MESSENGER = 95 // Samsung Dual Messenger user ID

    fun getVolumeUserId(volumePath: String): Int? {
        val matcher = VOLUME_USER_ID_PATTERN.matcher(volumePath)
        return if (matcher.find()) matcher.group(1)?.toIntOrNull() else null
    }

    fun getAppUserId(context: Context): Int? {
        // `Context.getUserId()` and `UserHandle.myUserId()` are restricted APIs,
        // so we derive it from the app external files directory
        context.getExternalFilesDir(null)?.let { externalFilesDir ->
            StorageUtils.getVolumePath(context, externalFilesDir.absolutePath)?.let { volumePath ->
                return getVolumeUserId(volumePath)
            }
        }
        return null
    }

    fun getGrantedDirForPath(context: Context, anyPath: String): String? {
        return getAccessibleDirs(context).firstOrNull { anyPath.startsWith(it) }
    }

    fun getInaccessibleDirectories(context: Context, dirPaths: List<String>): Set<PathSegments> {
        val concreteDirPaths = dirPaths.filter { it != StorageUtils.TRASH_PATH_PLACEHOLDER }
        val accessibleDirs = getAccessibleDirs(context)
        val inaccessibleDirPaths = concreteDirPaths.map(StorageUtils::ensureTrailingSeparator).filter { dirPath ->
            accessibleDirs.none(dirPath::startsWith)
        }.toSet()

        // find optimal directories to request for SAF access
        return inaccessibleDirPaths.mapNotNull { SafPermissions.getDirToRequest(context, it) }.toSet()
    }

    // returns paths accessible to the app (granted by the user or by default)
    private fun getAccessibleDirs(context: Context): Set<String> {
        return hashSetOf<String>().apply {
            addAll(SafPermissions.getGrantedDirectories(context))
            addAll(FilePermissions.getAccessibleDirectories(context))
        }
    }

    fun getStorageAccess(context: Context, dirPaths: List<String>): Map<PathSegments, Set<StorageApi>> {
        val storageAccess = HashMap<PathSegments, Set<StorageApi>>()
        dirPaths.map(::ensureTrailingSeparator).forEach { dirPath ->
            val apis = StorageApi.entries.filter { api ->
                api.getPermissionDelegate().canEditWithUserInteraction(context, dirPath)
            }.toSet()
            storageAccess[PathSegments(context, dirPath)] = apis
        }

        return storageAccess
    }
}