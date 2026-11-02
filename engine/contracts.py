"""파이프라인 단계 사이에서 주고받는 데이터 형식.

설계 04 P4: 각 단계는 여기 정의된 입출력 형식만 지키면 되고,
구현(알고리즘)은 설정으로 갈아끼울 수 있어야 한다.
설계 04 P3: 모든 중간 결과는 JSON으로 저장해 나중에 열어보고 비교할 수 있게 한다.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1


@dataclass(frozen=True)
class VideoInfo:
    """입력 영상의 기본 정보."""

    source_name: str  # 원본 파일 이름 (경로는 PC마다 달라서 이름만 남긴다)
    width: int
    height: int
    duration_sec: float


@dataclass(frozen=True)
class Frame:
    """추출된 프레임 하나.

    time_sec는 프레임 번호로 계산한 값이 아니라 영상에 기록된 표시 시각
    (PTS x time_base)이다. 가변 프레임률 영상에서도 맞는 시각을 쓰기 위해서다.
    기준점은 첫 프레임이다: 첫 프레임의 시각을 빼서 첫 프레임을 0초로 맞춘다.
    """

    index: int  # 추출된 프레임 중 몇 번째인지 (0부터)
    source_index: int  # 원본 영상에서 디코딩된 순서 (0부터)
    time_sec: float  # 첫 프레임 기준 시각(초)
    image_path: str  # 작업 폴더 기준 상대 경로 (예: "frames/000012.png")
    width: int
    height: int

    def __post_init__(self) -> None:
        if self.time_sec < 0:
            raise ValueError(f"프레임 시각은 0 이상이어야 한다: {self.time_sec}")
        if Path(self.image_path).is_absolute():
            raise ValueError(f"이미지 경로는 작업 폴더 기준 상대 경로여야 한다: {self.image_path}")


@dataclass(frozen=True)
class FrameSet:
    """S0(프레임 추출)의 결과. S1, S2의 입력이 된다.

    implementation과 params에 어떤 방법과 설정으로 만들었는지 남긴다 (설계 03 D2).
    """

    source: VideoInfo
    implementation: str  # 예: "pyav-min-interval"
    params: dict[str, Any] = field(default_factory=dict)
    frames: list[Frame] = field(default_factory=list)
    stage: str = "S0"
    schema_version: int = SCHEMA_VERSION

    def __post_init__(self) -> None:
        times = [f.time_sec for f in self.frames]
        if times != sorted(times):
            raise ValueError("프레임은 시각 순서대로 정렬되어 있어야 한다")
        indexes = [f.index for f in self.frames]
        if indexes != list(range(len(self.frames))):
            raise ValueError("프레임 index는 0부터 빠짐없이 이어져야 한다")

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> FrameSet:
        version = data.get("schema_version")
        if version != SCHEMA_VERSION:
            raise ValueError(f"지원하지 않는 형식 버전: {version} (현재 {SCHEMA_VERSION})")
        return cls(
            source=VideoInfo(**data["source"]),
            implementation=data["implementation"],
            params=data.get("params", {}),
            frames=[Frame(**f) for f in data.get("frames", [])],
            stage=data.get("stage", "S0"),
            schema_version=version,
        )

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        text = json.dumps(self.to_dict(), ensure_ascii=False, indent=2)
        path.write_text(text, encoding="utf-8")

    @classmethod
    def load(cls, path: Path) -> FrameSet:
        return cls.from_dict(json.loads(path.read_text(encoding="utf-8")))
