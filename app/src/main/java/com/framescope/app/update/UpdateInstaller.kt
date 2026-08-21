package com.framescope.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class InstallResult {
    object Success : InstallResult()
    object PermissionRequired : InstallResult()
    data class SignatureMismatch(val errorMessage: String) : InstallResult()
    data class Failed(val reason: String) : InstallResult()
}

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canInstallPackages(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    suspend fun installApk(
        apkFile: File,
        onResult: (InstallResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            withContext(Dispatchers.Main) {
                onResult(InstallResult.Failed("APK file is missing or corrupted (0 bytes)"))
            }
            return@withContext
        }

        withContext(Dispatchers.Main) {
            performStandardPackageInstall(context, apkFile, onResult)
        }
    }

    private fun performStandardPackageInstall(
        context: Context,
        apkFile: File,
        onResult: (InstallResult) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            onResult(InstallResult.PermissionRequired)
            return
        }

        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.openWrite("package", 0, apkFile.length()).use { output ->
                FileInputStream(apkFile).use { input ->
                    input.copyTo(output)
                }
                session.fsync(output)
            }

            val intent = Intent(context, UpdateInstallerReceiver::class.java)
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, pendingIntentFlags)

            session.commit(pendingIntent.intentSender)
            session.close()
        } catch (e: Exception) {
            onResult(InstallResult.Failed(e.localizedMessage ?: "Failed to launch package installer session"))
        }
    }

    fun openUnknownAppSourcesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    suspend fun handleSignatureMismatch(
        downloadedApkFile: File?,
        targetVersionName: String,
        onAppClosing: () -> Unit
    ) = withContext(Dispatchers.IO) {
        if (downloadedApkFile != null && downloadedApkFile.exists()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "FrameScope_v${targetVersionName}-release.apk")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { output ->
                            downloadedApkFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    }
                } else {
                    val publicDownloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(publicDownloadsDir, "FrameScope_v${targetVersionName}-release.apk")
                    downloadedApkFile.copyTo(targetFile, overwrite = true)
                }
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Saved FrameScope_v${targetVersionName}-release.apk to Downloads folder",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("Failed to copy APK to MediaStore/Downloads", e)
            }
        }

        withContext(Dispatchers.Main) {
            onAppClosing()
        }

        withContext(Dispatchers.Main) {
            try {
                val uninstallIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(uninstallIntent)
            } catch (e: Exception) {
                com.framescope.app.utils.FrameScopeLog.e("Failed to launch uninstaller intent", e)
            }
        }
    }
}
