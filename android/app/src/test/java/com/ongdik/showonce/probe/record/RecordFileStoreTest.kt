package com.ongdik.showonce.probe.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordFileStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val headerLine = """{"type":"session"}"""

    private fun storeIn(
        recordsDirectory: File,
        maxFileBytes: Long = RecordFileStore.DEFAULT_MAX_FILE_BYTES,
        maxTotalBytes: Long = RecordFileStore.DEFAULT_MAX_TOTAL_BYTES,
    ): RecordFileStore {
        var fileNumber = 0
        return RecordFileStore(
            recordsDirectory = recordsDirectory,
            nextFileName = { "records-%04d.jsonl".format(++fileNumber) },
            maxFileBytes = maxFileBytes,
            maxTotalBytes = maxTotalBytes,
        )
    }

    private fun linesOf(file: File) = file.readLines(Charsets.UTF_8)

    @Test
    fun writesSessionHeaderAsFirstLineAndAppendsRecordLines() {
        val recordsDirectory = File(temporaryFolder.root, "records")
        val store = storeIn(recordsDirectory)
        store.startSession(headerLine)
        store.append("""{"type":"screen","n":1}""")
        store.append("""{"type":"click","n":2}""")

        val files = RecordFileStore.recordFiles(recordsDirectory)
        assertEquals(1, files.size)
        assertEquals(listOf(headerLine, """{"type":"screen","n":1}""", """{"type":"click","n":2}"""), linesOf(files[0]))
    }

    @Test
    fun ignoresLinesAppendedBeforeSessionStarts() {
        val recordsDirectory = File(temporaryFolder.root, "records")
        storeIn(recordsDirectory).append("""{"type":"screen"}""")
        assertTrue(RecordFileStore.recordFiles(recordsDirectory).isEmpty())
    }

    @Test
    fun opensNewFileWithSameHeaderWhenFileSizeLimitIsExceeded() {
        val recordsDirectory = File(temporaryFolder.root, "records")
        val store = storeIn(recordsDirectory, maxFileBytes = 60)
        store.startSession(headerLine)
        val recordLine = """{"type":"screen","padding":"xxxxxxxxxx"}""" // 40바이트 + 줄바꿈. 머리말(19) + 이 줄(41) = 60이라 파일 하나에 한 줄만 들어간다
        store.append(recordLine)
        store.append(recordLine)

        val files = RecordFileStore.recordFiles(recordsDirectory)
        assertEquals(2, files.size)
        files.forEach { file -> assertEquals(headerLine, linesOf(file).first()) }
        assertEquals(listOf(headerLine, recordLine), linesOf(files[1]))
    }

    @Test
    fun deletesOldestFilesWhenTotalSizeLimitIsExceeded() {
        val recordsDirectory = File(temporaryFolder.root, "records")
        val store = storeIn(recordsDirectory, maxFileBytes = 60, maxTotalBytes = 130)
        store.startSession(headerLine)
        val recordLine = """{"type":"screen","padding":"xxxxxxxxxx"}"""
        repeat(5) { store.append(recordLine) }

        val remainingNames = RecordFileStore.recordFiles(recordsDirectory).map { it.name }
        assertTrue(RecordFileStore.summarize(recordsDirectory).totalBytes <= 130)
        assertEquals("records-0005.jsonl", remainingNames.last())
        assertTrue("records-0001.jsonl" !in remainingNames)
    }

    @Test
    fun continuesInNewFileWhenCurrentFileWasDeletedByUser() {
        val recordsDirectory = File(temporaryFolder.root, "records")
        val store = storeIn(recordsDirectory)
        store.startSession(headerLine)
        store.append("""{"n":1}""")

        RecordFileStore.deleteAll(recordsDirectory)
        assertEquals(0, RecordFileStore.summarize(recordsDirectory).fileCount)

        store.append("""{"n":2}""")
        val files = RecordFileStore.recordFiles(recordsDirectory)
        assertEquals(1, files.size)
        assertEquals(listOf(headerLine, """{"n":2}"""), linesOf(files[0]))
    }

    @Test
    fun summarizesFileCountAndTotalBytes() {
        val recordsDirectory = File(temporaryFolder.root, "records")
        val store = storeIn(recordsDirectory)
        store.startSession(headerLine)
        store.append("""{"text":"확인"}""")

        val summary = RecordFileStore.summarize(recordsDirectory)
        assertEquals(1, summary.fileCount)
        val expectedBytes = (headerLine + "\n" + """{"text":"확인"}""" + "\n").toByteArray(Charsets.UTF_8).size.toLong()
        assertEquals(expectedBytes, summary.totalBytes)
    }

    @Test
    fun summarizesMissingDirectoryAsEmpty() {
        val summary = RecordFileStore.summarize(File(temporaryFolder.root, "not-created"))
        assertEquals(RecordFileStore.Summary(fileCount = 0, totalBytes = 0), summary)
    }
}
