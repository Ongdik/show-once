import pytest

from engine.contracts import Frame, FrameSet, VideoInfo


def _frame(index: int, time_sec: float) -> Frame:
    return Frame(
        index=index,
        source_index=index * 3,
        time_sec=time_sec,
        image_path=f"frames/{index:06d}.png",
        width=540,
        height=1170,
    )


def _frameset(frames: list[Frame]) -> FrameSet:
    return FrameSet(
        source=VideoInfo(source_name="test.mp4", width=1080, height=2340, duration_sec=12.5),
        implementation="pyav-min-interval",
        params={"min_interval_ms": 100},
        frames=frames,
    )


def test_JSON으로_저장하고_다시_읽으면_같은_값이다(tmp_path):
    original = _frameset([_frame(0, 0.0), _frame(1, 0.35), _frame(2, 2.1)])
    path = tmp_path / "frames.json"

    original.save(path)
    loaded = FrameSet.load(path)

    assert loaded == original


def test_저장된_JSON에_구현_이름과_설정값이_남는다(tmp_path):
    path = tmp_path / "frames.json"
    _frameset([_frame(0, 0.0)]).save(path)

    text = path.read_text(encoding="utf-8")

    assert '"implementation": "pyav-min-interval"' in text
    assert '"min_interval_ms": 100' in text


def test_프레임이_시각_순서가_아니면_거부한다():
    with pytest.raises(ValueError, match="시각 순서"):
        _frameset([_frame(0, 1.0), _frame(1, 0.5)])


def test_프레임_index가_비어있으면_거부한다():
    with pytest.raises(ValueError, match="index"):
        _frameset([_frame(0, 0.0), _frame(2, 0.5)])


def test_음수_시각은_거부한다():
    with pytest.raises(ValueError, match="0 이상"):
        _frame(0, -0.1)


def test_절대_경로는_거부한다(tmp_path):
    with pytest.raises(ValueError, match="상대 경로"):
        Frame(
            index=0,
            source_index=0,
            time_sec=0.0,
            image_path=str(tmp_path / "a.png"),
            width=1,
            height=1,
        )


def test_다른_형식_버전은_읽지_않는다():
    data = _frameset([_frame(0, 0.0)]).to_dict()
    data["schema_version"] = 999

    with pytest.raises(ValueError, match="형식 버전"):
        FrameSet.from_dict(data)


def test_작업_폴더_밖을_가리키는_경로는_거부한다():
    with pytest.raises(ValueError, match="밖을 가리킬 수 없다"):
        Frame(
            index=0,
            source_index=0,
            time_sec=0.0,
            image_path="private/../../outside.png",
            width=1,
            height=1,
        )


def test_화면_전환_결과를_저장하고_다시_읽으면_같은_값이다(tmp_path):
    from engine.contracts import FrameChange, TransitionCandidate, TransitionSet

    original = TransitionSet(
        implementation="pixel-diff",
        params={"motion_ratio": 0.02},
        frame_changes=[FrameChange(0, 0.0, 0.0), FrameChange(1, 0.5, 0.3)],
        candidates=[
            TransitionCandidate(0, 1, 0.5, 0.5, 0.3, 0.3, True),
        ],
    )
    path = tmp_path / "transitions.json"

    original.save(path)

    assert TransitionSet.load(path) == original
    assert len(TransitionSet.load(path).transitions) == 1


def test_전환_구간의_앞뒤가_뒤바뀌면_거부한다():
    from engine.contracts import TransitionCandidate

    with pytest.raises(ValueError, match="before_index"):
        TransitionCandidate(3, 3, 0.5, 0.5, 0.3, 0.3, True)
