package com.ongdik.showonce.probe.snapshot

import java.util.Locale

/**
 * 기록을 JSON 한 줄로 만든다. 필드가 10개 남짓이라 라이브러리 없이 직접 쓴다 (이슈 #14 결정).
 * 정확해야 하는 곳은 문자열 이스케이프뿐이고, 그 부분은 테스트로 지킨다.
 * 필드가 늘거나 JSON을 다시 읽어야 하면 kotlinx.serialization으로 바꾼다.
 *
 * 스냅샷은 Logcat 한 줄 길이 제한(약 4KB) 때문에 요약 한 줄 + 노드 한 줄씩으로 나눈다.
 */
object SnapshotJson {

    fun summary(s: Snapshot): String = obj(
        "type" to "screen",
        "reason" to s.reason,
        "package" to s.packageName,
        "screen" to Raw(screen(s.screen)),
        "wallTimeMs" to s.wallTimeMs,
        "eventTimeMs" to s.eventTimeMs,
        "nodeCount" to s.nodes.size,
        "truncated" to s.truncated,
    )

    fun click(c: ClickRecord): String = obj(
        "type" to "click",
        "package" to c.packageName,
        "screen" to Raw(screen(c.screen)),
        "wallTimeMs" to c.wallTimeMs,
        "eventTimeMs" to c.eventTimeMs,
        "node" to c.node?.let { Raw(node(it)) },
    )

    fun node(n: NodeRecord): String = obj(
        "index" to n.index,
        "depth" to n.depth,
        "text" to n.text,
        "description" to n.description,
        "viewId" to n.viewId,
        "className" to n.className,
        "bounds" to Raw(bounds(n.bounds)),
        "clickable" to n.clickable,
        "editable" to n.editable,
        "textHidden" to n.textHidden,
    )

    private fun screen(s: ScreenInfo) =
        obj("widthPx" to s.widthPx, "heightPx" to s.heightPx, "rotation" to s.rotation)

    // [left, top, right, bottom]. Locale.ROOT: 기기 언어가 독일어 등이어도 소수점이 쉼표로 바뀌지 않게.
    private fun bounds(b: RatioRect) =
        listOf(b.left, b.top, b.right, b.bottom)
            .joinToString(",", "[", "]") { String.format(Locale.ROOT, "%.4f", it) }

    /** 이미 JSON인 조각 (따옴표로 감싸지 않는다) */
    private class Raw(val json: String)

    private fun obj(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:${value(v)}" }

    private fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.json
        is String -> quote(v)
        is Boolean, is Int, is Long -> v.toString()
        else -> quote(v.toString())
    }

    /** JSON 문자열 이스케이프 (RFC 8259 7장): 따옴표, 역슬래시, U+0020 미만 제어 문자 */
    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format(Locale.ROOT, "\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
