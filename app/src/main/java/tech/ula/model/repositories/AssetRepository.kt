package tech.ula.model.repositories

import tech.ula.model.entities.Asset
import tech.ula.utils.AssetListPreferences
import tech.ula.utils.ConnectionUtility
import tech.ula.utils.TimestampPreferences
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.net.ssl.SSLHandshakeException

class AssetRepository(
    deviceArchitecture: String,
    distributionType: String,
    private val applicationFilesDirPath: String,
    private val timestampPreferences: TimestampPreferences,
    private val assetListPreferences: AssetListPreferences,
    private val connectionUtility: ConnectionUtility = ConnectionUtility()
) {

    private val allAssetListTypes = listOf(
            "support" to "all",
            "support" to deviceArchitecture,
            distributionType to "all",
            distributionType to deviceArchitecture
    )

    fun getCachedAssetLists(): List<List<Asset>> {
        return assetListPreferences.getAssetLists(allAssetListTypes)
    }

    fun getDistributionAssetsList(distributionType: String): List<Asset> {
        val distributionAssetLists = allAssetListTypes.filter {
            (assetType, _) ->
            assetType == distributionType
        }
        val allAssets = assetListPreferences.getAssetLists(distributionAssetLists).flatten()
        return allAssets.filter { !(it.name.contains("rootfs.tar.gz")) }
    }

    @Throws(Exception::class)
    fun retrieveAllRemoteAssetLists(): List<List<Asset>> {
        val allAssetLists = ArrayList<List<Asset>>()
        allAssetListTypes.forEach {
            (assetType, architectureType) ->
            val assetList = retrieveAndParseAssetList(assetType, architectureType)
            allAssetLists.add(assetList)
            assetListPreferences.setAssetList(assetType, architectureType, assetList)
        }
        return allAssetLists.toList()
    }

    private fun retrieveAndParseAssetList(
        assetType: String,
        architectureType: String,
        protocol: String = "https"
    ): List<Asset> {
        val assetList = ArrayList<Asset>()

        val url = "$protocol://github.com/CypherpunkArmory/UserLAnd-Assets-" +
                "$assetType/raw/master/assets/$architectureType/assets.txt"
        try {
            val reader = BufferedReader(InputStreamReader(connectionUtility.getUrlInputStream(url)))

            reader.forEachLine {
                val (filename, timestampAsString) = it.split(" ")
                if (filename == "assets.txt") return@forEachLine
                val remoteTimestamp = timestampAsString.toLong()
                assetList.add(Asset(filename, assetType, architectureType, remoteTimestamp))
            }

            reader.close()
            return assetList.toList()
        } catch (err: SSLHandshakeException) {
            // Try again with http if https fails
            return retrieveAndParseAssetList(assetType, architectureType, "http")
        } catch (err: Exception) {
            throw object : Exception("Error getting asset list") {}
        }
    }

    fun doesAssetNeedToUpdated(asset: Asset): Boolean {
        val assetFile = File("$applicationFilesDirPath/${asset.pathName}")

        if (!assetFile.exists()) return true

        val localTimestamp = timestampPreferences.getSavedTimestampForFile(asset.concatenatedName)
        return localTimestamp < asset.remoteTimestamp
    }
}