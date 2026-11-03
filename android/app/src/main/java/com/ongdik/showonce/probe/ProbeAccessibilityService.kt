package com.ongdik.showonce.probe

import android.accessibilityservice.AccessibilityService
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.ongdik.showonce.probe.snapshot.AccessibilityNodeView
import com.ongdik.showonce.probe.snapshot.AppAllowList
import com.ongdik.showonce.probe.snapshot.ClickRecord
import com.ongdik.showonce.probe.snapshot.NodeRecord
import com.ongdik.showonce.probe.snapshot.RepeatFilter
import com.ongdik.showonce.probe.snapshot.ScreenInfo
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
 * - 허용 목록(AppAllowList)에 없는 앱은 노드를 읽지 않고 앱 이름과 이벤트 종류만 남긴다.
 * - 입력란에서 숨긴 값이 다른 글자로 다시 보이면 TypedValueMasker가 가린다.
 * - 로그는 디버그 빌드에서만 나간다 (ProbeLog).
 */
class ProbeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val repeatFilter = RepeatFilter()
    private val typedValueMasker = TypedValueMasker()
    // 이벤트가 연결 전에 와도 안전하게, 처음 쓸 때 만든다. packageName은 이 앱 자신의 패키지
    private val appAllowList by lazy { AppAllowList.forProbe(ownPackageName = packageName) }
    private var pendingEventTime = 0L
    private val captureWhenSettled = Runnable { captureScreen("contentSettled", pendingEventTime) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        ProbeLog.initialize(this)
        ProbeLog.info("접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
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
        val contentAllowed = appAllowList.isContentAllowed(screenPackageName)

        // 허용 목록 밖 앱은 노드를 아예 읽지 않는다 (읽고 버리는 게 아니라).
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
            nodes = nodes.records,
            truncated = nodes.truncated,
            contentRecorded = contentAllowed,
        )
        // Logcat 한 줄 길이 제한 때문에 요약 한 줄 + 노드 한 줄씩
        ProbeLog.debug("screen " + SnapshotJson.summary(snapshot))
        snapshot.nodes.forEach { nodeRecord -> ProbeLog.debug("node " + SnapshotJson.node(nodeRecord)) }
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
            node = clickedNode,
            contentRecorded = contentAllowed,
        )
        ProbeLog.debug("click " + SnapshotJson.click(click))
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
        ProbeLog.info("접근성 서비스 종료됨")
        super.onDestroy()
    }

    private data class CollectedNodes(val records: List<NodeRecord>, val truncated: Boolean)

    companion object {
        private const val SETTLE_DELAY_MS = 500L
    }
}
