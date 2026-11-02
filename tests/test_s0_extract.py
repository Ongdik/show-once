import pytest
from PIL import Image

from engine.contracts import FrameSet
from engine.stages.s0_extract import (
    FRAMESET_FILE,
    ExtractConfig,
    MinIntervalSampler,
    extract_frames,
)

# 화면이 움직일 때 10ms 간격으로 몰리고, 멈추면 길게 비는 시각 (실제 녹화를 흉내)
TIMES = [0.0, 0.010, 0.020, 0.040, 0.050, 0.850, 0.860, 1.700]


def test_최소_간격보다_가까운_프레임은_건너뛴다(make_video, tmp_path):
    frameset = extract_frames(
        make_video(TIMES), tmp_path / "job", ExtractConfig(min_interval_ms=33)
    )

    times = [f.time_sec for f in frameset.frames]
    # 0.000 남김 → 0.010, 0.020 건너뜀 → 0.040 남김 → 0.050 건너뜀 → 0.850 → 0.860 건너뜀 → 1.700
    assert times == pytest.approx([0.0, 0.040, 0.850, 1.700], abs=0.001)


def test_남긴_프레임의_원본_순번을_기록한다(make_video, tmp_path):
    frameset = extract_frames(
        make_video(TIMES), tmp_path / "job", ExtractConfig(min_interval_ms=33)
    )

    assert [f.source_index for f in frameset.frames] == [0, 3, 5, 7]


def test_최소_간격이_0이면_모든_프레임을_남긴다(make_video, tmp_path):
    frameset = extract_frames(make_video(TIMES), tmp_path / "job", ExtractConfig(min_interval_ms=0))

    assert [f.time_sec for f in frameset.frames] == pytest.approx(TIMES, abs=0.001)


def test_분석용_크기로_줄이고_비율을_유지한다(make_video, tmp_path):
    job = tmp_path / "job"
    frameset = extract_frames(make_video(TIMES), job, ExtractConfig(analysis_width=32))

    first = frameset.frames[0]
    assert (first.width, first.height) == (32, 64)  # 원본 64x128
    with Image.open(job / first.image_path) as image:
        assert image.size == (32, 64)


def test_원본보다_큰_크기는_늘리지_않는다(make_video, tmp_path):
    frameset = extract_frames(
        make_video(TIMES), tmp_path / "job", ExtractConfig(analysis_width=540)
    )

    assert (frameset.frames[0].width, frameset.frames[0].height) == (64, 128)


def test_이미지와_frames_json을_작업_폴더에_저장한다(make_video, tmp_path):
    job = tmp_path / "job"
    frameset = extract_frames(make_video(TIMES), job)

    assert FrameSet.load(job / FRAMESET_FILE) == frameset
    for frame in frameset.frames:
        assert (job / frame.image_path).is_file()


def test_구현_이름과_설정값을_결과에_남긴다(make_video, tmp_path):
    config = ExtractConfig(min_interval_ms=50, analysis_width=32)
    frameset = extract_frames(make_video(TIMES), tmp_path / "job", config)

    assert frameset.implementation == "pyav-min-interval"
    assert frameset.params["min_interval_ms"] == 50
    assert frameset.params["analysis_width"] == 32


def test_알_수_없는_샘플링_규칙은_거부한다(make_video, tmp_path):
    with pytest.raises(ValueError, match="샘플링 규칙"):
        extract_frames(make_video(TIMES), tmp_path / "job", ExtractConfig(sampler="없는규칙"))


def test_첫_프레임은_항상_남기고_경계값은_포함한다():
    sampler = MinIntervalSampler(min_interval_ms=33)

    assert [sampler.keep(t) for t in (0.0, 0.032, 0.033, 0.065)] == [True, False, True, False]


def test_원본_프레임은_비공개_폴더에_저장한다(make_video, tmp_path):
    frameset = extract_frames(make_video(TIMES), tmp_path / "job")

    assert all(f.image_path.startswith("private/") for f in frameset.frames)
    assert not (tmp_path / "job" / "public").exists()


def test_다시_실행하면_이전_이미지가_남지_않는다(make_video, tmp_path):
    job = tmp_path / "job"
    extract_frames(make_video(TIMES), job, ExtractConfig(min_interval_ms=0))
    frameset = extract_frames(make_video(TIMES), job, ExtractConfig(min_interval_ms=500))

    saved = sorted(p.name for p in (job / "private" / "frames").iterdir())
    assert len(saved) == len(frameset.frames)

