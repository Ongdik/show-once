package com.ongdik.showonce.probe

import android.accessibilityservice.AccessibilityService
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.ongdik.showonce.probe.record.RecordFileStore
import com.ongdik.showonce.probe.snapshot.AccessibilityNodeView
import com.ongdik.showonce.probe.snapshot.AppAllowList
import com.ongdik.showonce.probe.snapshot.ClickRecord
import com.ongdik.showonce.probe.snapshot.NodeRecord
import com.ongdik.showonce.probe.snapshot.RepeatFilter
import com.ongdik.showonce.probe.snapshot.ScreenInfo
import com.ongdik.showonce.probe.snapshot.SessionHeader
import com.ongdik.showonce.probe.snapshot.Snapshot
import com.ongdik.showonce.probe.snapshot.SnapshotBuilder
import com.ongdik.showonce.probe.snapshot.SnapshotJson
import com.ongdik.showonce.probe.snapshot.TypedValueMasker

/**
 * 다른 앱의 화면 구조와 클릭을 읽어 Logcat에 남기는 접근성 서비스.
 *
 * 이 클래스의 함수는 우리가 부르지 않는다. 사용자가 설정에서 서비스를 켜면
 * 시스템이 onServiceConnected를, 화면이 바뀌거나 클릭이 일어나면 onAccessibilityEvent를 부른다.
 * 어떤 이벤트를 받을지는 res/xml/accessibility_service_config.xml에서 정한다.
 *
 * 언제 화면을 찍나:
 * - 화면 전환: 바로 한 번 + 0.5초 뒤 한 번 (막 바뀐 순간엔 트리가 덜 그려졌을 수 있다)
 * - 내용 변경: 0.5초 동안 조용해지면 (웹 페이지는 화면 전환 없이 내용만 바뀐다)
 * 같은 내용이 반복되면 RepeatFilter가 건너뛴다.
 *
 * 안전장치 (#16):
 * - 허용 목록(AppAllowList)에 없는 앱은 우리 코드가 노드 내용을 읽지 않고 앱 이름과 이벤트 종류만 남긴다.
 *   (rootInActiveWindow를 부르는 순간 안드로이드가 하위 노드를 메모리에 미리 가져올 수는 있다.
 *    API 33+의 미리 가져오기 끄기 옵션은 실시간 안내(10번)에서 성능과 함께 검토한다.)
 * - 입력란에서 숨긴 값이 다른 글자로 다시 보이면 TypedValueMasker가 가린다.
 * - 로그는 디버그 빌드에서만 나간다 (ProbeLog).
 *
 * 기록 파일 (#17): 같은 기록을 앱 내부 저장소(files/records/)에도 한 줄 JSON으로 쌓는다.
 * USB 디버깅을 끈 채 은행 앱을 확인할 때도 기록이 남게 하기 위해서다. 파일 쓰기는 별도 스레드 하나에서 한다.
 */
class ProbeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val repeatFilter = RepeatFilter()
    private val typedValueMasker = TypedValueMasker()
    private val appAllowList = AppAllowList.forProbe()
    private var pendingEventTime = 0L
    private var recordFileStore: RecordFileStore? = null
    private val recordWriter: ExecutorService = Executors.newSingleThreadExecutor()
    private val captureWhenSettled = Runnable { captureScreen("contentSettled", pendingEventTime) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ProbeLog.initialize(this)
        startRecordFile()
        ProbeLog.info("접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 이 앱 자신의 화면은 시험 대상이 아니라서 아무것도 남기지 않는다.
        // (남기면 "기록 모두 지우기" 직후 이 앱 화면이 바로 새 파일로 기록된다)
        if (event.packageName?.toString() == packageName) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                captureScreen("windowChanged", event.eventTime)
                scheduleSettledCapture(event.eventTime)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> scheduleSettledCapture(event.eventTime)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> logClick(event)
        }
    }

    // 마지막 변경 후 0.5초가 지나야 찍는다. 그 안에 또 바뀌면 예약을 다시 건다.
    private fun scheduleSettledCapture(eventTime: Long) {
        pendingEventTime = eventTime
        handler.removeCallbacks(captureWhenSettled)
        handler.postDelayed(captureWhenSettled, SETTLE_DELAY_MS)
    }

    private fun captureScreen(reason: String, eventTime: Long) {
        val root = rootInActiveWindow ?: return
        val screen = screenInfo()
        val screenPackageName = root.packageName?.toString()
        if (screenPackageName == packageName) return // 0.5초 뒤 찍는 사이 이 앱으로 돌아왔을 수 있다
        val contentAllowed = appAllowList.isContentAllowed(screenPackageName)

        // 허용 목록 밖 앱은 노드 내용을 읽지 않는다 (읽고 버리는 게 아니라). 앱 이름만 쓴다.
        val nodes = if (contentAllowed) {
            val result = SnapshotBuilder.collect(AccessibilityNodeView(root), screen)
            val nowMs = System.currentTimeMillis()
            typedValueMasker.remember(result.hiddenValues, nowMs)
            CollectedNodes(typedValueMasker.maskAll(result.nodes, nowMs), result.truncated)
        } else {
            CollectedNodes(emptyList(), truncated = false)
        }
        if (!repeatFilter.isNew(screenPackageName, nodes.records)) return

        val snapshot = Snapshot(
            reason = reason,
            packageName = screenPackageName,
            screen = screen,
            wallTimeMs = System.currentTimeMillis(),
            eventTimeMs = eventTime,
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            nodes = nodes.records,
            truncated = nodes.truncated,
            contentRecorded = contentAllowed,
        )
        // Logcat 한 줄 길이 제한 때문에 요약 한 줄 + 노드 한 줄씩
        ProbeLog.debug("screen " + SnapshotJson.summary(snapshot))
        snapshot.nodes.forEach { nodeRecord -> ProbeLog.debug("node " + SnapshotJson.node(nodeRecord)) }
        writeRecordLine(SnapshotJson.snapshotLine(snapshot))
    }

    private fun logClick(event: AccessibilityEvent) {
        val screen = screenInfo()
        val clickPackageName = event.packageName?.toString()
        val contentAllowed = appAllowList.isContentAllowed(clickPackageName)
        val clickedNode = if (contentAllowed) {
            event.source?.let { source ->
                val nodeView = AccessibilityNodeView(source)
                val nowMs = System.currentTimeMillis()
                SnapshotBuilder.hiddenTypedValue(nodeView)?.let { typedValueMasker.remember(listOf(it), nowMs) }
                typedValueMasker.mask(SnapshotBuilder.toClickedNodeRecord(nodeView, screen), nowMs)
            }
        } else {
            null
        }
        val click = ClickRecord(
            packageName = clickPackageName,
            screen = screen,
            wallTimeMs = System.currentTimeMillis(),
            eventTimeMs = event.eventTime,
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            node = clickedNode,
            contentRecorded = contentAllowed,
        )
        val clickLine = SnapshotJson.click(click)
        ProbeLog.debug("click $clickLine")
        writeRecordLine(clickLine)
    }

    private fun startRecordFile() {
        val fileNameFormat = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT)
        val store = RecordFileStore(
            recordsDirectory = recordsDirectory(this),
            nextFileName = { "records-${fileNameFormat.format(Date())}${RecordFileStore.FILE_EXTENSION}" },
        )
        val headerLine = SnapshotJson.sessionHeader(sessionHeader())
        recordWriter.execute { runCatchingWriteFailure { store.startSession(headerLine) } }
        recordFileStore = store
    }

    // 같은 순간의 시계 세 개를 머리말에 남긴다 (세 줄이 몇 ms 안에 연달아 읽힌다)
    private fun sessionHeader(): SessionHeader {
        // 플래그 없는 getPackageInfo(이름, 0)는 API 33에서 새 형태가 생겼지만, minSdk 26에서도 되는 이 형태를 쓴다.
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return SessionHeader(
            formatVersion = RECORD_FORMAT_VERSION,
            wallTimeMs = System.currentTimeMillis(),
            uptimeMs = SystemClock.uptimeMillis(),
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            appVersionName = packageInfo.versionName,
            appVersionCode = versionCode,
            allowedPackages = appAllowList.allowedPackages.toList(),
            settleDelayMs = SETTLE_DELAY_MS,
            maxNodesPerSnapshot = SnapshotBuilder.DEFAULT_MAX_NODES,
            typedValueRememberMs = TypedValueMasker.REMEMBER_DURATION_MS,
        )
    }

    // 줄은 여기서(이벤트 스레드) 다 만들어 두고, 파일 쓰기만 별도 스레드에 맡긴다.
    private fun writeRecordLine(line: String) {
        val store = recordFileStore ?: return
        recordWriter.execute { runCatchingWriteFailure { store.append(line) } }
    }

    // 쓰기 스레드에서 예외(저장 공간 부족 등)가 나면 프로세스가 통째로 종료되고 접근성 서비스도 같이 죽는다.
    // USB를 끈 채 은행 앱을 확인할 때는 그걸 바로 알 수 없으므로, 잡아서 로그만 남기고 계속한다.
    // 로그에는 예외 종류만 남기고 줄 내용은 남기지 않는다.
    private inline fun runCatchingWriteFailure(write: () -> Unit) {
        try {
            write()
        } catch (writeFailure: Exception) {
            ProbeLog.info("기록 파일 쓰기 실패: ${writeFailure.javaClass.simpleName}")
        }
    }

    // 노드 위치(getBoundsInScreen)와 같은 기준인 화면 전체 크기
    private fun screenInfo(): ScreenInfo {
        val windowManager = getSystemService(WindowManager::class.java)
        val (screenWidthPx, screenHeightPx) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayBounds = windowManager.maximumWindowMetrics.bounds
            displayBounds.width() to displayBounds.height()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            displayMetrics.widthPixels to displayMetrics.heightPixels
        }
        val rotation = getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        return ScreenInfo(screenWidthPx, screenHeightPx, rotation)
    }

    override fun onInterrupt() {
        // 시스템이 피드백을 중단시킬 때 불린다 (음성 안내 앱용). 이 앱은 따로 멈출 것이 없다.
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureWhenSettled)
        recordWriter.shutdown() // 이미 맡긴 쓰기는 끝까지 한다
        ProbeLog.info("접근성 서비스 종료됨")
        super.onDestroy()
    }

    private data class CollectedNodes(val records: List<NodeRecord>, val truncated: Boolean)

    companion object {
        private const val SETTLE_DELAY_MS = 500L
        const val RECORD_FORMAT_VERSION = 1

        /** 기록 파일 위치. 앱 내부 저장소라 다른 앱이나 USB 파일 탐색기로 읽을 수 없다 */
        fun recordsDirectory(context: android.content.Context): File = File(context.filesDir, "records")
    }
}
