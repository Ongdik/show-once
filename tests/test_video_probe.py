import pytest

from engine.adapters.video import probe_video

# 실제 안드로이드 녹화처럼: 화면이 바뀔 때는 짧은 간격으로 몰리고, 멈추면 길게 비는 시각
VFR_TIMES = [0.0, 0.017, 0.033, 0.050, 0.850, 0.867, 0.883, 1.700, 1.717]
CFR_TIMES = [i / 30 for i in range(30)]  # 1초 동안 30fps 고정


def test_프레임_시각을_영상에_기록된_값_그대로_읽는다(make_video):
    probe = probe_video(make_video(VFR_TIMES))

    assert probe.frame_count == len(VFR_TIMES)
    assert probe.frame_times_sec == pytest.approx(VFR_TIMES, abs=0.001)


def test_간격이_들쭉날쭉하면_가변_프레임률로_판별한다(make_video):
    probe = probe_video(make_video(VFR_TIMES))

    assert probe.is_variable_frame_rate
    assert probe.interval_max_ms == pytest.approx(817, abs=1)  # 0.883초 → 1.700초
    assert probe.interval_median_ms == pytest.approx(17, abs=1)


def test_간격이_일정하면_고정_프레임률로_판별한다(make_video):
    probe = probe_video(make_video(CFR_TIMES))

    assert not probe.is_variable_frame_rate
    assert probe.average_fps == pytest.approx(30, abs=0.5)


def test_영상_크기와_길이를_읽는다(make_video):
    probe = probe_video(make_video(VFR_TIMES))

    assert (probe.info.width, probe.info.height) == (64, 128)
    assert probe.info.duration_sec == pytest.approx(1.717, abs=0.001)


def test_번호로_계산한_시각은_가변_프레임률에서_크게_틀린다(make_video):
    """PTS 기반 시각을 쓰는 이유를 보여주는 테스트."""
    probe = probe_video(make_video(VFR_TIMES))
    fps = probe.average_fps

    errors = [abs(i / fps - t) for i, t in enumerate(probe.frame_times_sec)]

    assert max(errors) > 0.3


def test_디코딩한_시각이_영상_정보_확인의_시각과_같다(make_video):
    """S0 추출(디코딩)과 영상 정보 확인(디먹싱)이 같은 시각 기준을 쓰는지 확인한다."""
    from engine.adapters.video import iter_decoded_frames

    video = make_video(VFR_TIMES)
    decoded = [f.time_sec for f in iter_decoded_frames(video)]

    assert decoded == pytest.approx(probe_video(video).frame_times_sec, abs=1e-6)
