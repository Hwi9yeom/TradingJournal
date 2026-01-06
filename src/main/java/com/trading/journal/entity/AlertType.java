package com.trading.journal.entity;

/**
 * 알림 유형
 */
public enum AlertType {
    // 목표 관련
    GOAL_MILESTONE,      // 목표 마일스톤 달성
    GOAL_COMPLETED,      // 목표 달성
    GOAL_DEADLINE,       // 목표 마감 임박
    GOAL_OVERDUE,        // 목표 기한 초과

    // 손익 관련
    PROFIT_TARGET,       // 목표 수익 달성
    LOSS_LIMIT,          // 손실 한도 초과
    DAILY_LOSS_LIMIT,    // 일일 손실 한도
    DRAWDOWN_WARNING,    // 낙폭 경고

    // 포트폴리오 관련
    PORTFOLIO_CHANGE,    // 포트폴리오 대폭 변동
    POSITION_SIZE,       // 포지션 비중 초과
    SECTOR_CONCENTRATION,// 섹터 집중도 경고

    // 거래 관련
    TRADE_EXECUTED,      // 거래 체결
    WINNING_STREAK,      // 연승 기록
    LOSING_STREAK,       // 연패 경고

    // 시스템
    SYSTEM_INFO,         // 시스템 정보
    SYSTEM_WARNING,      // 시스템 경고
    CUSTOM               // 사용자 정의
}
