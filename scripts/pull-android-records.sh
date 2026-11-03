#!/usr/bin/env bash
# 안드로이드 시험 앱(ShowOnce Probe)의 기록 파일을 PC의 data/android-records/<가져온 시각>/ 으로 가져온다.
#
# - 기록은 앱 내부 저장소(files/records/)에 있어서 다른 앱이나 USB 파일 탐색기로는 읽을 수 없다.
#   디버그 빌드는 `adb shell run-as`로 앱 자신의 권한으로 읽을 수 있어서 그 방법을 쓴다.
# - 가져온 파일은 레포에 올리지 않는다. data/는 .gitignore에 들어 있다 (CLAUDE.md 3번 7항, 7번).
# - 은행 앱 확인처럼 USB 디버깅을 끄고 기록한 경우, 끝난 뒤 다시 켜고 이 스크립트를 실행한다.
#
# 사용법 (Git Bash, 레포 최상위에서): bash scripts/pull-android-records.sh
set -euo pipefail

APP_PACKAGE="com.ongdik.showonce.probe"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESTINATION="$REPO_ROOT/data/android-records/$(date +%Y%m%d-%H%M%S)"

# Windows의 adb가 줄 끝에 붙이는 \r을 지운다
record_file_names=$(adb shell run-as "$APP_PACKAGE" ls files/records 2>/dev/null | tr -d '\r' || true)
if [ -z "$record_file_names" ]; then
    echo "가져올 기록 파일이 없습니다. (폰 연결, USB 디버깅, 앱이 디버그 빌드인지 확인)"
    exit 1
fi

mkdir -p "$DESTINATION"
for record_file_name in $record_file_names; do
    # exec-out: 줄바꿈을 바꾸지 않고 그대로 받는다
    adb exec-out run-as "$APP_PACKAGE" cat "files/records/$record_file_name" > "$DESTINATION/$record_file_name"
done

echo "가져온 위치: $DESTINATION"
for pulled_file in "$DESTINATION"/*.jsonl; do
    echo "  $(basename "$pulled_file"): $(wc -l < "$pulled_file")줄, $(wc -c < "$pulled_file")바이트"
done
