"""처리 결과 확인용 HTML 리포트.

리포트는 구역(섹션)들을 이어붙여 만든다. 각 단계는 자기 결과 파일을 읽어
구역 하나를 그리는 함수를 SECTIONS에 추가하면 된다 (S1, S2가 이어서 붙인다).

가림 전 원본 프레임을 보여주므로 리포트는 비공개 폴더(private/)에 저장한다.
이미지는 복사하지 않고 상대 경로로 가리킨다.
"""

from __future__ import annotations

import os
from collections.abc import Callable
from html import escape
from pathlib import Path

from engine.contracts import FrameSet
from engine.jobs import FRAMESET_FILE, REPORT_FILE

# (작업 폴더, 리포트가 놓일 폴더) → HTML 조각. 결과 파일이 없으면 None.
Section = Callable[[Path, Path], str | None]


def _rel(target: Path, base: Path) -> str:
    """리포트 폴더 기준 상대 경로. Windows에서도 브라우저가 읽도록 / 로 바꾼다."""
    return Path(os.path.relpath(target, base)).as_posix()


def interval_chart_svg(times_sec: list[float], min_interval_ms: float | None) -> str:
    """프레임 간격 그래프. 가로축 시각(초), 세로축 직전 프레임과의 간격(ms).

    데이터 값을 화면 좌표로 바꾼다: x = 왼쪽여백 + (t / 전체시간) * 그림너비,
    y는 SVG에서 아래로 갈수록 커지므로 y = 위여백 + 그림높이 - (간격 / 최대간격) * 그림높이.
    """
    width, height = 900, 220
    left, right, top, bottom = 50, 15, 15, 30
    plot_w, plot_h = width - left - right, height - top - bottom
    gaps = [(b - a) * 1000 for a, b in zip(times_sec, times_sec[1:], strict=False)]
    if not gaps:
        return "<p>프레임이 2장 미만이라 간격 그래프를 그릴 수 없다.</p>"
    duration = max(times_sec[-1], 1e-9)
    max_gap = max(gaps)

    def x(t: float) -> float:
        return left + t / duration * plot_w

    def y(ms: float) -> float:
        return top + plot_h - ms / max_gap * plot_h

    parts = [
        f'<svg viewBox="0 0 {width} {height}" class="chart" role="img" '
        f'aria-label="프레임 간격 그래프">',
        f'<line x1="{left}" y1="{top + plot_h}" x2="{width - right}" y2="{top + plot_h}" '
        f'class="axis"/>',
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top + plot_h}" class="axis"/>',
        f'<text x="{left - 6}" y="{top + 4}" class="label" text-anchor="end">'
        f"{max_gap:.0f}ms</text>",
        f'<text x="{left - 6}" y="{top + plot_h}" class="label" text-anchor="end">0</text>',
        f'<text x="{width - right}" y="{height - 8}" class="label" text-anchor="end">'
        f"{duration:.2f}초</text>",
    ]
    if min_interval_ms is not None and 0 < min_interval_ms < max_gap:
        yy = y(min_interval_ms)
        parts.append(
            f'<line x1="{left}" y1="{yy:.1f}" x2="{width - right}" y2="{yy:.1f}" class="limit"/>'
            f'<text x="{width - right}" y="{yy - 4:.1f}" class="label" text-anchor="end">'
            f"최소 간격 {min_interval_ms:g}ms</text>"
        )
    for i, (t, gap) in enumerate(zip(times_sec[1:], gaps, strict=False), start=1):
        xx = x(t)
        parts.append(
            f'<line x1="{xx:.1f}" y1="{top + plot_h}" x2="{xx:.1f}" y2="{y(gap):.1f}" class="bar">'
            f"<title>#{i} {t:.3f}초, 직전과 {gap:.0f}ms</title></line>"
        )
    parts.append("</svg>")
    return "".join(parts)


def section_s0_frames(job_dir: Path, report_dir: Path) -> str | None:
    """S0 프레임 추출 결과: 요약, 간격 그래프, 썸네일."""
    path = job_dir / FRAMESET_FILE
    if not path.is_file():
        return None
    fs = FrameSet.load(path)
    src = fs.source
    times = [f.time_sec for f in fs.frames]
    params = ", ".join(f"{escape(str(k))}={escape(str(v))}" for k, v in fs.params.items())

    cards = []
    for frame in fs.frames:
        gap = (
            ""
            if frame.index == 0
            else f" · +{(frame.time_sec - times[frame.index - 1]) * 1000:.0f}ms"
        )
        image = escape(_rel(job_dir / frame.image_path, report_dir))
        cards.append(
            f'<figure><a href="{image}" target="_blank"><img src="{image}" loading="lazy" '
            f'alt="프레임 {frame.index}"></a>'
            f"<figcaption>#{frame.index} · {frame.time_sec:.3f}초{gap}</figcaption></figure>"
        )

    min_interval = fs.params.get("min_interval_ms")
    return (
        "<section><h2>S0 프레임 추출</h2>"
        "<table>"
        f"<tr><th>영상</th><td>{escape(src.source_name)} · {src.width}x{src.height} · "
        f"{src.duration_sec:.2f}초</td></tr>"
        f"<tr><th>구현</th><td>{escape(fs.implementation)}</td></tr>"
        f"<tr><th>설정</th><td>{params}</td></tr>"
        f"<tr><th>남은 프레임</th><td>{len(fs.frames)}장</td></tr>"
        "</table>"
        "<h3>프레임 간격</h3>"
        "<p class=hint>막대 하나가 프레임 하나. 짧으면 화면이 움직인 구간, 길면 멈춘 구간.</p>"
        f"{interval_chart_svg(times, min_interval)}"
        f'<h3>프레임 ({len(fs.frames)}장)</h3><div class="grid">{"".join(cards)}</div>'
        "</section>"
    )


SECTIONS: list[Section] = [section_s0_frames]

STYLE = """
body{font-family:system-ui,sans-serif;margin:24px;color:#222}
table{border-collapse:collapse;margin:8px 0}
th,td{padding:4px 10px;text-align:left;border-bottom:1px solid #eee}th{white-space:nowrap}
.chart{width:100%;max-width:900px;background:#fafafa}.axis{stroke:#999}
.bar{stroke:#3b6fd6;stroke-width:2}
.limit{stroke:#d63b3b;stroke-dasharray:4 3}.label{font-size:11px;fill:#666}
.hint{color:#666;font-size:13px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(110px,1fr));gap:10px}
figure{margin:0}img{width:100%;border:1px solid #ddd}figcaption{font-size:11px;color:#555}
"""


def build_report(job_dir: Path) -> Path:
    """작업 폴더의 결과로 리포트를 만들고 경로를 돌려준다."""
    job_dir = Path(job_dir)
    report_path = job_dir / REPORT_FILE
    report_dir = report_path.parent
    sections = [html for section in SECTIONS if (html := section(job_dir, report_dir))]
    if not sections:
        raise FileNotFoundError(f"리포트로 만들 처리 결과가 없다: {job_dir}")
    report_dir.mkdir(parents=True, exist_ok=True)
    report_path.write_text(
        "<!DOCTYPE html><html lang=ko><head><meta charset=utf-8>"
        f"<title>show-once 리포트 · {escape(job_dir.name)}</title><style>{STYLE}</style></head>"
        f"<body><h1>처리 결과 · {escape(job_dir.name)}</h1>"
        "<p class=hint>가림 전 원본 프레임을 담고 있다. 공유하지 않는다.</p>"
        f"{''.join(sections)}</body></html>",
        encoding="utf-8",
    )
    return report_path
