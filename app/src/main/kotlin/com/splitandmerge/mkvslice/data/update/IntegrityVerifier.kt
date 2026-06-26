package com.splitandmerge.mkvslice.data.update

import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class IntegrityVerifier @Inject constructor(
    private val installedAppMetaProvider: InstalledAppMetaProvider,
    private val apkMetaProvider: ApkMetaProvider
) {
    private class VerifiedImpl(
        override val file: File,
        override val expectedLength: Long
    ) : Verified

    fun versionCompare(installed: Int, manifest: Int): Boolean {
        return manifest > installed
    }

    fun sha256Matches(file: File, expectedHex: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            var bytes = input.read(buffer)
            while (bytes >= 0) {
                digest.update(buffer, 0, bytes)
                bytes = input.read(buffer)
            }
        }
        val computedHex = digest.digest().joinToString("") { "%02x".format(it) }
        return computedHex.equals(expectedHex, ignoreCase = true)
    }

    fun sizeMatches(file: File, expectedSize: Long): Boolean {
        return file.length() == expectedSize
    }

    fun signerMatches(installedSignerDigests: Set<String>, apkSignerDigests: Set<String>): Boolean {
        if (installedSignerDigests.isEmpty() || apkSignerDigests.isEmpty()) return false
        return installedSignerDigests == apkSignerDigests
    }

    fun packageNameMatches(installedPackageName: String, apkPackageName: String?): Boolean {
        return installedPackageName == apkPackageName
    }

    fun archiveVersionMatches(manifestVersionCode: Int, apkVersionCode: Int?): Boolean {
        return manifestVersionCode == apkVersionCode
    }

    fun verifyAndFinalize(tmpFile: File, finalFile: File, manifest: UpdateManifest): Verified? {
        // Size Check
        if (!sizeMatches(tmpFile, manifest.sizeBytes)) {
            return null
        }

        // SHA-256 Check (re-reading from disk)
        if (!sha256Matches(tmpFile, manifest.sha256)) {
            return null
        }

        // Package Name Check
        val installedPackageName = installedAppMetaProvider.getPackageName()
        val apkPackageName = apkMetaProvider.getPackageName(tmpFile.absolutePath)
        if (!packageNameMatches(installedPackageName, apkPackageName)) {
            return null
        }

        // Version Code Check
        val apkVersionCode = apkMetaProvider.getVersionCode(tmpFile.absolutePath)
        if (!archiveVersionMatches(manifest.versionCode, apkVersionCode)) {
            return null
        }

        // Signer Check
        val installedSigners = installedAppMetaProvider.getSignerDigests()
        val apkSigners = apkMetaProvider.getSignerDigests(tmpFile.absolutePath)
        if (!signerMatches(installedSigners, apkSigners)) {
            return null
        }

        // Multiple Signers mismatch check
        val installedHasMultiple = installedAppMetaProvider.hasMultipleSigners()
        val apkHasMultiple = apkMetaProvider.hasMultipleSigners(tmpFile.absolutePath)
        if (installedHasMultiple != apkHasMultiple) {
            return null
        }

        // Atomic rename tmp -> final only after full verify passes
        if (finalFile.exists()) {
            finalFile.delete()
        }
        if (!tmpFile.renameTo(finalFile)) {
            return null
        }

        return VerifiedImpl(finalFile, finalFile.length())
    }
}
