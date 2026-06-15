package com.splitandmerge.mkvslice.domain.storage

import android.content.ContentResolver
import android.content.Context
import android.content.UriPermission
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.StructStatVfs
import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.FileDescriptor

class OutputFolderValidatorTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var validator: OutputFolderValidator

    @Before
    fun setup() {
        context = mockk()
        contentResolver = mockk()
        every { context.contentResolver } returns contentResolver
        validator = OutputFolderValidator(context)
        
        mockkStatic(Uri::class)
        mockkStatic(DocumentFile::class)
        mockkStatic(Os::class)
        mockkStatic(android.util.Log::class)
        
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.v(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<Throwable>()) } returns 0
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun test_blankUri_returnsNotReachable() {
        val result = validator.validate("", 100L)
        assertEquals(OutputFolderValidation.NotReachable, result)
    }

    @Test
    fun test_nullDocumentFile_returnsNotReachable() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        every { DocumentFile.fromTreeUri(context, mockUri) } returns null

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.NotReachable, result)
    }

    @Test
    fun test_documentFileNotDirectory_returnsNotReachable() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns false

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.NotReachable, result)
    }

    @Test
    fun test_contentResolverQueryThrows_returnsNotReachable() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        every { contentResolver.query(mockUri, null, null, null, null) } throws RuntimeException("Query failed")

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.NotReachable, result)
    }

    @Test
    fun test_noPersistedPermission_returnsPermissionRevoked() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        every { contentResolver.persistedUriPermissions } returns emptyList()

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.PermissionRevoked, result)
        
    }

    @Test
    fun test_readOnlyPermission_returnsPermissionRevoked() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns false
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.PermissionRevoked, result)
    }

    @Test
    fun test_createFileReturnsNull_returnsNotWritable() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns true
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        every { documentFile.createFile(any(), any()) } returns null

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.NotWritable("Could not create probe file"), result)
    }

    @Test
    fun test_createFileThrows_returnsNotWritable() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns true
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        every { documentFile.createFile(any(), any()) } throws RuntimeException("Create failed")

        val result = validator.validate(uriStr, 100L)
        assertEquals(OutputFolderValidation.NotWritable("Create failed"), result)
    }

    @Test
    fun test_insufficientSpace_returnsInsufficientSpaceWithCorrectBytes() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns true
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        val probeFile = mockk<DocumentFile>()
        every { documentFile.createFile(any(), any()) } returns probeFile
        every { probeFile.canWrite() } returns true
        val probeUri = mockk<Uri>()
        every { probeFile.uri } returns probeUri
        every { probeFile.delete() } returns true

        val pfd = mockk<ParcelFileDescriptor>()
        every { contentResolver.openFileDescriptor(probeUri, "w") } returns pfd
        val fd = FileDescriptor()
        every { pfd.fileDescriptor } returns fd
        every { pfd.close() } returns Unit

        val testStats = StructStatVfs(1024L, 0, 0, 0, 50L, 0, 0, 0, 0, 0, 0)
        try {
            StructStatVfs::class.java.getField("f_bsize").apply {
                isAccessible = true
                set(testStats, 1024L)
            }
            StructStatVfs::class.java.getField("f_bavail").apply {
                isAccessible = true
                set(testStats, 50L)
            }
        } catch (e: Exception) {}
        every { Os.fstatvfs(any()) } returns testStats

        val result = validator.validate(uriStr, 100000L)
        assertEquals(OutputFolderValidation.InsufficientSpace(100000L, 51200L), result)
    }

    @Test
    fun test_sufficientSpace_returnsOk() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns true
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        val probeFile = mockk<DocumentFile>()
        every { documentFile.createFile(any(), any()) } returns probeFile
        every { probeFile.canWrite() } returns true
        val probeUri = mockk<Uri>()
        every { probeFile.uri } returns probeUri
        every { probeFile.delete() } returns true

        val pfd = mockk<ParcelFileDescriptor>()
        every { contentResolver.openFileDescriptor(probeUri, "w") } returns pfd
        val fd = FileDescriptor()
        every { pfd.fileDescriptor } returns fd
        every { pfd.close() } returns Unit

        val testStats = StructStatVfs(1024L, 0, 0, 0, 200L, 0, 0, 0, 0, 0, 0)
        try {
            StructStatVfs::class.java.getField("f_bsize").apply {
                isAccessible = true
                set(testStats, 1024L)
            }
            StructStatVfs::class.java.getField("f_bavail").apply {
                isAccessible = true
                set(testStats, 200L)
            }
        } catch (e: Exception) {}
        every { Os.fstatvfs(any()) } returns testStats

        val result = validator.validate(uriStr, 100000L)
        assertEquals(OutputFolderValidation.Ok, result)
    }

    @Test
    fun test_fstatvfsThrows_doesNotBlock_returnsOk() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns true
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        val probeFile = mockk<DocumentFile>()
        every { documentFile.createFile(any(), any()) } returns probeFile
        every { probeFile.canWrite() } returns true
        val probeUri = mockk<Uri>()
        every { probeFile.uri } returns probeUri
        every { probeFile.delete() } returns true

        val pfd = mockk<ParcelFileDescriptor>()
        every { contentResolver.openFileDescriptor(probeUri, "w") } returns pfd
        val fd = FileDescriptor()
        every { pfd.fileDescriptor } returns fd
        every { pfd.close() } returns Unit

        every { Os.fstatvfs(any()) } throws android.system.ErrnoException("fstatvfs", android.system.OsConstants.ENOSYS)

        val result = validator.validate(uriStr, 100000L)
        assertEquals(OutputFolderValidation.Ok, result)
    }

    @Test
    fun test_noPersistedPermission_assumeFalse_doesNotReturnPermissionRevoked() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        every { contentResolver.persistedUriPermissions } returns emptyList()

        // createFile returns null, so it should proceed past permission check and hit NotWritable
        every { documentFile.createFile(any(), any()) } returns null

        val result = validator.validate(uriStr, 100L, assumePermissionPersisted = false)
        assertEquals(OutputFolderValidation.NotWritable("Could not create probe file"), result)
    }

    @Test
    fun test_readOnlyPermission_assumeFalse_doesNotReturnPermissionRevoked() {
        val uriStr = "content://test"
        val mockUri = mockk<Uri>()
        every { Uri.parse(uriStr) } returns mockUri
        
        val documentFile = mockk<DocumentFile>()
        every { DocumentFile.fromTreeUri(context, mockUri) } returns documentFile
        every { documentFile.exists() } returns true
        every { documentFile.isDirectory } returns true

        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(mockUri, null, null, null, null) } returns cursor
        
        val permission = mockk<UriPermission>()
        every { permission.uri } returns mockUri
        every { permission.isReadPermission } returns true
        every { permission.isWritePermission } returns false
        every { contentResolver.persistedUriPermissions } returns listOf(permission)

        every { documentFile.createFile(any(), any()) } returns null

        val result = validator.validate(uriStr, 100L, assumePermissionPersisted = false)
        assertEquals(OutputFolderValidation.NotWritable("Could not create probe file"), result)
    }
}
