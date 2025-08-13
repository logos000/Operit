package com.ai.assistance.operit.core.tools.automatic.config

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.automatic.UIRouteConfig
import com.ai.assistance.operit.core.tools.automatic.JsonUIRouteConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Manages UI automation configuration packages.
 *
 * This manager handles loading, importing, and accessing automation configurations
 * (defined in .json files) from both internal assets and external user storage.
 * It allows for a modular and extensible system for defining app-specific automation flows.
 *
 * Package Sources:
 * 1.  **Assets**: Built-in packages bundled with the application.
 * 2.  **External Storage**: User-imported packages for custom automation.
 */
class AutomationPackageManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AutomationPackageManager"
        private const val CONFIG_DIR_NAME = "automation_configs"
        
        @Volatile private var INSTANCE: AutomationPackageManager? = null

        fun getInstance(context: Context): AutomationPackageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutomationPackageManager(context.applicationContext).also {
                    INSTANCE = it
                    it.loadAllPackages()
                }
            }
        }
    }

    // A map to hold metadata of all available packages (name to description).
    private val availablePackageInfo = mutableMapOf<String, AutomationPackageInfo>()

    // The directory where user-imported configuration files are stored.
    private val externalConfigsDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), CONFIG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
            Log.d(TAG, "Created external configs directory at: ${dir.absolutePath}")
        }
        dir
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Loads all automation packages from both assets and external storage.
     */
    private fun loadAllPackages() {
        Log.d(TAG, "Loading all automation packages...")
        availablePackageInfo.clear()
        loadPackagesFromAssets()
        loadPackagesFromExternalStorage()
        Log.d(TAG, "Finished loading. Total packages available: ${availablePackageInfo.size}")
    }

    /**
     * Scans the "automation_configs" directory in assets and loads package info.
     */
    private fun loadPackagesFromAssets() {
        try {
            val assetManager = context.assets
            val packageFiles = assetManager.list(CONFIG_DIR_NAME) ?: return

            packageFiles.filter { it.endsWith(".json") }.forEach { fileName ->
                val filePath = "$CONFIG_DIR_NAME/$fileName"
                try {
                    val jsonString = assetManager.open(filePath).bufferedReader().use { it.readText() }
                    val jsonConfig = json.decodeFromString<JsonUIRouteConfig>(jsonString)
                    val info = AutomationPackageInfo(
                        name = jsonConfig.appName,
                        packageName = jsonConfig.packageName,
                        description = jsonConfig.description,
                        isBuiltIn = true,
                        fileName = fileName
                    )
                    availablePackageInfo[info.name] = info
                    Log.d(TAG, "Loaded built-in package: ${info.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load or parse asset package: $fileName", e)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error accessing assets directory for automation packages.", e)
        }
    }

    /**
     * Scans the external storage directory and loads package info.
     */
    private fun loadPackagesFromExternalStorage() {
        if (!externalConfigsDir.exists() || !externalConfigsDir.isDirectory) return

        externalConfigsDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                val jsonString = file.readText()
                val jsonConfig = json.decodeFromString<JsonUIRouteConfig>(jsonString)
                val info = AutomationPackageInfo(
                    name = jsonConfig.appName,
                    packageName = jsonConfig.packageName,
                    description = jsonConfig.description,
                    isBuiltIn = false,
                    fileName = file.name
                )
                // User-imported packages can override built-in ones with the same name.
                availablePackageInfo[info.name] = info
                Log.d(TAG, "Loaded external package: ${info.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load or parse external package: ${file.name}", e)
            }
        }
    }
    
    /**
     * Retrieves a list of all available automation packages' metadata.
     * This is intended for displaying package options to the user.
     *
     * @return A list of [AutomationPackageInfo] objects.
     */
    fun getAllPackageInfo(): List<AutomationPackageInfo> {
        return availablePackageInfo.values.toList()
    }

    /**
     * Finds and loads a UIRouteConfig based on the application's package name.
     * It prioritizes user-imported packages over built-in ones if duplicates exist.
     *
     * @param appPackageName The package name of the target application (e.g., "com.example.app").
     * @return The corresponding [UIRouteConfig], or null if no matching configuration is found.
     */
    fun getConfigByAppPackageName(appPackageName: String): UIRouteConfig? {
        // Find the package info that matches the app package name, prioritizing external packages.
        val packageInfo = availablePackageInfo.values
            .sortedBy { it.isBuiltIn } // false (external) comes before true (built-in)
            .find { it.packageName == appPackageName }

        if (packageInfo == null) {
            Log.w(TAG, "No automation config found for app package name: $appPackageName")
            return null
        }

        // Use the private loader function to get the full config.
        return loadConfig(packageInfo.name)
    }

    /**
     * Loads the full UIRouteConfig for a given package name.
     * This is a private helper function.
     *
     * @param appName The user-facing name of the package to load.
     * @return The [UIRouteConfig] object, or null if the package is not found or fails to load.
     */
    private fun loadConfig(appName: String): UIRouteConfig? {
        val packageInfo = availablePackageInfo[appName]
        if (packageInfo == null) {
            Log.w(TAG, "Attempted to load a non-existent package by name: $appName")
            return null
        }

        try {
            val jsonString = if (packageInfo.isBuiltIn) {
                val filePath = "$CONFIG_DIR_NAME/${packageInfo.fileName}"
                context.assets.open(filePath).bufferedReader().use { it.readText() }
            } else {
                File(externalConfigsDir, packageInfo.fileName).readText()
            }
            return UIRouteConfig.loadFromJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config for package: $appName", e)
            return null
        }
    }

    /**
     * Imports a new automation package from a file path into the app's external storage.
     *
     * @param filePath The absolute path to the .json configuration file.
     * @return A string message indicating success or failure.
     */
    fun importPackage(filePath: String): String {
        val sourceFile = File(filePath)
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return "Error: Cannot access source file at $filePath"
        }
        if (!sourceFile.name.endsWith(".json")) {
            return "Error: Only .json files are supported for import."
        }

        try {
            val destFile = File(externalConfigsDir, sourceFile.name)
            if (destFile.exists()) {
                Log.w(TAG, "Overwriting existing package: ${sourceFile.name}")
            }
            
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Successfully copied package to: ${destFile.absolutePath}")
            
            // Reload packages to include the new one.
            loadAllPackages()
            return "Successfully imported package: ${sourceFile.name}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import package from: $filePath", e)
            return "Error: Failed to import package. ${e.message}"
        }
    }

    /**
     * Deletes a user-imported package. Built-in packages cannot be deleted.
     *
     * @param packageName The name of the package to delete.
     * @return True if deletion was successful, false otherwise.
     */
    fun deletePackage(packageName: String): Boolean {
        val packageInfo = availablePackageInfo[packageName]
        if (packageInfo == null) {
            Log.w(TAG, "Cannot delete non-existent package: $packageName")
            return false
        }
        if (packageInfo.isBuiltIn) {
            Log.w(TAG, "Cannot delete a built-in package: $packageName")
            return false
        }

        val fileToDelete = File(externalConfigsDir, packageInfo.fileName)
        return try {
            if (fileToDelete.delete()) {
                Log.d(TAG, "Successfully deleted package: $packageName")
                loadAllPackages() // Refresh the package list
                true
            } else {
                Log.e(TAG, "Failed to delete package file: ${fileToDelete.absolutePath}")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error while deleting package: $packageName", e)
            false
        }
    }
}

/**
 * Data class holding metadata about an automation package.
 */
data class AutomationPackageInfo(
    val name: String,
    val packageName: String,
    val description: String,
    val isBuiltIn: Boolean,
    val fileName: String
) 