package com.ongdik.showonce.probe

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 다른 앱의 화면 변화와 클릭을 받아 보는 접근성 서비스.
 *
 * 이 클래스의 함수는 우리가 부르지 않는다. 사용자가 설정에서 서비스를 켜면
 * 시스템이 onServiceConnected를, 화면이 바뀌거나 클릭이 일어나면 onAccessibilityEvent를 부른다.
 * 어떤 이벤트를 받을지는 res/xml/accessibility_service_config.xml에서 정한다.
 *
 * 지금은 "서비스가 켜지고 이벤트가 들어온다"만 확인한다. 화면 글자·노드 트리 기록은 다음 단계.
 */
class ProbeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "접근성 서비스 연결됨")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val type = AccessibilityEvent.eventTypeToString(event.eventType)
        Log.d(TAG, "$type package=${event.packageName} class=${event.className}")
    }

    override fun onInterrupt() {
        // 시스템이 피드백을 중단시킬 때 불린다 (음성 안내 앱용). 이 앱은 따로 멈출 것이 없다.
    }

    override fun onDestroy() {
        Log.i(TAG, "접근성 서비스 종료됨")
        super.onDestroy()
    }

    companion object {
        const val TAG = "ShowOnceProbe"
    }
}
