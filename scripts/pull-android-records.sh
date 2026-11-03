#!/usr/bin/env bash
# 안드로이드 시험 앱(ShowOnce Probe)의 기록 파일을 PC의 data/android-records/<가져온 시각>/ 으로 가져온다.
#
# - 기록은 앱 내부 저장소(files/records/)에 있어서 다른 앱이나 USB 파일 탐색기로는 읽을 수 없다.
#   디버그 빌드는 `adb shell run-as`로 앱 자신의 권한으로 읽을 수 있어서 그 방법을 쓴다.
# - 가져온 파일은 레포에 올리지 않는다. data/는 .gitignore에 들어 있다 (CLAUDE.md 3번 7항, 7번).
# - 은행 앱 확인처럼 USB 디버깅을 끄고 기록한 경우, 끝난 뒤 다시 켜고 이 스크립트를 실행한다.
# - 가져오는 동안 서비스가 쓰고 있으면 마지막 줄이 잘려 있을 수 있다. 읽을 때 깨진 마지막 줄은 건너뛴다.
#
# 사용법 (Git Bash, 레포 최상위에서): bash scripts/pull-android-records.sh
set -euo pipefail
shopt -s nullglob

APP_PACKAGE="com.ongdik.showonce.probe"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DESTINATION="$REPO_ROOT/data/android-records/$(date +%Y%m%d-%H%M%S)"

# 실패하면 어느 단계에서 왜 실패했는지 보여 준다.
# 1) Git Bash가 adb를 찾는가 (Android Studio와 함께 깔리지만 Git Bash PATH에는 없을 수 있다)
if ! command -v adb >/dev/null 2>&1; then
    echo "adb를 찾지 못했습니다. ~/.bashrc에 아래 줄을 추가하고 'source ~/.bashrc' 후 다시 실행하세요:"
    echo '  export PATH="$HOME/AppData/Local/Android/Sdk/platform-tools:$PATH"'
    exit 1
fi

# 2) 연결된 기기가 있는가
connected_devices=$(adb devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
if [ -z "$connected_devices" ]; then
    echo "연결된 기기가 없습니다. adb devices 결과:"
    adb devices
    exit 1
fi

# 3) 앱 권한(run-as)으로 읽을 수 있는가. adb exec-out은 폰 쪽 실패를 종료 코드로 알려 주지 않아서 먼저 확인한다.
if ! run_as_output=$(adb shell run-as "$APP_PACKAGE" ls files 2>&1); then
    echo "앱 권한으로 읽을 수 없습니다 (앱이 디버그 빌드로 설치됐는지 확인). adb가 돌려준 내용:"
    echo "  $run_as_output"
    exit 1
fi

# Windows의 adb가 줄 끝에 붙이는 \r을 지운다
listed_names=$(adb shell run-as "$APP_PACKAGE" ls files/records 2>/dev/null | tr -d '\r' || true)

mkdir -p "$DESTINATION"
pulled_count=0
for record_file_name in $listed_names; do
    # 예상한 이름(records-....jsonl)만 받는다. 경로 문자가 섞인 이름은 버린다.
    case "$record_file_name" in
        */* | *..*) continue ;;
        records-*.jsonl) ;;
        *) continue ;;
    esac
    destination_file="$DESTINATION/$record_file_name"
    # exec-out: 줄바꿈을 바꾸지 않고 그대로 받는다
    adb exec-out run-as "$APP_PACKAGE" cat "files/records/$record_file_name" > "$destination_file"
    # 첫 줄이 세션 머리말이 아니면 오류 문장이 들어온 것이다
    if ! head -n 1 "$destination_file" | grep -q '^{"type":"session"'; then
        echo "  경고: $record_file_name 첫 줄이 머리말이 아닙니다. 오류 문장일 수 있어 지웁니다."
        rm -f "$destination_file"
        continue
    fi
    pulled_count=$((pulled_count + 1))
done

if [ "$pulled_count" -eq 0 ]; then
    rmdir "$DESTINATION" 2>/dev/null || true
    echo "가져올 기록 파일이 없습니다."
    exit 1
fi

echo "가져온 위치: $DESTINATION"
for pulled_file in "$DESTINATION"/*.jsonl; do
    echo "  $(basename "$pulled_file"): $(wc -l < "$pulled_file")줄, $(wc -c < "$pulled_file")바이트"
done
