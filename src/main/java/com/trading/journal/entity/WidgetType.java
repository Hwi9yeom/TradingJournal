package com.trading.journal.entity;

/** 대시보드 위젯 유형 */
public enum WidgetType {
    // 요약 카드
    PORTFOLIO_SUMMARY, // 포트폴리오 요약 (총자산, 수익률)
    TODAY_PERFORMANCE, // 오늘의 성과
    PROFIT_LOSS_CARD, // 총 손익 카드
    HOLDINGS_COUNT, // 보유 종목 수

    // 차트
    EQUITY_CURVE, // 누적 수익률 차트
    DRAWDOWN_CHART, // 낙폭 차트
    ALLOCATION_PIE, // 자산 배분 파이차트
    MONTHLY_RETURNS, // 월별 수익률 바차트
    SECTOR_ALLOCATION, // 섹터별 비중

    // 목록
    HOLDINGS_LIST, // 보유 종목 목록
    RECENT_TRANSACTIONS, // 최근 거래 내역
    TOP_PERFORMERS, // 수익률 상위 종목
    WORST_PERFORMERS, // 수익률 하위 종목

    // 목표/알림
    GOALS_PROGRESS, // 목표 진행 상황
    ACTIVE_ALERTS, // 활성 알림

    // 분석
    RISK_METRICS, // 리스크 지표
    TRADING_STATS, // 거래 통계 (승률, 평균손익)
    STREAK_INDICATOR, // 연승/연패 표시

    // 고급 분석 (Advanced Analytics)
    MONTE_CARLO_SUMMARY, // 몬테카를로 시뮬레이션 요약 (VaR, 기대수익)
    MONTE_CARLO_CHART, // 몬테카를로 분포 차트
    STRESS_TEST_SUMMARY, // 스트레스 테스트 결과 요약
    STRESS_TEST_SCENARIOS, // 시나리오별 영향 목록
    TAX_HARVESTING_OPPORTUNITIES, // Tax-Loss Harvesting 기회
    SCREENER_WATCHLIST // 스크리너 관심종목
}
