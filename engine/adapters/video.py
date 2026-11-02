"""녹화 영상 파일을 읽는 어댑터.

PyAV(FFmpeg의 Python 바인딩)로 영상을 읽는다.
- 디먹싱(demux): mp4 상자에서 압축된 패킷을 꺼낸다. 패킷에 PTS(표시 시각)가 붙어 있다.
- 디코딩(decode): 패킷의 압축을 풀어 실제 그림을 만든다. 비용이 크다.

영상 정보 확인(probe_video)은 시각만 필요하므로 디먹싱만 한다.
"""

from __future__ import annotations

import statistics
from dataclasses import dataclass
from pathlib import Path

import av

from engine.contracts import VideoInfo

# 가장 긴 프레임 간격이 중앙값의 이 배수를 넘으면 가변 프레임률로 본다.
# 고정 프레임률이면 간격이 거의 일정해서 비율이 1에 가깝다.
VFR_RATIO_THRESHOLD = 2.0


@dataclass(frozen=True)
class VideoProbe:
    """영상 정보 확인 결과."""

    info: VideoInfo
    codec: str
    frame_count: int
    nominal_fps: float | None  # 파일에 적힌 기준 fps (실제 간격과 다를 수 있다)
    average_fps: float  # 실제 프레임 수 / 첫~마지막 프레임 시간
    interval_min_ms: float
    interval_median_ms: float
    interval_max_ms: float
    is_variable_frame_rate: bool
    frame_times_sec: list[float]  # 모든 프레임의 표시 시각 (시각순)


@dataclass(frozen=True)
class _StreamMeta:
    """컨테이너가 열려 있는 동안 꺼내 둔 스트림 정보 (닫은 뒤에는 스트림에 접근할 수 없다)."""

    width: int
    height: int
    codec: str
    nominal_fps: float | None


def read_frame_times(path: Path) -> tuple[list[float], _StreamMeta]:
    """영상 스트림의 모든 프레임 시각(초)을 시각순으로 돌려준다. 디코딩하지 않는다."""
    with av.open(str(path)) as container:
        stream = container.streams.video[0]
        rate = stream.guessed_rate or stream.base_rate
        meta = _StreamMeta(
            width=stream.width,
            height=stream.height,
            codec=stream.codec_context.name,
            nominal_fps=float(rate) if rate else None,
        )
        times = [
            float(packet.pts * packet.time_base)
            for packet in container.demux(stream)
            # 마지막에 오는 빈 패킷(flush 용도)은 PTS가 없다
            if packet.pts is not None
        ]
    # B 프레임이 있으면 패킷이 저장 순서로 나오므로 표시 순서(시각순)로 정렬한다
    times.sort()
    if not times:
        raise ValueError(f"영상 프레임이 없다: {path}")
    start = times[0]
    # 첫 프레임이 0초가 아닌 영상도 있어서, 첫 프레임을 0초로 맞춘다
    return [t - start for t in times], meta


def probe_video(path: Path) -> VideoProbe:
    """영상의 기본 정보와 프레임 간격 분포를 확인한다."""
    path = Path(path)
    times, meta = read_frame_times(path)
    intervals_ms = [(b - a) * 1000 for a, b in zip(times, times[1:], strict=False)]

    if intervals_ms:
        median = statistics.median(intervals_ms)
        longest = max(intervals_ms)
        is_vfr = median > 0 and longest / median > VFR_RATIO_THRESHOLD
    else:
        median = longest = 0.0
        is_vfr = False

    span = times[-1] - times[0]
    return VideoProbe(
        info=VideoInfo(
            source_name=path.name,
            width=meta.width,
            height=meta.height,
            duration_sec=round(span, 6),
        ),
        codec=meta.codec,
        frame_count=len(times),
        nominal_fps=meta.nominal_fps,
        average_fps=(len(times) - 1) / span if span > 0 else 0.0,
        interval_min_ms=min(intervals_ms, default=0.0),
        interval_median_ms=median,
        interval_max_ms=longest,
        is_variable_frame_rate=is_vfr,
        frame_times_sec=times,
    )
