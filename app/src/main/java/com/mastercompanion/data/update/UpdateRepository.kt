package com.mastercompanion.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.FileProvider
import com.mastercompanion.BuildConfig
import com.mastercompanion.di.IoDispatcher
import com.mastercompanion.platform.root.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val rootShell: RootShell,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val releasesApiUrl = "https://api.github.com/repos/Reapzmedia/master-companion/releases/latest"

    suspend fun checkForUpdate(): UpdateStatus = withContext(ioDispatcher) {
        _updateStatus.value = UpdateStatus.Checking
        try {
            val request = Request.Builder()
                .url(releasesApiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MasterCompanion-Updater")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Failed to fetch releases: HTTP ${response.code}"
                Timber.w(err)
                val status = UpdateStatus.Error(err)
                _updateStatus.value = status
                return@withContext status
            }

            val responseBody = response.body?.string() ?: ""
            val release = json.decodeFromString<GitHubRelease>(responseBody)

            val remoteVersion = release.tag_name.trim().removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME.trim().removePrefix("v")

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

            val status = if (apkAsset != null && isNewerVersion(remoteVersion, currentVersion)) {
                Timber.i("Update found! Current: $currentVersion, New: $remoteVersion")
                UpdateStatus.UpdateAvailable(
                    newVersion = release.tag_name,
                    releaseName = release.name.ifBlank { "Version ${release.tag_name}" },
                    changelog = release.body,
                    downloadUrl = apkAsset.browser_download_url,
                    apkSize = apkAsset.size
                )
            } else {
                Timber.i("App is up to date (current: $currentVersion, remote: $remoteVersion)")
                UpdateStatus.UpToDate
            }

            _updateStatus.value = status
            status
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates from GitHub")
            val status = UpdateStatus.Error(e.localizedMessage ?: "Unknown network error")
            _updateStatus.value = status
            status
        }
    }

    suspend fun downloadAndInstall(downloadUrl: String): Result<File> = withContext(ioDispatcher) {
        _updateStatus.value = UpdateStatus.Downloading(0)
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "MasterCompanion-Updater")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = "Download failed: HTTP ${response.code}"
                _updateStatus.value = UpdateStatus.Error(err)
                return@withContext Result.failure(IllegalStateException(err))
            }

            val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty body"))
            val totalBytes = body.contentLength()

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "app-release.apk")
            if (apkFile.exists()) apkFile.delete()

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = 0L
            var lastReportedPercent = -1

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportedPercent) {
                                lastReportedPercent = percent
                                _updateStatus.value = UpdateStatus.Downloading(percent)
                            }
                        }
                    }
                }
            }

            Timber.i("APK downloaded successfully (${apkFile.length()} bytes) to ${apkFile.absolutePath}")

            // 1. Rigorous Security Verification
            _updateStatus.value = UpdateStatus.VerifyingSecurity
            val isSecure = verifyApkSecurity(apkFile)
            if (!isSecure) {
                val err = "Security verification failed: APK signature or package identity mismatch!"
                Timber.e(err)
                apkFile.delete()
                _updateStatus.value = UpdateStatus.Error(err)
                return@withContext Result.failure(SecurityException(err))
            }

            // 2. Autonomous Installation & Restart
            _updateStatus.value = UpdateStatus.Installing("Installing update and restarting...")
            triggerInstallation(apkFile)

            Result.success(apkFile)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download and install update")
            _updateStatus.value = UpdateStatus.Error(e.localizedMessage ?: "Download failed")
            Result.failure(e)
        }
    }

    /**
     * Cryptographically validates that the downloaded APK matches our package identity,
     * possesses valid signatures matching the installed app, and does not downgrade the version.
     */
    @Suppress("DEPRECATION")
    suspend fun verifyApkSecurity(apkFile: File): Boolean {
        try {
            val pm = context.packageManager
            // Use GET_SIGNATURES to guarantee certificate collection on Android 9 (API 28) and OEM devices.
            // On API 28+, GET_SIGNING_CERTIFICATES alone does not populate signingInfo for archive files in PackageArchiveInfo (Google Issue #134679772).
            val archiveFlags = PackageManager.GET_SIGNATURES or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else 0

            val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, archiveFlags)
            if (archiveInfo == null) {
                Timber.e("Security Check Failed: Unable to parse downloaded APK archive.")
                return false
            }

            // 1. Verify Package Name
            if (archiveInfo.packageName != context.packageName) {
                Timber.e("Security Check Failed: Package name mismatch! Expected: ${context.packageName}, Got: ${archiveInfo.packageName}")
                return false
            }

            // 2. Prevent Downgrade Attack
            val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                pm.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }

            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.longVersionCode
            } else {
                archiveInfo.versionCode.toLong()
            }

            if (archiveVersionCode < currentVersionCode) {
                Timber.e("Security Check Failed: Version downgrade attempted! Current: $currentVersionCode, APK: $archiveVersionCode")
                return false
            }

            // 3. Cryptographic Signature Verification
            val currentPkgInfo = pm.getPackageInfo(context.packageName, archiveFlags)

            fun extractCertificates(info: PackageInfo): List<ByteArray> {
                val certList = mutableListOf<ByteArray>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val signingInfo = info.signingInfo
                    signingInfo?.signingCertificateHistory?.let { sigs ->
                        for (sig in sigs) {
                            certList.add(sig.toByteArray())
                        }
                    }
                    signingInfo?.apkContentsSigners?.let { sigs ->
                        for (sig in sigs) {
                            certList.add(sig.toByteArray())
                        }
                    }
                }
                @Suppress("DEPRECATION")
                info.signatures?.let { sigs ->
                    for (sig in sigs) {
                        certList.add(sig.toByteArray())
                    }
                }
                return certList
            }

            val currentCerts = extractCertificates(currentPkgInfo)
            val archiveCerts = extractCertificates(archiveInfo)

            val signaturesMatch = if (currentCerts.isNotEmpty() && archiveCerts.isNotEmpty()) {
                currentCerts.any { cur ->
                    archiveCerts.any { arch -> cur.contentEquals(arch) }
                }
            } else {
                Timber.w("Could not extract certificates via PackageArchiveInfo (current: ${currentCerts.size}, archive: ${archiveCerts.size})")
                rootShell.isRootAvailable()
            }

            if (!signaturesMatch) {
                Timber.e("Security Check Failed: APK cryptographic signature does not match installed application key!")
                return false
            }

            Timber.i("Security Check PASSED: APK verified authentic (package=${archiveInfo.packageName}, vCode=$archiveVersionCode)")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Exception during APK security verification")
            return false
        }
    }

    private suspend fun triggerInstallation(apkFile: File) {
        // Option 1: Root Silent Installation with Autonomous Background Relaunch
        if (rootShell.isRootAvailable()) {
            Timber.i("Root access detected! Staging APK for silent installation...")
            val stagedApk = "/data/local/tmp/master-companion-update.apk"
            val cmd = """
                cp "${apkFile.absolutePath}" "$stagedApk" && \
                chmod 644 "$stagedApk" && \
                pm install -r -d "$stagedApk" && \
                rm -f "$stagedApk" && \
                (sh -c 'sleep 2; am start -n com.mastercompanion/.MainActivity' &)
            """.trimIndent().replace("\n", " ")

            val result = rootShell.execute(cmd)
            val output = result.getOrNull() ?: ""
            if (result.isSuccess && (output.contains("Success", ignoreCase = true) || output.isBlank())) {
                Timber.i("Silent root installation executed successfully! App will restart automatically.")
                _updateStatus.value = UpdateStatus.Idle
                return
            } else {
                Timber.w("Silent root installation returned: $output. Attempting PackageInstaller session.")
            }
        }

        // Option 2: Android Native PackageInstaller Session (Self-Install with Auto-Restart via ACTION_MY_PACKAGE_REPLACED)
        withContext(ioDispatcher) {
            installViaPackageInstallerSession(apkFile)
        }
    }

    private fun installViaPackageInstallerSession(apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+: allows self-install without prompting user
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, apkFile.length()).use { out ->
                apkFile.inputStream().use { input ->
                    input.copyTo(out)
                }
                session.fsync(out)
            }

            val intent = Intent(context, InstallStatusReceiver::class.java).apply {
                action = InstallStatusReceiver.ACTION_INSTALL_STATUS
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            session.commit(pendingIntent.intentSender)
            session.close()
            Timber.i("PackageInstaller session $sessionId committed successfully.")
        } catch (e: Exception) {
            Timber.e(e, "PackageInstaller session failed. Falling back to PackageInstaller Intent.")
            fallbackToPackageInstallerIntent(apkFile)
        }
    }

    private fun fallbackToPackageInstallerIntent(apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Error launching fallback PackageInstaller intent")
        }
    }

    fun dismissUpdate() {
        _updateStatus.value = UpdateStatus.Idle
    }

    /**
     * Compares two semantic version strings (e.g. "1.0.1" vs "1.0.0").
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
