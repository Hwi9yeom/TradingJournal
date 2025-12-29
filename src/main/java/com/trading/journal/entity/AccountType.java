package com.trading.journal.entity;

/**
 * 투자 계좌 유형
 */
public enum AccountType {
    ISA,        // 개인종합자산관리계좌
    PENSION,    // 연금저축
    GENERAL,    // 일반 위탁계좌
    CUSTOM      // 사용자 정의 (전략별 등)
}
