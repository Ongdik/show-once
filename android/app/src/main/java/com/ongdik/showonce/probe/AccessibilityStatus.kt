package com.ongdik.showonce.probe

/**
 * 접근성 서비스가 켜져 있는지 판단한다.
 *
 * 안드로이드는 켜진 접근성 서비스 목록을 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES에
 * "패키지/클래스" 항목을 ':'로 이어 붙인 문자열 하나로 저장한다.
 * 클래스는 ".ProbeAccessibilityService"처럼 패키지를 생략한 형태로도 저장될 수 있다.
 *
 * 안드로이드 API(ComponentName 등)를 쓰지 않는 순수 함수로 두어 PC에서 바로 테스트한다.
 */
object AccessibilityStatus {

    fun isEnabled(enabledServices: String?, packageName: String, className: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        return enabledServices.split(':').any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2) return@any false
            val (pkg, cls) = parts
            val fullClass = if (cls.startsWith('.')) pkg + cls else cls
            pkg.equals(packageName, ignoreCase = true) &&
                fullClass.equals(className, ignoreCase = true)
        }
    }
}
