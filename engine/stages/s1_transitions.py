"""S1 화면 전환 후보 검출.

1. 변화 점수: 이웃 프레임을 흑백으로 바꿔 픽셀 차이를 구하고, 일정 이상 바뀐 픽셀의 비율을 쓴다.
2. 움직임 구간: 변화 점수가 기준 이상인 연속 프레임을 하나의 구간으로 묶는다.
   화면 전환 애니메이션은 변화를 여러 프레임에 나눠 담기 때문이다.
3. 전환 판정: 구간 직전 프레임과 직후(안정된) 프레임을 직접 비교해 충분히 바뀌었으면 전환 후보.

화면 전환은 부모님께 보여주지 않는 내부 신호다 (설계 02). S3가 터치(S2)와 합쳐 행동 단계를 만든다.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
from PIL import Image

from engine.contracts import FrameChange, FrameSet, TransitionCandidate, TransitionSet
from engine.jobs import FRAMESET_FILE, TRANSITIONS_FILE


@dataclass(frozen=True)
class TransitionConfig:
    detector: str = "pixel-diff"
    # 픽셀 하나가 "바뀌었다"고 볼 밝기 차이 (0~255). 압축 잡음보다 크고 실제 변화보다 작게.
    pixel_threshold: int = 25
    # 이웃 프레임 사이 바뀐 픽셀 비율이 이 값 이상이면 움직임으로 본다.
    # rec01 탐색: 터치 표시 점·시계 같은 작은 변화는 1% 미만이었다.
    motion_ratio: float = 0.02
    # 움직임 구간 직전·직후 프레임을 비교해 이 비율 이상 바뀌었으면 화면 전환 후보.
    transition_ratio: float = 0.10
    # 화면 맨 위(상태 표시줄)는 시계·아이콘이 바뀌므로 비교에서 뺀다. 화면 높이 대비 비율.
    ignore_top_ratio: float = 0.03


def to_gray(image: Image.Image, ignore_top_ratio: float) -> np.ndarray:
    """흑백으로 바꾸고 상태 표시줄 영역을 잘라낸다.

    Pillow의 "L" 변환은 밝기 = 0.299R + 0.587G + 0.114B (ITU-R 601).
    사람 눈이 초록에 가장 민감한 것을 반영한 가중치다.
    int16으로 바꾸는 이유: uint8끼리 빼면 음수가 255 근처로 돌아가(오버플로) 차이가 틀린다.
    """
    gray = np.asarray(image.convert("L"), dtype=np.int16)
    cut = round(gray.shape[0] * ignore_top_ratio)
    return gray[cut:]


def change_ratio(a: np.ndarray, b: np.ndarray, pixel_threshold: int) -> float:
    """두 흑백 이미지에서 밝기 차이가 기준을 넘는 픽셀의 비율.

    평균 차이 대신 비율을 쓰는 이유: 평균은 "전체가 조금 바뀜"과 "일부가 크게 바뀜"을
    구분하지 못한다. 비율은 "화면의 몇 %가 바뀌었나"로 바로 읽힌다.
    """
    if a.shape != b.shape:
        raise ValueError(f"이미지 크기가 다르다: {a.shape} vs {b.shape}")
    return float((np.abs(a - b) > pixel_threshold).mean())


def find_motion_runs(ratios: list[float], motion_ratio: float) -> list[tuple[int, int]]:
    """변화 점수가 기준 이상인 연속 구간을 (첫 index, 마지막 index)로 돌려준다.

    ratios[i]는 프레임 i-1과 i를 비교한 값이다 (ratios[0]은 비교 대상이 없어 0).
    """
    runs: list[tuple[int, int]] = []
    start: int | None = None
    for i, ratio in enumerate(ratios):
        if i > 0 and ratio >= motion_ratio:
            if start is None:
                start = i
        elif start is not None:
            runs.append((start, i - 1))
            start = None
    if start is not None:
        runs.append((start, len(ratios) - 1))
    return runs


def detect_transitions(job_dir: Path, config: TransitionConfig | None = None) -> TransitionSet:
    """작업 폴더의 S0 결과(frames.json)로 화면 전환 후보를 찾아 저장한다."""
    config = config or TransitionConfig()
    if config.detector != "pixel-diff":
        raise ValueError(f"알 수 없는 검출 방법: {config.detector} (가능: ['pixel-diff'])")
    job_dir = Path(job_dir)
    frameset = FrameSet.load(job_dir / FRAMESET_FILE)
    frames = frameset.frames

    grays = []
    for frame in frames:
        with Image.open(job_dir / frame.image_path) as image:
            grays.append(to_gray(image, config.ignore_top_ratio))

    ratios = [0.0] + [
        change_ratio(grays[i - 1], grays[i], config.pixel_threshold) for i in range(1, len(grays))
    ]
    changes = [
        FrameChange(index=f.index, time_sec=f.time_sec, change_ratio=round(r, 6))
        for f, r in zip(frames, ratios, strict=True)
    ]

    candidates = []
    for first, last in find_motion_runs(ratios, config.motion_ratio):
        before, after = first - 1, last
        total = change_ratio(grays[before], grays[after], config.pixel_threshold)
        candidates.append(
            TransitionCandidate(
                before_index=before,
                after_index=after,
                start_sec=frames[first].time_sec,
                end_sec=frames[after].time_sec,
                peak_change_ratio=round(max(ratios[first : last + 1]), 6),
                total_change_ratio=round(total, 6),
                is_transition=total >= config.transition_ratio,
            )
        )

    # 어떤 S0 결과로 만들었는지 남긴다. S0를 다른 설정으로 다시 돌리면 프레임 번호가 바뀌어
    # 이 결과와 맞지 않게 되므로, 읽는 쪽이 어긋남을 알아챌 수 있어야 한다.
    params = asdict(config)
    params["input"] = {
        "implementation": frameset.implementation,
        "params": frameset.params,
        "frame_count": len(frames),
    }
    result = TransitionSet(
        implementation=config.detector,
        params=params,
        frame_changes=changes,
        candidates=candidates,
    )
    result.save(job_dir / TRANSITIONS_FILE)
    return result
