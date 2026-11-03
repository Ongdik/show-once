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

    fun summary(snapshot: Snapshot): String = jsonObject(
        "type" to "screen",
        "reason" to snapshot.reason,
        "package" to snapshot.packageName,
        "screen" to RawJson(screenJson(snapshot.screen)),
        "wallTimeMs" to snapshot.wallTimeMs,
        "eventTimeMs" to snapshot.eventTimeMs,
        "nodeCount" to snapshot.nodes.size,
        "truncated" to snapshot.truncated,
    )

    fun click(click: ClickRecord): String = jsonObject(
        "type" to "click",
        "package" to click.packageName,
        "screen" to RawJson(screenJson(click.screen)),
        "wallTimeMs" to click.wallTimeMs,
        "eventTimeMs" to click.eventTimeMs,
        "node" to click.node?.let { RawJson(node(it)) },
    )

    fun node(record: NodeRecord): String = jsonObject(
        "index" to record.index,
        "depth" to record.depth,
        "text" to record.text,
        "description" to record.description,
        "viewId" to record.viewId,
        "className" to record.className,
        "bounds" to RawJson(boundsJson(record.bounds)),
        "clickable" to record.clickable,
        "editable" to record.editable,
        "textHidden" to record.textHidden,
    )

    private fun screenJson(screen: ScreenInfo) = jsonObject(
        "widthPx" to screen.widthPx,
        "heightPx" to screen.heightPx,
        "rotation" to screen.rotation,
    )

    // [left, top, right, bottom]. Locale.ROOT: 기기 언어가 독일어 등이어도 소수점이 쉼표로 바뀌지 않게.
    private fun boundsJson(bounds: RatioRect) =
        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            .joinToString(",", "[", "]") { ratio -> String.format(Locale.ROOT, "%.4f", ratio) }

    /** 이미 JSON인 조각 (따옴표로 감싸지 않는다) */
    private class RawJson(val json: String)

    private fun jsonObject(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(",", "{", "}") { (fieldName, fieldValue) ->
            "${quote(fieldName)}:${jsonValue(fieldValue)}"
        }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is RawJson -> value.json
        is String -> quote(value)
        is Boolean, is Int, is Long -> value.toString()
        else -> quote(value.toString())
    }

    /** JSON 문자열 이스케이프 (RFC 8259 7장): 따옴표, 역슬래시, U+0020 미만 제어 문자 */
    fun quote(text: String): String {
        val escaped = StringBuilder(text.length + 2).append('"')
        for (character in text) {
            when (character) {
                '"' -> escaped.append("\\\"")
                '\\' -> escaped.append("\\\\")
                '\n' -> escaped.append("\\n")
                '\r' -> escaped.append("\\r")
                '\t' -> escaped.append("\\t")
                '\b' -> escaped.append("\\b")
                '\u000C' -> escaped.append("\\f")
                else ->
                    if (character < ' ') {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", character.code))
                    } else {
                        escaped.append(character)
                    }
            }
        }
        return escaped.append('"').toString()
    }
}
