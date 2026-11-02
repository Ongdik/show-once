"""show-once 명령어.

    uv run show-once probe   <영상>                 영상 정보와 프레임 간격 확인
    uv run show-once extract <영상> [옵션]          S0 프레임 추출
    uv run show-once transitions <작업 폴더>        S1 화면 전환 후보
    uv run show-once report  <작업 폴더>            처리 결과 확인용 HTML 리포트

명령이 몇 개뿐이라 표준 라이브러리 argparse를 쓴다.
"""

from __future__ import annotations

import argparse
import sys
import time
from collections.abc import Sequence
from pathlib import Path

from engine.adapters.video import probe_video
from engine.jobs import FRAMESET_FILE, TRANSITIONS_FILE
from engine.report import build_report
from engine.stages.s0_extract import ExtractConfig, extract_frames
from engine.stages.s1_transitions import TransitionConfig, detect_transitions

DEFAULT_JOBS_ROOT = Path("data/jobs")


def default_job_dir(video: Path) -> Path:
    """작업 폴더 기본값: data/jobs/<영상 이름(확장자 제외)>"""
    return DEFAULT_JOBS_ROOT / video.stem


def cmd_probe(args: argparse.Namespace) -> int:
    probe = probe_video(args.video)
    info = probe.info
    nominal = f"{probe.nominal_fps:g}" if probe.nominal_fps else "알 수 없음"
    print(f"파일        {info.source_name}")
    print(f"크기        {info.width}x{info.height}, 코덱 {probe.codec}")
    print(f"길이        {info.duration_sec:.3f}초, 프레임 {probe.frame_count}장")
    print(f"fps         기준 {nominal} / 실제 평균 {probe.average_fps:.1f}")
    print(
        f"프레임 간격  최소 {probe.interval_min_ms:.1f}ms · 중앙값 {probe.interval_median_ms:.1f}ms"
        f" · 최대 {probe.interval_max_ms:.1f}ms"
    )
    kind = "가변 프레임률(VFR)" if probe.is_variable_frame_rate else "고정 프레임률(CFR)"
    print(f"판별        {kind}")
    return 0


def cmd_extract(args: argparse.Namespace) -> int:
    job_dir = args.job or default_job_dir(args.video)
    config = ExtractConfig(min_interval_ms=args.min_interval_ms, analysis_width=args.width)
    started = time.perf_counter()
    frameset = extract_frames(args.video, job_dir, config)
    elapsed = time.perf_counter() - started
    print(f"프레임 {len(frameset.frames)}장 추출 ({elapsed:.1f}초)")
    print(f"결과        {job_dir / FRAMESET_FILE}")
    return 0


def cmd_transitions(args: argparse.Namespace) -> int:
    started = time.perf_counter()
    config = TransitionConfig(
        pixel_threshold=args.pixel_threshold,
        motion_ratio=args.motion_ratio,
        transition_ratio=args.transition_ratio,
        ignore_top_ratio=args.ignore_top,
    )
    result = detect_transitions(args.job, config)
    elapsed = time.perf_counter() - started
    print(
        f"움직임 구간 {len(result.candidates)}개 중 화면 전환 {len(result.transitions)}개"
        f" ({elapsed:.1f}초)"
    )
    for c in result.transitions:
        print(
            f"  {c.start_sec:6.2f}~{c.end_sec:6.2f}초  프레임 #{c.before_index}→#{c.after_index}"
            f"  전후 변화 {c.total_change_ratio * 100:.1f}%"
        )
    print(f"결과        {args.job / TRANSITIONS_FILE}")
    return 0


def cmd_report(args: argparse.Namespace) -> int:
    report = build_report(args.job)
    print(f"리포트      {report}")
    print("브라우저로 열어서 확인한다. 가림 전 원본 프레임이 들어 있으니 공유하지 않는다.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="show-once", description="화면 녹화를 단계별 가이드로")
    sub = parser.add_subparsers(dest="command", required=True)

    probe = sub.add_parser("probe", help="영상 정보와 프레임 간격 확인")
    probe.add_argument("video", type=Path)
    probe.set_defaults(func=cmd_probe)

    defaults = ExtractConfig()
    extract = sub.add_parser("extract", help="S0 프레임 추출")
    extract.add_argument("video", type=Path)
    extract.add_argument("--job", type=Path, help="작업 폴더 (기본: data/jobs/<영상 이름>)")
    extract.add_argument(
        "--min-interval-ms",
        type=float,
        default=defaults.min_interval_ms,
        help=f"남길 프레임 사이 최소 간격 (기본 {defaults.min_interval_ms:g})",
    )
    extract.add_argument(
        "--width",
        type=int,
        default=defaults.analysis_width,
        help=f"분석용 가로 크기 (기본 {defaults.analysis_width})",
    )
    extract.set_defaults(func=cmd_extract)

    transitions = sub.add_parser("transitions", help="S1 화면 전환 후보")
    transitions.add_argument("job", type=Path, help="작업 폴더 (예: data/jobs/rec01)")
    t_defaults = TransitionConfig()
    transitions.add_argument(
        "--pixel-threshold",
        type=int,
        default=t_defaults.pixel_threshold,
        help=f"픽셀이 바뀌었다고 볼 밝기 차이 0~255 (기본 {t_defaults.pixel_threshold})",
    )
    transitions.add_argument(
        "--motion-ratio",
        type=float,
        default=t_defaults.motion_ratio,
        help=f"움직임으로 볼 바뀐 픽셀 비율 (기본 {t_defaults.motion_ratio})",
    )
    transitions.add_argument(
        "--transition-ratio",
        type=float,
        default=t_defaults.transition_ratio,
        help=f"전환으로 볼 구간 전후 변화 비율 (기본 {t_defaults.transition_ratio})",
    )
    transitions.add_argument(
        "--ignore-top",
        type=float,
        default=t_defaults.ignore_top_ratio,
        help=f"비교에서 뺄 화면 맨 위 비율 (기본 {t_defaults.ignore_top_ratio})",
    )
    transitions.set_defaults(func=cmd_transitions)

    report = sub.add_parser("report", help="처리 결과 확인용 HTML 리포트")
    report.add_argument("job", type=Path, help="작업 폴더 (예: data/jobs/rec01)")
    report.set_defaults(func=cmd_report)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if hasattr(args, "video") and not args.video.is_file():
        parser.error(f"영상 파일이 없다: {args.video}")
    if args.command in ("transitions", "report") and not args.job.is_dir():
        parser.error(f"작업 폴더가 없다: {args.job}")
    try:
        return args.func(args)
    except FileNotFoundError as error:
        parser.error(str(error))


if __name__ == "__main__":
    sys.exit(main())
