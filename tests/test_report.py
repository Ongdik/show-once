from engine.report import REPORT_FILE, build_report, interval_chart_svg
from engine.stages.s0_extract import ExtractConfig, extract_frames

TIMES = [0.0, 0.040, 0.850, 1.700]


def test_리포트는_비공개_폴더에_만든다(make_video, tmp_path):
    job = tmp_path / "job"
    extract_frames(make_video(TIMES), job)

    report = build_report(job)

    assert report == job / REPORT_FILE
    assert report.parent.name == "private"


def test_리포트의_이미지는_실제_프레임을_상대_경로로_가리킨다(make_video, tmp_path):
    job = tmp_path / "job"
    frameset = extract_frames(make_video(TIMES), job)

    html = build_report(job).read_text(encoding="utf-8")

    for frame in frameset.frames:
        relative = frame.image_path.removeprefix("private/")  # 리포트가 private/ 안에 있다
        assert f'src="{relative}"' in html
        assert (job / "private" / relative).is_file()


def test_리포트에_구현과_설정값과_프레임_수가_나온다(make_video, tmp_path):
    job = tmp_path / "job"
    extract_frames(make_video(TIMES), job, ExtractConfig(min_interval_ms=33))

    html = build_report(job).read_text(encoding="utf-8")

    assert "pyav-min-interval" in html
    assert "min_interval_ms=33" in html
    assert "4장" in html


def test_간격_그래프는_프레임마다_막대를_그린다():
    svg = interval_chart_svg([0.0, 0.040, 0.850, 1.700], min_interval_ms=33)

    assert svg.count('class="bar"') == 3  # 간격은 프레임 수 - 1
    assert "최소 간격 33ms" in svg


def test_값에_HTML_특수문자가_있어도_그대로_보이게_처리한다(make_video, tmp_path):
    # Windows는 폴더 이름에 < > 를 못 쓰므로 & 로 확인한다
    job = tmp_path / "rec&01"
    extract_frames(make_video(TIMES), job)

    html = build_report(job).read_text(encoding="utf-8")

    assert "rec&amp;01" in html
    assert "rec&01" not in html
