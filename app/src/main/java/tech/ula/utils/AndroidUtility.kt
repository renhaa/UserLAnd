package tech.ula.utils

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContentResolver
import android.content.SharedPreferences
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.support.v4.content.ContextCompat
import tech.ula.R
import tech.ula.model.entities.Asset
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

fun makePermissionsUsable(containingDirectoryPath: String, filename: String) {
    val commandToRun = arrayListOf("chmod", "0777", filename)

    val pb = ProcessBuilder(commandToRun)
    pb.directory(File(containingDirectoryPath))

    val process = pb.start()
    process.waitFor()
}

fun arePermissionsGranted(context: Context): Boolean {
    return (ContextCompat.checkSelfPermission(context,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

            ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
}

class DefaultPreferences(private val prefs: SharedPreferences) {

    fun getProotDebuggingEnabled(): Boolean {
        return prefs.getBoolean("pref_proot_debug_enabled", false)
    }

    fun getProotDebuggingLevel(): String {
        return prefs.getString("pref_proot_debug_level", "-1") ?: ""
    }

    fun getProotDebugLogLocation(): String {
        return prefs.getString("pref_proot_debug_log_location", "${Environment.getExternalStorageDirectory().path}/PRoot_Debug_Log") ?: ""
    }
}

class TimestampPreferences(private val prefs: SharedPreferences) {
    fun getSavedTimestampForFile(assetConcatenatedName: String): Long {
        return prefs.getLong(assetConcatenatedName, 0)
    }

    fun setSavedTimestampForFileToNow(assetConcatenatedName: String) {
        with(prefs.edit()) {
            putLong(assetConcatenatedName, currentTimeSeconds())
            apply()
        }
    }
}

class AssetListPreferences(private val prefs: SharedPreferences) {
    fun getAssetLists(allAssetListTypes: List<Pair<String, String>>): List<List<Asset>> {
        val assetLists = ArrayList<List<Asset>>()
        allAssetListTypes.forEach {
            (assetType, architectureType) ->
            val allEntries = prefs.getStringSet("$assetType-$architectureType", setOf()) ?: setOf()
            val assetList: List<Asset> = allEntries.map {
                val (filename, remoteTimestamp) = it.split("-")
                Asset(filename, assetType, architectureType, remoteTimestamp.toLong())
            }
            assetLists.add(assetList)
        }
        return assetLists
    }

    fun setAssetList(assetType: String, architectureType: String, assetList: List<Asset>) {
        val entries = assetList.map {
            "${it.name}-${it.remoteTimestamp}"
        }.toSet()
        with(prefs.edit()) {
            putStringSet("$assetType-$architectureType", entries)
            apply()
        }
    }
}

class AppsPreferences(private val prefs: SharedPreferences) {
    companion object {
        val SSH = "SSH"
        val VNC = "VNC"
    }

    fun setAppServiceTypePreference(appName: String, serviceType: String) {
        with(prefs.edit()) {
            putString(appName, serviceType)
            apply()
        }
    }

    fun getAppServiceTypePreference(appName: String): String {
        return prefs.getString(appName, "") ?: ""
    }
}

class BuildWrapper {
    fun getSupportedAbis(): Array<String> {
        return Build.SUPPORTED_ABIS
    }

    fun getArchType(): String {
        val supportedABIS = this.getSupportedAbis()
                .map {
                    translateABI(it)
                }
                .filter {
                    isSupported(it)
                }
        if (supportedABIS.size == 1 && supportedABIS[0] == "") {
            throw Exception("No supported ABI!")
        } else {
            return supportedABIS[0]
        }
    }

    private fun isSupported(abi: String): Boolean {
        val supportedABIs = listOf("arm64", "arm", "x86_64", "x86")
        return supportedABIs.contains(abi)
    }

    private fun translateABI(abi: String): String {
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            "x86" -> "x86"
            else -> ""
        }
    }
}

class ConnectionUtility {
    @Throws(Exception::class)
    fun getUrlConnection(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }

    @Throws(Exception::class)
    fun getUrlInputStream(url: String): InputStream {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        return conn.inputStream
    }
}

class DownloadManagerWrapper {
    fun generateDownloadRequest(url: String, destination: String): DownloadManager.Request {
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle(destination)
        request.setDescription("Downloading ${destination.substringAfterLast("-")}.")
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destination)
        return request
    }

    fun generateQuery(id: Long): DownloadManager.Query {
        val query = DownloadManager.Query()
        query.setFilterById(id)
        return query
    }

    fun generateCursor(downloadManager: DownloadManager, query: DownloadManager.Query): Cursor {
        return downloadManager.query(query)
    }

    fun getDownloadTitle(cursor: Cursor): String {
        if (cursor.moveToFirst()) {
            return cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
        }
        return ""
    }

    fun getDownloadsDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}

class DownloadBroadcastReceiver : BroadcastReceiver() {
    private var doOnReceived: (Long) -> Unit = {}

    fun setDoOnReceived(action: (Long) -> Unit) {
        doOnReceived = action
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val downloadedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        downloadedId?.let {
            if (it == -1L) return@let
            doOnReceived(it)
        }
    }
}

class LocalFileLocator(private val applicationFilesDir: String, private val resources: Resources) {
    fun findIconUri(type: String): Uri {
        val icon =
                File("$applicationFilesDir/apps/$type/$type.png")
        if (icon.exists()) return Uri.fromFile(icon)
        return getDefaultIconUri()
    }

    private fun getDefaultIconUri(): Uri {
        val resId = R.mipmap.ic_launcher_foreground
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE +
                "://" + resources.getResourcePackageName(resId) + '/' +
                resources.getResourceTypeName(resId) + '/' +
                resources.getResourceEntryName(resId))
    }

    fun findAppDescription(appName: String): String {
        val appDescriptionFile =
                File("$applicationFilesDir/apps/$appName/$appName.txt")
        if (!appDescriptionFile.exists()) {
            return resources.getString(R.string.error_app_description_not_found)
        }
        return appDescriptionFile.readText()
    }
}