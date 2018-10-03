package tech.ula.utils

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import tech.ula.model.entities.Asset
import tech.ula.model.entities.Filesystem
import java.io.File

class FilesystemUtility(
    private val applicationFilesDirPath: String,
    private val execUtility: ExecUtility,
    private val logger: LogUtility = LogUtility()
) {

    private val filesystemExtractionSuccess = ".success_filesystem_extraction"
    private val filesystemExtractionFailure = ".failure_filesystem_extraction"

    private fun getSupportDirectoryPath(targetDirectoryName: String): String {
        return "$applicationFilesDirPath/$targetDirectoryName/support"
    }

    fun copyDistributionAssetsToFilesystem(targetFilesystemName: String, distributionType: String) {
        val sharedDirectory = File("$applicationFilesDirPath/$distributionType")
        val targetDirectory = File("$applicationFilesDirPath/$targetFilesystemName/support")
        if (!targetDirectory.exists()) targetDirectory.mkdirs()
        sharedDirectory.copyRecursively(targetDirectory, overwrite = true)
        targetDirectory.walkBottomUp().forEach {
            if (it.name == "support") {
                return
            }
            makePermissionsUsable(targetDirectory.path, it.name)
        }
    }

    fun removeRootfsFilesFromFilesystem(targetFilesystemName: String) {
        val supportDirectory = File(getSupportDirectoryPath(targetFilesystemName))
        supportDirectory.walkBottomUp().forEach {
            if (it.name.contains("rootfs.tar.gz")) it.delete()
        }
    }

    fun extractFilesystem(filesystem: Filesystem, targetDirectoryName: String, listener: (String) -> Any) {
        val command = "../support/execInProot.sh /support/extractFilesystem.sh"
        try {
            val env = HashMap<String, String>()
            env["INITIAL_USERNAME"] = filesystem.defaultUsername
            env["INITIAL_PASSWORD"] = filesystem.defaultPassword
            env["INITIAL_VNC_PASSWORD"] = filesystem.defaultVncPassword

            execUtility.wrapWithBusyboxAndExecute(targetDirectoryName, command, listener, environmentVars = env)
        } catch (err: Exception) {
            logger.logRuntimeErrorForCommand(functionName = "extractFilesystem", command = command, err = err)
        }
    }

    fun isExtractionComplete(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        val success = File("$supportPath/$filesystemExtractionSuccess")
        val failure = File("$supportPath/$filesystemExtractionFailure")
        return success.exists() || failure.exists()
    }

    fun hasFilesystemBeenSuccessfullyExtracted(targetDirectoryName: String): Boolean {
        val supportPath = getSupportDirectoryPath(targetDirectoryName)
        return File("$supportPath/$filesystemExtractionSuccess").exists()
    }

    fun areAllRequiredAssetsPresent(
        targetDirectoryName: String,
        distributionAssetList: List<Asset>
    ): Boolean {
        val supportDirectory = File(getSupportDirectoryPath(targetDirectoryName))
        if (!supportDirectory.exists() || !supportDirectory.isDirectory) return false

        val supportDirectoryFileNames = supportDirectory.listFiles().map { it.name }
        return distributionAssetList.all {
            supportDirectoryFileNames.contains(it.name)
        }
    }

    fun deleteFilesystem(filesystemId: Long) {
        val directory = File("$applicationFilesDirPath/$filesystemId")
        launch(CommonPool) {
            if (directory.exists() && directory.isDirectory)
                directory.deleteRecursively()
            val isDirectoryDeleted = directory.deleteRecursively()
            if (isDirectoryDeleted) {
                val successMessage = "Successfully deleted filesystem located at: $directory"
                logger.v("Filesystem Delete", successMessage)
            } else {
                val errorMessage = "Error in attempting to delete filesystem located at: $directory"
                logger.e("Filesystem Delete", errorMessage)
            }
        }
    }

    fun moveAppScriptToRequiredLocations(appName: String, appFilesystem: Filesystem) {
        // TODO add error cases
        val appScriptSource = File("$applicationFilesDirPath/apps/$appName/$appName.sh")
        val appScriptSupportTarget = File("$applicationFilesDirPath/${appFilesystem.id}/support/$appName.sh")
        val appScriptProfileDTarget = File("$applicationFilesDirPath/${appFilesystem.id}/etc/profile.d/$appName.sh")
        appScriptSource.copyTo(appScriptSupportTarget, overwrite = true)
        appScriptSource.copyTo(appScriptProfileDTarget, overwrite = true)
    }
}
