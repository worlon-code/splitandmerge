package com.splitandmerge.mkvslice.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

interface InstalledAppMetaProvider {
    fun getPackageName(): String
    fun getVersionCode(): Int
    fun getSignerDigests(): Set<String>
    fun hasMultipleSigners(): Boolean
}

interface ApkMetaProvider {
    fun getPackageName(apkPath: String): String?
    fun getVersionCode(apkPath: String): Int?
    fun getSignerDigests(apkPath: String): Set<String>
    fun hasMultipleSigners(apkPath: String): Boolean
}

class RealInstalledAppMetaProvider(private val context: Context) : InstalledAppMetaProvider {
    override fun getPackageName(): String = context.packageName

    override fun getVersionCode(): Int {
        val pm = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        @Suppress("DEPRECATION")
        return packageInfo.versionCode
    }

    override fun getSignerDigests(): Set<String> {
        val pm = context.packageManager
        val packageName = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            val signers = signingInfo.apkContentsSigners ?: emptyArray()
            if (signers.isEmpty()) emptySet() else signers.map { sha256(it.toByteArray()) }.toSet()
        } else {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = packageInfo.signatures ?: emptyArray()
            if (signatures.isEmpty()) emptySet() else signatures.map { sha256(it.toByteArray()) }.toSet()
        }
    }

    override fun hasMultipleSigners(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pm = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            return packageInfo.signingInfo?.hasMultipleSigners() ?: false
        }
        return false
    }
}

class RealApkMetaProvider(private val context: Context) : ApkMetaProvider {
    override fun getPackageName(apkPath: String): String? {
        val pm = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkPath, 0)
        }
        return packageInfo?.packageName
    }

    override fun getVersionCode(apkPath: String): Int? {
        val pm = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkPath, 0)
        }
        @Suppress("DEPRECATION")
        return packageInfo?.versionCode
    }

    override fun getSignerDigests(apkPath: String): Set<String> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            } ?: return emptySet()
            
            packageInfo.applicationInfo?.let {
                it.sourceDir = apkPath
                it.publicSourceDir = apkPath
            }
            
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            val signers = signingInfo.apkContentsSigners ?: emptyArray()
            if (signers.isEmpty()) emptySet() else signers.map { sha256(it.toByteArray()) }.toSet()
        } else {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNATURES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
            } ?: return emptySet()
            
            packageInfo.applicationInfo?.let {
                it.sourceDir = apkPath
                it.publicSourceDir = apkPath
            }
            
            val signatures = packageInfo.signatures ?: emptyArray()
            if (signatures.isEmpty()) emptySet() else signatures.map { sha256(it.toByteArray()) }.toSet()
        }
    }

    override fun hasMultipleSigners(apkPath: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pm = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            } ?: return false
            
            packageInfo.applicationInfo?.let {
                it.sourceDir = apkPath
                it.publicSourceDir = apkPath
            }
            
            return packageInfo.signingInfo?.hasMultipleSigners() ?: false
        }
        return false
    }
}

private fun sha256(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
