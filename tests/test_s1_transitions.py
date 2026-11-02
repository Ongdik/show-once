import numpy as np
import pytest

from engine import cli
from engine.contracts import TransitionSet
from engine.jobs import TRANSITIONS_FILE
from engine.report import build_report
from engine.stages.s0_extract import ExtractConfig, extract_frames
from engine.stages.s1_transitions import (
    TransitionConfig,
    change_ratio,
    detect_transitions,
    find_motion_runs,
)

W, H = 128, 256


def screen_a() -> np.ndarray:
    """어두운 배경 + 왼쪽 위 흰 상자 (화면 A)"""
    image = np.full((H, W, 3), 40, dtype=np.uint8)
    image[40:120, 10:70] = 230
    return image


def screen_b() -> np.ndarray:
    """밝은 배경 + 오른쪽 아래 어두운 상자 (화면 B)"""
    image = np.full((H, W, 3), 200, dtype=np.uint8)
    image[140:230, 50:120] = 20
    return image


def blend(a: np.ndarray, b: np.ndarray, k: float) -> np.ndarray:
    """화면 전환 애니메이션 중간 프레임"""
    return (a * (1 - k) + b * k).astype(np.uint8)


def with_dot(image: np.ndarray, x: int, y: int) -> np.ndarray:
    """터치 표시처럼 작은 점을 찍는다 (화면의 약 0.5%)"""
    out = image.copy()
    out[y : y + 12, x : x + 12] = 255
    return out


def run(job, make_scene_video, scenes, config=None):
    extract_frames(make_scene_video(scenes), job, ExtractConfig(min_interval_ms=0))
    return detect_transitions(job, config)


def test_여러_프레임에_걸친_애니메이션을_전환_하나로_찾는다(make_scene_video, tmp_path):
    a, b = screen_a(), screen_b()
    scenes = [
        (0.0, a),
        (0.5, a),
        (0.6, blend(a, b, 0.33)),
        (0.7, blend(a, b, 0.66)),
        (0.8, b),
        (1.5, b),
    ]

    result = run(tmp_path / "job", make_scene_video, scenes)

    assert len(result.transitions) == 1
    t = result.transitions[0]
    assert (t.before_index, t.after_index) == (1, 4)  # 0.5초의 A → 0.8초의 B
    assert (t.start_sec, t.end_sec) == pytest.approx((0.6, 0.8), abs=0.001)


def test_터치_점이_움직이는_것은_전환이_아니다(make_scene_video, tmp_path):
    a = screen_a()
    scenes = [(i * 0.1, with_dot(a, 80, 150 + i * 10)) for i in range(6)]

    result = run(tmp_path / "job", make_scene_video, scenes)

    assert result.candidates == []
    assert max(c.change_ratio for c in result.frame_changes) < 0.02


def test_움직였다가_같은_화면으로_돌아오면_전환이_아니다(make_scene_video, tmp_path):
    """잠깐 뜬 팝업이 사라진 경우: 움직임 구간은 있지만 전후가 같다."""
    a, b = screen_a(), screen_b()
    scenes = [(0.0, a), (0.5, a), (0.6, b), (0.7, a), (1.5, a)]

    result = run(tmp_path / "job", make_scene_video, scenes)

    assert len(result.candidates) == 1
    assert not result.candidates[0].is_transition
    assert result.candidates[0].total_change_ratio < 0.01


def test_상태_표시줄만_바뀌면_무시한다(make_scene_video, tmp_path):
    a = screen_a()
    changed_top = a.copy()
    changed_top[0:40] = 250  # 위쪽 영역만 바뀜 (시계·아이콘이 바뀐 것처럼)
    scenes = [(0.0, a), (0.5, changed_top), (1.0, changed_top)]

    ignored = run(
        tmp_path / "job1", make_scene_video, scenes, TransitionConfig(ignore_top_ratio=0.25)
    )
    counted = run(tmp_path / "job2", make_scene_video, scenes, TransitionConfig(ignore_top_ratio=0))

    assert ignored.candidates == []
    assert len(counted.candidates) == 1


def test_결과에_구현과_설정값과_프레임별_점수를_남긴다(make_scene_video, tmp_path):
    job = tmp_path / "job"
    scenes = [(0.0, screen_a()), (0.5, screen_b())]
    run(job, make_scene_video, scenes, TransitionConfig(motion_ratio=0.05))

    saved = TransitionSet.load(job / TRANSITIONS_FILE)

    assert saved.implementation == "pixel-diff"
    assert saved.params["motion_ratio"] == 0.05
    assert [c.index for c in saved.frame_changes] == [0, 1]
    assert saved.frame_changes[0].change_ratio == 0.0


def test_알_수_없는_검출_방법은_거부한다(make_scene_video, tmp_path):
    with pytest.raises(ValueError, match="검출 방법"):
        run(tmp_path / "job", make_scene_video, [(0.0, screen_a())], TransitionConfig(detector="x"))


def test_픽셀_차이는_음수로_넘쳐도_틀리지_않는다():
    """uint8끼리 빼면 10 - 20 이 246이 된다. int16으로 바꿔 계산해야 한다."""
    a = np.full((4, 4), 10, dtype=np.int16)
    b = np.full((4, 4), 20, dtype=np.int16)

    assert change_ratio(a, b, pixel_threshold=25) == 0.0


def test_크기가_다른_이미지는_비교하지_않는다():
    with pytest.raises(ValueError, match="크기"):
        change_ratio(np.zeros((4, 4)), np.zeros((4, 5)), pixel_threshold=25)


def test_움직임_구간_묶기():
    ratios = [0.0, 0.0, 0.3, 0.2, 0.0, 0.5, 0.0, 0.1]

    assert find_motion_runs(ratios, motion_ratio=0.05) == [(2, 3), (5, 5), (7, 7)]


def test_명령어와_리포트(make_scene_video, tmp_path, capsys):
    job = tmp_path / "job"
    extract_frames(make_scene_video([(0.0, screen_a()), (0.5, screen_b())]), job)

    assert cli.main(["transitions", str(job)]) == 0
    assert "화면 전환 1개" in capsys.readouterr().out
    html = build_report(job).read_text(encoding="utf-8")
    assert "S1 화면 전환 후보" in html
    assert "직전 #0" in html and "직후 #1" in html
