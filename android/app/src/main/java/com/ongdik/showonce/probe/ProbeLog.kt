package com.ongdik.showonce.probe

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * 이 앱의 로그 출력을 한 곳에 모은다. 디버그 빌드일 때만 출력한다.
 *
 * Log.d는 릴리스 빌드에도 그대로 남아 adb로 볼 수 있다. 화면 글자가 들어간 기록이
 * 부모님 폰의 Logcat에 남지 않도록, 앱이 디버그 빌드(FLAG_DEBUGGABLE)일 때만 내보낸다.
 * Gradle의 BuildConfig 대신 앱 정보 플래그를 써서 빌드 설정을 바꾸지 않았다.
 */
object ProbeLog {

    const val TAG = "ShowOnceProbe"

    @Volatile
    private var enabled = false

    fun initialize(context: Context) {
        enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun debug(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun info(message: String) {
        if (enabled) Log.i(TAG, message)
    }
}
