"""처리 작업 폴더의 구조.

    <작업 폴더>/
    ├── private/   원본에서 나온 것 (가림 전). 비공개. S0~S4가 쓴다.
    │   ├── frames/
    │   ├── frames.json
    │   └── report.html   확인용 리포트 (원본 프레임을 보여주므로 비공개)
    └── public/    가림 처리된 것만. S5(가림)만 쓴다. (아직 없음)

원본과 공개용을 폴더부터 나눠, 실수로라도 섞이지 않게 한다 (설계 03 D1, CLAUDE.md 7번).
경로는 이 파일에서만 정한다.
"""

PRIVATE_DIR = "private"
PUBLIC_DIR = "public"

# S0 프레임 추출 결과
FRAMES_DIR = f"{PRIVATE_DIR}/frames"
FRAMESET_FILE = f"{PRIVATE_DIR}/frames.json"

# 확인용 리포트
REPORT_FILE = f"{PRIVATE_DIR}/report.html"
