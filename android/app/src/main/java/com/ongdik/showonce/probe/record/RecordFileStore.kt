package com.ongdik.showonce.probe.record

import java.io.File

/**
 * 기록 줄(JSON Lines)을 앱 내부 저장소의 파일에 쌓는다. 안드로이드 API를 쓰지 않아 PC에서 실제 파일로 테스트한다.
 *
 * - 파일 첫 줄은 세션 머리말(SessionHeader). 파일을 나눌 때도 같은 머리말을 다시 써서 파일마다 혼자 읽을 수 있게 한다.
 * - 파일 하나가 maxFileBytes를 넘으면 새 파일을 연다.
 * - 전체가 maxTotalBytes를 넘으면 가장 오래된 파일부터 지운다 (폰 저장 공간을 계속 먹지 않게).
 * - 앱 화면에서 "기록 모두 지우기"로 쓰던 파일이 지워져도, 다음 줄에서 새 파일을 열어 이어 쓴다.
 *
 * 줄 내용은 만들지 않는다. 받은 줄을 그대로 쓰기만 한다. 줄은 SnapshotJson을 거친 것만 들어온다 (#17 결정).
 * 여러 스레드에서 불려도 되게 모든 공개 함수는 synchronized다.
 */
class RecordFileStore(
    private val recordsDirectory: File,
    private val nextFileName: () -> String,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
) {

    private var sessionHeaderLine: String? = null
    private var currentFile: File? = null

    @Synchronized
    fun startSession(headerLine: String) {
        sessionHeaderLine = headerLine
        currentFile = openNewFile(headerLine)
    }

    @Synchronized
    fun append(line: String) {
        val headerLine = sessionHeaderLine ?: return // 세션 전에는 쓰지 않는다
        val lineBytes = (line + "\n").toByteArray(Charsets.UTF_8)

        var targetFile = currentFile
        val needsNewFile = targetFile == null ||
            !targetFile.exists() ||
            targetFile.length() + lineBytes.size > maxFileBytes
        if (needsNewFile) targetFile = openNewFile(headerLine)

        targetFile!!.appendBytes(lineBytes)
        currentFile = targetFile
        deleteOldestFilesOverLimit(keep = targetFile)
    }

    private fun openNewFile(headerLine: String): File {
        recordsDirectory.mkdirs()
        val newFile = File(recordsDirectory, nextFileName())
        newFile.writeBytes((headerLine + "\n").toByteArray(Charsets.UTF_8))
        return newFile
    }

    private fun deleteOldestFilesOverLimit(keep: File) {
        val filesOldestFirst = recordFiles(recordsDirectory)
        var totalBytes = filesOldestFirst.sumOf { it.length() }
        for (oldFile in filesOldestFirst) {
            if (totalBytes <= maxTotalBytes) break
            if (oldFile == keep) continue
            totalBytes -= oldFile.length()
            oldFile.delete()
        }
    }

    data class Summary(val fileCount: Int, val totalBytes: Long)

    companion object {
        const val DEFAULT_MAX_FILE_BYTES = 5L * 1024 * 1024
        const val DEFAULT_MAX_TOTAL_BYTES = 20L * 1024 * 1024
        const val FILE_EXTENSION = ".jsonl"

        /** 기록 파일 목록, 오래된 것부터 (파일 이름이 시각 순서라서 이름순 = 시간순) */
        fun recordFiles(recordsDirectory: File): List<File> =
            recordsDirectory.listFiles { file -> file.isFile && file.name.endsWith(FILE_EXTENSION) }
                ?.sortedBy { it.name }
                .orEmpty()

        fun summarize(recordsDirectory: File): Summary {
            val files = recordFiles(recordsDirectory)
            return Summary(fileCount = files.size, totalBytes = files.sumOf { it.length() })
        }

        fun deleteAll(recordsDirectory: File) {
            recordFiles(recordsDirectory).forEach { it.delete() }
        }
    }
}
