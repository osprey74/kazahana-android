#!/usr/bin/env bash
# kazahana-android メモリ実測スクリプト
#
# Google Play の新品質要件（2027-02 施行）のしきい値に対して、実機の
# 「Anonymous RSS + Swap」と ビットマップ由来のネイティブヒープを計測する。
#
# 指標の定義:
#   Anonymous RSS + Swap = /proc/<pid>/status の RssAnon + VmSwap
#   （Android vitals の "Memory usage (Anonymous RSS + swap)" と同一定義）
#
# 使い方:
#   ./tools/measure-memory.sh <ラベル>
# 例:
#   ./tools/measure-memory.sh 起動直後
#   ./tools/measure-memory.sh タイムライン200件スクロール後
#   ./tools/measure-memory.sh バックグラウンド遷移60秒後

set -uo pipefail

PKG="${PKG:-com.kazahana.app}"
LABEL="${1:-unlabeled}"
ADB="${ADB:-adb}"

PID=$("$ADB" shell pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}')
if [ -z "$PID" ]; then
    echo "ERROR: $PKG が起動していません（adb shell pidof が空）" >&2
    exit 1
fi

STATUS=$("$ADB" shell cat "/proc/$PID/status" 2>/dev/null | tr -d '\r')
rss_anon=$(echo "$STATUS" | awk '/^RssAnon:/ {print $2}')
rss_file=$(echo "$STATUS" | awk '/^RssFile:/ {print $2}')
rss_shmem=$(echo "$STATUS" | awk '/^RssShmem:/ {print $2}')
vm_swap=$(echo "$STATUS" | awk '/^VmSwap:/ {print $2}')

# 未取得のフィールドは 0 として扱う（一部端末で VmSwap が出ないため）
rss_anon=${rss_anon:-0}; rss_file=${rss_file:-0}
rss_shmem=${rss_shmem:-0}; vm_swap=${vm_swap:-0}

metric=$(( rss_anon + vm_swap ))

# dumpsys の App Summary（ビットマップは API 26+ でネイティブヒープに載る）
SUMMARY=$("$ADB" shell dumpsys meminfo "$PKG" 2>/dev/null | tr -d '\r' \
    | sed -n '/App Summary/,/^$/p')
java_heap=$(echo "$SUMMARY"   | awk '/Java Heap:/    {print $3}')
native_heap=$(echo "$SUMMARY" | awk '/Native Heap:/  {print $3}')
graphics=$(echo "$SUMMARY"    | awk '/Graphics:/     {print $2}')

# プロセス状態（oom_score_adj: 0=前景 / 100前後=知覚可能 / 200+=背景 / 900+=cached）
OOM=$("$ADB" shell cat "/proc/$PID/oom_score_adj" 2>/dev/null | tr -d '\r')
case "${OOM:-0}" in
    0|-*)      state="foreground" ;;
    [1-9]|[1-9][0-9]|1[0-9][0-9]) state="user-perceived" ;;
    9[0-9][0-9]|[1-9][0-9][0-9][0-9]) state="cached" ;;
    *)         state="background" ;;
esac

printf '%s\n' "── $LABEL ──"
printf '  プロセス状態      : %s (oom_score_adj=%s, pid=%s)\n' "$state" "${OOM:-?}" "$PID"
printf '  RssAnon           : %8.1f MB\n' "$(echo "$rss_anon / 1024" | bc -l)"
printf '  VmSwap            : %8.1f MB\n' "$(echo "$vm_swap / 1024" | bc -l)"
printf '  ▶ Anon RSS + Swap : %8.1f MB   ← Play の判定指標\n' "$(echo "$metric / 1024" | bc -l)"
printf '  (参考) RssFile     : %8.1f MB\n' "$(echo "$rss_file / 1024" | bc -l)"
printf '  (参考) Java Heap   : %8.1f MB\n' "$(echo "${java_heap:-0} / 1024" | bc -l)"
printf '  (参考) Native Heap : %8.1f MB   ← ビットマップはここ (API 26+)\n' "$(echo "${native_heap:-0} / 1024" | bc -l)"
printf '  (参考) Graphics    : %8.1f MB\n' "$(echo "${graphics:-0} / 1024" | bc -l)"
echo
