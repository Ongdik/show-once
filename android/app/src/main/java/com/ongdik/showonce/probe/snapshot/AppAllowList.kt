package com.ongdik.showonce.probe.snapshot

/**
 * 화면 내용(노드 글자·위치)을 기록해도 되는 앱 목록.
 *
 * 기본 거부(deny by default): 목록에 없는 앱은 앱 이름과 이벤트 종류만 남기고 내용은 읽지 않는다.
 * 제외 목록(은행·메신저만 빼기)은 목록에서 빠진 민감한 앱이 그대로 기록되므로 쓰지 않는다 (이슈 #16).
 *
 * 위험 ①(은행 앱이 막는가)에는 "은행 앱이 떴다/꺼졌다"만 필요하므로, 은행 앱은 목록에 넣지 않는다.
 */
class AppAllowList(val allowedPackages: Set<String>) {

    fun isContentAllowed(packageName: String?): Boolean =
        packageName != null && packageName in allowedPackages

    companion object {
        /**
         * 시험 앱에서 내용을 볼 앱들. 패키지 이름은 실제 기기에서 `adb shell pm list packages`로 확인한다.
         * 이 앱 자신은 넣지 않는다: 시험 대상이 아니고, 넣으면 "기록 모두 지우기" 직후 이 앱 화면이 바로 다시 기록된다 (#17).
         */
        fun forProbe() = AppAllowList(
            setOf(
                "com.android.settings", // 설정
                "com.google.android.settings.intelligence", // 설정 검색 화면
                "com.android.chrome", // 크롬 (웹 화면 접근성 확인)
                "io.metamask", // MetaMask (위험 ③: 앱 안 브라우저). 테스트 지갑만 쓴다
            ),
        )
    }
}
