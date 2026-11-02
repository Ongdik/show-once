"""테스트용 영상을 직접 만드는 도구.

실제 녹화는 커밋할 수 없으므로(개인정보, 용량), 프레임 시각을 우리가 정한 영상을 만든다.
시각을 알고 있으니 추출 결과가 정확한지 검증할 수 있다.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from fractions import Fraction
from pathlib import Path

import av
import numpy as np
import pytest

MakeVideo = Callable[[Sequence[float]], Path]


def write_video(path: Path, times_sec: Sequence[float], width: int = 64, height: int = 128) -> Path:
    """times_sec에 적힌 시각마다 프레임 하나씩 넣은 mp4를 만든다.

    프레임마다 밝기를 다르게 해서 서로 구분되게 한다.
    """
    time_base = Fraction(1, 1000)  # 1ms 단위
    with av.open(str(path), mode="w") as container:
        stream = container.add_stream("mpeg4", rate=1000)
        stream.width = width
        stream.height = height
        stream.pix_fmt = "yuv420p"
        stream.time_base = time_base
        stream.codec_context.time_base = time_base
        for i, t in enumerate(times_sec):
            level = (i * 37) % 256
            image = np.full((height, width, 3), level, dtype=np.uint8)
            frame = av.VideoFrame.from_ndarray(image, format="rgb24")
            frame.pts = round(t * 1000)
            frame.time_base = time_base
            for packet in stream.encode(frame):
                container.mux(packet)
        for packet in stream.encode():
            container.mux(packet)
    return path


@pytest.fixture
def make_video(tmp_path: Path) -> MakeVideo:
    counter = iter(range(1000))

    def _make(times_sec: Sequence[float]) -> Path:
        return write_video(tmp_path / f"video_{next(counter)}.mp4", times_sec)

    return _make
