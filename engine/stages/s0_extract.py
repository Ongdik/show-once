"""S0 프레임 추출.

녹화 영상을 디코딩해, 샘플링 규칙으로 남길 프레임을 고르고,
분석용 크기로 줄여 작업 폴더에 저장한 뒤 frames.json(FrameSet)을 쓴다.

샘플링 규칙은 이름으로 고르는 교체 가능한 구현이다 (설계 04 P4).
어떤 규칙과 설정으로 만들었는지는 frames.json에 함께 남는다 (설계 03 D2).
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Protocol

from engine.adapters.video import iter_decoded_frames, probe_video
from engine.contracts import Frame, FrameSet
from engine.jobs import PRIVATE_DIR

# 가림 전 원본 프레임이므로 비공개 폴더에 둔다
FRAMES_DIR = f"{PRIVATE_DIR}/frames"
FRAMESET_FILE = f"{PRIVATE_DIR}/frames.json"

# PNG는 무손실이라 압축 수준이 달라도 픽셀은 같다. 수준 6(기본) 73ms → 수준 1 36ms/프레임,
# 크기는 약 11% 커진다 (실측, 540x1170). 분석용 중간 파일이라 속도를 택했다.
PNG_COMPRESS_LEVEL = 1


class FrameSampler(Protocol):
    """디코딩된 프레임을 시각순으로 받아 남길지 정한다."""

    name: str

    def keep(self, time_sec: float) -> bool: ...


class MinIntervalSampler:
    """직전에 남긴 프레임과 최소 간격 이상 떨어진 프레임만 남긴다.

    가변 프레임률 녹화는 화면이 움직일 때 60fps로 몰려 기록된다.
    몰린 구간만 솎아내고, 실제로 기록된 프레임과 그 시각은 그대로 쓴다.
    (일정 간격으로 다시 샘플링하면 멈춘 구간에 같은 그림을 복제하게 된다.)
    """

    name = "min-interval"

    def __init__(self, min_interval_ms: float) -> None:
        if min_interval_ms < 0:
            raise ValueError(f"최소 간격은 0 이상이어야 한다: {min_interval_ms}")
        self.min_interval_sec = min_interval_ms / 1000
        self._last_kept: float | None = None

    def keep(self, time_sec: float) -> bool:
        # 부동소수점 오차로 경계값이 빠지지 않도록 아주 작은 여유를 둔다
        if self._last_kept is None or time_sec - self._last_kept >= self.min_interval_sec - 1e-9:
            self._last_kept = time_sec
            return True
        return False


SAMPLERS: dict[str, type] = {MinIntervalSampler.name: MinIntervalSampler}


@dataclass(frozen=True)
class ExtractConfig:
    sampler: str = MinIntervalSampler.name
    min_interval_ms: float = 33.0  # 최대 약 30fps. S2 실험으로 다시 정한다.
    analysis_width: int = 540  # 원본보다 크면 원본 크기 그대로 둔다
    image_format: str = "png"  # 무손실. JPEG은 압축 잡음이 화면 비교(S1)를 흐린다.


def make_sampler(config: ExtractConfig) -> FrameSampler:
    try:
        sampler_cls = SAMPLERS[config.sampler]
    except KeyError:
        available = list(SAMPLERS)
        raise ValueError(f"알 수 없는 샘플링 규칙: {config.sampler} (가능: {available})") from None
    return sampler_cls(min_interval_ms=config.min_interval_ms)


def _analysis_size(width: int, height: int, target_width: int) -> tuple[int, int]:
    if width <= target_width:
        return width, height
    return target_width, round(height * target_width / width)


def extract_frames(
    video_path: Path, job_dir: Path, config: ExtractConfig | None = None
) -> FrameSet:
    """영상에서 프레임을 추출해 job_dir에 저장하고 FrameSet을 돌려준다."""
    config = config or ExtractConfig()
    video_path, job_dir = Path(video_path), Path(job_dir)
    sampler = make_sampler(config)
    info = probe_video(video_path).info

    frames_dir = job_dir / FRAMES_DIR
    frames_dir.mkdir(parents=True, exist_ok=True)

    frames: list[Frame] = []
    for decoded in iter_decoded_frames(video_path):
        if not sampler.keep(decoded.time_sec):
            continue
        width, height = _analysis_size(
            decoded.image.width, decoded.image.height, config.analysis_width
        )
        # yuv420p → rgb24 변환과 축소를 FFmpeg(swscale)가 한 번에 처리한다
        rgb = decoded.image.reformat(width=width, height=height, format="rgb24")
        relative = f"{FRAMES_DIR}/{len(frames):06d}.{config.image_format}"
        is_png = config.image_format == "png"
        save_options = {"compress_level": PNG_COMPRESS_LEVEL} if is_png else {}
        rgb.to_image().save(job_dir / relative, **save_options)
        frames.append(
            Frame(
                index=len(frames),
                source_index=decoded.source_index,
                time_sec=round(decoded.time_sec, 6),
                image_path=relative,
                width=width,
                height=height,
            )
        )

    frameset = FrameSet(
        source=info,
        implementation=f"pyav-{sampler.name}",
        params=asdict(config),
        frames=frames,
    )
    frameset.save(job_dir / FRAMESET_FILE)
    return frameset
