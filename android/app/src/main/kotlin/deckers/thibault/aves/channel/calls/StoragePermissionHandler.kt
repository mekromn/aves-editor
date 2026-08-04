package deckers.thibault.aves.channel.calls

import android.content.Context
import deckers.thibault.aves.channel.calls.Coresult.Companion.safe
import deckers.thibault.aves.model.FieldMap
import deckers.thibault.aves.storage.PathSegments
import deckers.thibault.aves.storage.PermissionManager
import deckers.thibault.aves.storage.apis.MediaStorePermissions
import deckers.thibault.aves.storage.apis.SafPermissions
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StoragePermissionHandler(private val context: Context) : MethodCallHandler {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getStorageAccess" -> ioScope.launch { safe(call, result, ::getStorageAccess) }
            "getSafGrantedDirectories" -> ioScope.launch { safe(call, result, ::getSafGrantedDirectories) }
            "getInaccessibleDirectories" -> ioScope.launch { safe(call, result, ::getInaccessibleDirectories) }
            "getSafRestrictedDirectories" -> ioScope.launch { safe(call, result, ::getSafRestrictedDirectories) }
            "getSafRestrictedVolumes" -> ioScope.launch { safe(call, result, ::getSafRestrictedVolumes) }
            "revokeSafDirectoryAccess" -> safe(call, result, ::revokeSafDirectoryAccess)
            "canRequestMediaStoreBulkAccess" -> safe(call, result, ::canRequestMediaStoreBulkAccess)
            "canInsertByMediaStore" -> safe(call, result, ::canInsertByMediaStore)
            else -> result.notImplemented()
        }
    }

    private fun getStorageAccess(call: MethodCall, result: MethodChannel.Result) {
        val dirPaths = call.argument<List<String>>("dirPaths")
        if (dirPaths == null) {
            result.error("getStorageAccess-args", "missing arguments", null)
            return
        }

        val apisByPathSegments = PermissionManager.getStorageAccess(context, dirPaths)
        result.success(apisByPathSegments.map { (pathSegments, apis) -> hashMapOf(
            "dir" to pathSegments.toMap(),
            "apis" to apis.map { api -> api.toKey() }.toList(),
        ) }.toList())
    }

    private fun getSafGrantedDirectories(@Suppress("unused_parameter") call: MethodCall, result: MethodChannel.Result) {
        val dirPaths = SafPermissions.getGrantedDirectories(context)
        result.success(dirPaths.toList())
    }

    private fun getInaccessibleDirectories(call: MethodCall, result: MethodChannel.Result) {
        val dirPaths = call.argument<List<String>>("dirPaths")
        if (dirPaths == null) {
            result.error("getInaccessibleDirectories-args", "missing arguments", null)
            return
        }

        val pathSegments = PermissionManager.getInaccessibleDirectories(context, dirPaths)
        result.success(pathSegments.map(PathSegments::toMap).toList())
    }

    private fun getSafRestrictedDirectories(@Suppress("unused_parameter") call: MethodCall, result: MethodChannel.Result) {
        val pathSegments = SafPermissions.getRestrictedDirectories(context)
        result.success(pathSegments.map(PathSegments::toMap).toList())
    }

    private fun getSafRestrictedVolumes(@Suppress("unused_parameter") call: MethodCall, result: MethodChannel.Result) {
        val paths = SafPermissions.getRestrictedVolumes(context)
        result.success(paths.toList())
    }

    private fun revokeSafDirectoryAccess(call: MethodCall, result: MethodChannel.Result) {
        val path = call.argument<String>("path")
        if (path == null) {
            result.error("revokeSafDirectoryAccess-args", "missing arguments", null)
            return
        }

        val success = SafPermissions.revokeDirectoryAccess(context, path)
        result.success(success)
    }

    private fun canRequestMediaStoreBulkAccess(@Suppress("unused_parameter") call: MethodCall, result: MethodChannel.Result) {
        result.success(MediaStorePermissions.canRequestBulkAccess())
    }

    private fun canInsertByMediaStore(call: MethodCall, result: MethodChannel.Result) {
        val directories = call.argument<List<FieldMap>>("directories")
        if (directories == null) {
            result.error("canInsertByMediaStore-args", "missing arguments", null)
            return
        }

        result.success(MediaStorePermissions.canInsert(directories))
    }

    companion object {
        const val CHANNEL = "deckers.thibault/aves/storage_permission"
    }
}