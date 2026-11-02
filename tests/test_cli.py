import pytest

from engine import cli
from engine.contracts import FrameSet
from engine.jobs import FRAMESET_FILE

TIMES = [0.0, 0.010, 0.050, 0.850, 1.700]


def test_probe는_영상_정보와_판별_결과를_출력한다(make_video, capsys):
    assert cli.main(["probe", str(make_video(TIMES))]) == 0

    out = capsys.readouterr().out
    assert "프레임 5장" in out
    assert "가변 프레임률" in out


def test_extract는_지정한_작업_폴더에_설정값대로_저장한다(make_video, tmp_path):
    job = tmp_path / "job"

    cli.main(["extract", str(make_video(TIMES)), "--job", str(job), "--min-interval-ms", "100"])

    frameset = FrameSet.load(job / FRAMESET_FILE)
    assert frameset.params["min_interval_ms"] == 100
    assert [f.time_sec for f in frameset.frames] == pytest.approx([0.0, 0.850, 1.700], abs=0.001)


def test_extract는_작업_폴더를_안_주면_영상_이름으로_만든다(make_video, tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    video = make_video(TIMES)

    cli.main(["extract", str(video)])

    assert (tmp_path / "data" / "jobs" / video.stem / FRAMESET_FILE).is_file()


def test_없는_영상이면_에러로_끝난다(tmp_path):
    with pytest.raises(SystemExit) as exit_info:
        cli.main(["extract", str(tmp_path / "없음.mp4")])

    assert exit_info.value.code == 2


def test_report는_처리_결과가_없으면_에러로_끝난다(tmp_path):
    with pytest.raises(SystemExit):
        cli.main(["report", str(tmp_path)])
