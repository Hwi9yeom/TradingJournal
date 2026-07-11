#!/usr/bin/env bash
# 페이지가 새 디자인 시스템으로 마이그레이션되었는지 검증한다.
# Usage: check-frontend.sh page.html [page2.html ...]   # 지정 페이지 검사
#        check-frontend.sh --all                        # 전체 페이지 + 삭제 파일 참조 검사
set -u
[ $# -eq 0 ] && { echo "usage: $0 page.html ... | --all" >&2; exit 2; }
cd "$(dirname "$0")/../src/main/resources/static" || exit 1

PAGES=(accounts.html ai-assistant.html alerts.html backtest.html correlation.html
       dashboard.html dividend.html export.html goals.html index.html journal.html
       login.html patterns.html plans.html reviews.html risk.html sectors.html statistics.html)
DELETED=(dashboard-new.html dashboard-original.html dashboard-custom.html
         dashboard-custom.js dashboard.js charts.js style.css
         css/dashboard-glass.css js/navigation-glass.js js/theme-toggle.js)
fail=0

err() { echo "FAIL: $1"; fail=1; }

check_page() {
    local p="$1"
    [ -f "$p" ] || { err "$p: 파일 없음"; return; }
    grep -q 'css/design-system.css' "$p"       || err "$p: design-system.css 미포함"
    grep -q "localStorage.getItem('trading-journal-theme')" "$p" || err "$p: 테마 FOUC 인라인 스니펫 없음"
    grep -q 'js/theme.js' "$p"                 || err "$p: js/theme.js 미포함"
    grep -q 'dashboard-glass.css' "$p"         && err "$p: 구 dashboard-glass.css 참조 잔존"
    grep -q 'navigation-glass.js\|theme-toggle.js' "$p" && err "$p: 구 nav/theme 스크립트 잔존"
    grep -q 'navbar-glass' "$p"                && err "$p: 하드코딩 navbar 잔존"
    grep -q 'bg-orb' "$p"                      && err "$p: 하드코딩 배경 오브 잔존"
    if [ "$p" != "login.html" ]; then
        grep -q 'js/navigation.js' "$p"        || err "$p: js/navigation.js 미포함"
    fi
}

if [ "${1:-}" = "--all" ]; then
    for p in "${PAGES[@]}"; do check_page "$p"; done
    for d in "${DELETED[@]}"; do
        [ -e "$d" ] && err "$d: 삭제 대상 파일이 아직 존재"
        name=$(basename "$d")
        esc=$(printf '%s' "$name" | sed 's/\./\\./g')
        # src/href/문자열 참조만 잡고, style.cssText 같은 부분 일치는 배제
        refs=$(find . \( -name '*.html' -o -name '*.js' -o -name '*.css' \) -print0 \
            | xargs -0 grep -lE "(^|[^[:alnum:]_.-])${esc}([^[:alnum:]_]|\$)" 2>/dev/null \
            | grep -v check-frontend | tr '\n' ' ' || true)
        [ -n "$refs" ] && err "$d 참조 잔존: $refs"
    done
else
    for p in "$@"; do check_page "$p"; done
fi

[ $fail -eq 0 ] && echo "OK" || exit 1
