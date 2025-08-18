package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "disclosures", indexes = {
    @Index(name = "idx_disclosure_stock_id", columnList = "stock_id"),
    @Index(name = "idx_disclosure_received_date", columnList = "receivedDate"),
    @Index(name = "idx_disclosure_is_important", columnList = "isImportant"),
    @Index(name = "idx_disclosure_is_read", columnList = "isRead"),
    @Index(name = "idx_disclosure_report_number", columnList = "reportNumber"),
    @Index(name = "idx_stock_received_date", columnList = "stock_id, receivedDate"),
    @Index(name = "idx_important_received_date", columnList = "isImportant, receivedDate"),
    @Index(name = "idx_read_received_date", columnList = "isRead, receivedDate")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Disclosure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;
    
    @Column(nullable = false)
    private String reportNumber; // 접수번호
    
    @Column(nullable = false)
    private String corpCode; // 고유번호
    
    @Column(nullable = false)
    private String corpName; // 회사명
    
    @Column(nullable = false, length = 500)
    private String reportName; // 보고서명
    
    @Column(nullable = false)
    private LocalDateTime receivedDate; // 접수일시
    
    @Column(nullable = false)
    private String submitter; // 제출인
    
    @Column
    private String reportType; // 보고서 타입 (정기공시, 주요사항보고, 기타 등)
    
    @Column(length = 1000)
    private String viewUrl; // 공시 상세 URL
    
    @Column(columnDefinition = "TEXT")
    private String summary; // 공시 요약
    
    @Column
    private Boolean isImportant; // 중요 공시 여부
    
    @Column
    private Boolean isRead; // 읽음 여부
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isRead == null) {
            isRead = false;
        }
        if (isImportant == null) {
            isImportant = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}