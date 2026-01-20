package com.trading.journal.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.*;
import org.springframework.stereotype.Service;

/** AI 프롬프트 템플릿 서비스 4가지 분석 유형에 대한 프롬프트 생성 */
@Service
public class PromptTemplateService {

    private final ObjectMapper objectMapper;

    // 시스템 프롬프트 - 트레이딩 전문가 역할
    private static final String SYSTEM_PROMPT =
            """
        당신은 10년 이상의 경험을 가진 전문 트레이더이자 투자 분석가입니다.
        주어진 데이터를 분석하고 실행 가능한 인사이트를 제공합니다.

        분석 원칙:
        1. 객관적인 데이터 기반 분석을 제공합니다
        2. 리스크 관리의 중요성을 항상 강조합니다
        3. 구체적이고 실행 가능한 조언을 제공합니다
        4. 심리적 요소와 감정 관리의 중요성을 인지합니다
        5. 한국어로 응답합니다

        응답 형식:
        - 명확하고 구조화된 형식으로 응답합니다
        - 핵심 포인트를 먼저 제시합니다
        - 필요시 수치를 인용하여 근거를 제시합니다
        """;

    public PromptTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 시스템 프롬프트 반환 */
    public String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /** 성과 분석 프롬프트 생성 */
    public String buildPerformanceAnalysisPrompt(
            PeriodAnalysisDto periodAnalysis,
            RiskMetricsDto riskMetrics,
            TradingPatternDto tradingPattern) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("## 거래 성과 분석 요청\n\n");
        prompt.append("아래 트레이딩 데이터를 분석하고 종합적인 성과 평가를 제공해주세요.\n\n");

        // 기간 분석 데이터
        prompt.append("### 기간 성과 데이터\n");
        prompt.append(
                String.format(
                        "- 분석 기간: %s ~ %s\n",
                        periodAnalysis.getStartDate(), periodAnalysis.getEndDate()));
        prompt.append(
                String.format(
                        "- 총 거래 횟수: %d건 (매수 %d, 매도 %d)\n",
                        periodAnalysis.getTotalTransactions(),
                        periodAnalysis.getBuyTransactions(),
                        periodAnalysis.getSellTransactions()));
        prompt.append(
                String.format(
                        "- 실현 손익: %s원 (수익률 %s%%)\n",
                        formatNumber(periodAnalysis.getRealizedProfit()),
                        periodAnalysis.getRealizedProfitRate()));
        prompt.append(
                String.format(
                        "- 승률: %s%% (승 %d, 패 %d)\n",
                        periodAnalysis.getWinRate(),
                        periodAnalysis.getWinCount(),
                        periodAnalysis.getLossCount()));
        prompt.append(String.format("- 평균 수익률: %s%%\n", periodAnalysis.getAverageReturn()));
        prompt.append(String.format("- 샤프 비율: %s\n", periodAnalysis.getSharpeRatio()));
        prompt.append(String.format("- 최대 낙폭: %s%%\n", periodAnalysis.getMaxDrawdown()));
        prompt.append("\n");

        // 리스크 지표
        if (riskMetrics != null) {
            prompt.append("### 리스크 지표\n");
            prompt.append(String.format("- 샤프 비율: %s\n", riskMetrics.getSharpeRatio()));
            prompt.append(String.format("- 소르티노 비율: %s\n", riskMetrics.getSortinoRatio()));
            prompt.append(String.format("- 칼마 비율: %s\n", riskMetrics.getCalmarRatio()));
            prompt.append(String.format("- 변동성: %s%%\n", riskMetrics.getVolatility()));
            prompt.append(String.format("- 최대 낙폭: %s%%\n", riskMetrics.getMaxDrawdown()));
            prompt.append(String.format("- 손익비: %s\n", riskMetrics.getProfitFactor()));
            prompt.append(
                    String.format(
                            "- 리스크 등급: %s\n",
                            riskMetrics.getRiskLevel() != null
                                    ? riskMetrics.getRiskLevel().getLabel()
                                    : "N/A"));
            if (riskMetrics.getVar95() != null) {
                prompt.append(
                        String.format(
                                "- 95%% VaR (일간): %s%%\n", riskMetrics.getVar95().getDailyVaR()));
            }
            prompt.append("\n");
        }

        // 거래 패턴
        if (tradingPattern != null) {
            prompt.append("### 거래 패턴\n");
            if (tradingPattern.getStreakAnalysis() != null) {
                var streak = tradingPattern.getStreakAnalysis();
                prompt.append(
                        String.format("- 현재 스트릭: %d (양수=연승, 음수=연패)\n", streak.getCurrentStreak()));
                prompt.append(
                        String.format(
                                "- 최대 연승: %d, 최대 연패: %d\n",
                                streak.getMaxWinStreak(), streak.getMaxLossStreak()));
            }
            if (tradingPattern.getHoldingPeriodAnalysis() != null) {
                var holding = tradingPattern.getHoldingPeriodAnalysis();
                prompt.append(String.format("- 평균 보유 기간: %s일\n", holding.getAvgHoldingDays()));
                prompt.append(
                        String.format(
                                "- 수익 거래 평균 보유: %s일, 손실 거래 평균 보유: %s일\n",
                                holding.getAvgWinHoldingDays(), holding.getAvgLossHoldingDays()));
            }
            prompt.append("\n");
        }

        prompt.append(
                """
                ### 분석 요청
                위 데이터를 바탕으로 다음을 분석해주세요:

                1. **성과 요약**: 전반적인 거래 성과 평가
                2. **강점**: 잘하고 있는 부분 (2-3가지)
                3. **개선점**: 개선이 필요한 부분 (2-3가지)
                4. **리스크 평가**: 리스크 관리 상태 평가
                5. **실행 제안**: 구체적인 개선 행동 (2-3가지)

                간결하고 실행 가능한 조언을 제공해주세요.
                """);

        return prompt.toString();
    }

    /** 거래 복기 자동 생성 프롬프트 */
    public String buildTradeReviewPrompt(
            TransactionDto sellTransaction, TransactionDto buyTransaction) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("## 거래 복기 생성 요청\n\n");
        prompt.append("아래 거래 데이터를 분석하여 거래 복기를 생성해주세요.\n\n");

        prompt.append("### 거래 정보\n");
        prompt.append(
                String.format(
                        "- 종목: %s (%s)\n",
                        sellTransaction.getStockName(), sellTransaction.getStockSymbol()));

        // 매수 정보
        if (buyTransaction != null) {
            prompt.append(
                    String.format(
                            "- 매수일: %s\n", buyTransaction.getTransactionDate().toLocalDate()));
            prompt.append(String.format("- 매수가: %s원\n", formatNumber(buyTransaction.getPrice())));
            prompt.append(String.format("- 매수 수량: %s주\n", buyTransaction.getQuantity()));
            if (buyTransaction.getStopLossPrice() != null) {
                prompt.append(
                        String.format(
                                "- 설정 손절가: %s원\n",
                                formatNumber(buyTransaction.getStopLossPrice())));
            }
            if (buyTransaction.getTakeProfitPrice() != null) {
                prompt.append(
                        String.format(
                                "- 설정 익절가: %s원\n",
                                formatNumber(buyTransaction.getTakeProfitPrice())));
            }
        }

        // 매도 정보
        prompt.append(
                String.format("- 매도일: %s\n", sellTransaction.getTransactionDate().toLocalDate()));
        prompt.append(String.format("- 매도가: %s원\n", formatNumber(sellTransaction.getPrice())));
        prompt.append(String.format("- 매도 수량: %s주\n", sellTransaction.getQuantity()));
        prompt.append(
                String.format("- 실현 손익: %s원\n", formatNumber(sellTransaction.getRealizedPnl())));
        if (sellTransaction.getRMultiple() != null) {
            prompt.append(String.format("- R-multiple: %s\n", sellTransaction.getRMultiple()));
        }

        // 보유 기간 계산
        if (buyTransaction != null) {
            long days =
                    java.time.temporal.ChronoUnit.DAYS.between(
                            buyTransaction.getTransactionDate().toLocalDate(),
                            sellTransaction.getTransactionDate().toLocalDate());
            prompt.append(String.format("- 보유 기간: %d일\n", days));
        }

        // 노트
        if (buyTransaction != null
                && buyTransaction.getNotes() != null
                && !buyTransaction.getNotes().isEmpty()) {
            prompt.append(String.format("- 매수 메모: %s\n", buyTransaction.getNotes()));
        }
        if (sellTransaction.getNotes() != null && !sellTransaction.getNotes().isEmpty()) {
            prompt.append(String.format("- 매도 메모: %s\n", sellTransaction.getNotes()));
        }

        prompt.append("\n");
        prompt.append(
                """
                ### 복기 생성 요청
                위 거래를 분석하여 다음 내용을 생성해주세요:

                1. **거래 요약**: 이 거래를 한 문장으로 요약
                2. **진입 이유 추정**: 매수 타이밍의 가능한 이유 (기술적/기본적 요인)
                3. **청산 이유 추정**: 매도 타이밍의 가능한 이유
                4. **실행 품질**: 진입/청산 타이밍 평가 (1-5점)
                5. **감정 상태 추정**: 거래 중 가능한 감정 상태
                6. **교훈**: 이 거래에서 배울 점
                7. **개선점**: 다음에 개선할 사항
                8. **추천 태그**: 이 거래에 적합한 태그 3개

                객관적이고 건설적인 관점에서 분석해주세요.
                """);

        return prompt.toString();
    }

    /** 리스크 경고 분석 프롬프트 */
    public String buildRiskWarningPrompt(
            RiskMetricsDto riskMetrics,
            PortfolioSummaryDto portfolioSummary,
            RiskDashboardDto riskDashboard) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("## 리스크 분석 및 경고 요청\n\n");
        prompt.append("현재 포트폴리오의 리스크 상태를 분석하고 경고사항을 알려주세요.\n\n");

        // 포트폴리오 현황
        if (portfolioSummary != null) {
            prompt.append("### 포트폴리오 현황\n");
            prompt.append(
                    String.format(
                            "- 총 평가금액: %s원\n",
                            formatNumber(portfolioSummary.getTotalCurrentValue())));
            prompt.append(
                    String.format(
                            "- 총 투자금액: %s원\n",
                            formatNumber(portfolioSummary.getTotalInvestment())));
            prompt.append(
                    String.format("- 총 수익률: %s%%\n", portfolioSummary.getTotalProfitLossPercent()));
            if (portfolioSummary.getHoldings() != null) {
                prompt.append(
                        String.format("- 보유 종목 수: %d개\n", portfolioSummary.getHoldings().size()));
            }
            prompt.append("\n");
        }

        // 리스크 지표
        if (riskMetrics != null) {
            prompt.append("### 리스크 지표\n");
            prompt.append(
                    String.format(
                            "- 리스크 등급: %s\n",
                            riskMetrics.getRiskLevel() != null
                                    ? riskMetrics.getRiskLevel().getLabel()
                                    : "미분류"));
            prompt.append(String.format("- 변동성: %s%%\n", riskMetrics.getVolatility()));
            prompt.append(String.format("- 최대 낙폭: %s%%\n", riskMetrics.getMaxDrawdown()));
            prompt.append(String.format("- 샤프 비율: %s\n", riskMetrics.getSharpeRatio()));
            prompt.append(String.format("- 손익비: %s\n", riskMetrics.getProfitFactor()));
            if (riskMetrics.getVar95() != null) {
                prompt.append(
                        String.format(
                                "- 95%% VaR: 일간 %s%%, 월간 %s%%\n",
                                riskMetrics.getVar95().getDailyVaR(),
                                riskMetrics.getVar95().getMonthlyVaR()));
            }
            prompt.append("\n");
        }

        // 리스크 대시보드 데이터
        if (riskDashboard != null) {
            prompt.append("### 오늘의 리스크 현황\n");
            prompt.append(
                    String.format(
                            "- 일일 손익: %s원 (%s%%)\n",
                            formatNumber(riskDashboard.getTodayPnl()),
                            riskDashboard.getTodayPnlPercent()));
            prompt.append(
                    String.format(
                            "- 주간 손익: %s원 (%s%%)\n",
                            formatNumber(riskDashboard.getWeekPnl()),
                            riskDashboard.getWeekPnlPercent()));
            prompt.append(
                    String.format(
                            "- 총 오픈 리스크: %s원 (%s%%)\n",
                            formatNumber(riskDashboard.getTotalOpenRisk()),
                            riskDashboard.getOpenRiskPercent()));

            if (riskDashboard.getDailyLossStatus() != null) {
                var status = riskDashboard.getDailyLossStatus();
                prompt.append(
                        String.format(
                                "- 일일 손실 한도: %s%% (사용률 %s%%, 상태: %s)\n",
                                status.getLimit(),
                                status.getPercentUsed(),
                                status.getStatusLabel()));
            }
            if (riskDashboard.getPositionCountStatus() != null) {
                var status = riskDashboard.getPositionCountStatus();
                prompt.append(
                        String.format(
                                "- 포지션 수: %s / %s개 (상태: %s)\n",
                                status.getCurrent(), status.getLimit(), status.getStatusLabel()));
            }
            prompt.append("\n");

            // 집중도 경고
            if (riskDashboard.getConcentrationAlerts() != null
                    && !riskDashboard.getConcentrationAlerts().isEmpty()) {
                prompt.append("### 집중도 경고\n");
                for (var alert : riskDashboard.getConcentrationAlerts()) {
                    prompt.append(
                            String.format(
                                    "- %s (%s): %.1f%% (한도 %.1f%%)\n",
                                    alert.getStockName(),
                                    alert.getAlertType(),
                                    alert.getConcentration(),
                                    alert.getLimit()));
                }
                prompt.append("\n");
            }
        }

        prompt.append(
                """
                ### 분석 요청
                위 데이터를 분석하여 다음을 제공해주세요:

                1. **리스크 상태 요약**: 전반적인 리스크 수준 평가
                2. **즉시 주의 필요**: 당장 조치가 필요한 리스크 (있다면)
                3. **잠재적 위험**: 주시해야 할 잠재적 리스크
                4. **개선 권고**: 리스크 관리 개선을 위한 구체적 조언
                5. **긍정적 요소**: 잘 관리되고 있는 부분

                심각도에 따라 우선순위를 매겨 알려주세요.
                """);

        return prompt.toString();
    }

    /** 전략 최적화 제안 프롬프트 */
    public String buildStrategyOptimizationPrompt(BacktestResultDto backtestResult) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("## 전략 최적화 분석 요청\n\n");
        prompt.append("백테스트 결과를 분석하여 전략 개선안을 제안해주세요.\n\n");

        prompt.append("### 전략 정보\n");
        prompt.append(String.format("- 전략명: %s\n", backtestResult.getStrategyName()));
        prompt.append(String.format("- 전략 유형: %s\n", backtestResult.getStrategyType()));
        prompt.append(String.format("- 대상 종목: %s\n", backtestResult.getSymbol()));
        prompt.append(
                String.format(
                        "- 테스트 기간: %s ~ %s\n",
                        backtestResult.getStartDate(), backtestResult.getEndDate()));

        // 전략 파라미터
        if (backtestResult.getStrategyConfig() != null) {
            prompt.append("- 전략 파라미터:\n");
            for (var entry : backtestResult.getStrategyConfig().entrySet()) {
                prompt.append(String.format("  - %s: %s\n", entry.getKey(), entry.getValue()));
            }
        }
        prompt.append("\n");

        prompt.append("### 성과 지표\n");
        prompt.append(
                String.format("- 초기 자본: %s원\n", formatNumber(backtestResult.getInitialCapital())));
        prompt.append(
                String.format("- 최종 자본: %s원\n", formatNumber(backtestResult.getFinalCapital())));
        prompt.append(String.format("- 총 수익률: %s%%\n", backtestResult.getTotalReturn()));
        prompt.append(String.format("- CAGR: %s%%\n", backtestResult.getCagr()));
        prompt.append(String.format("- 월평균 수익률: %s%%\n", backtestResult.getMonthlyReturn()));
        prompt.append("\n");

        prompt.append("### 거래 통계\n");
        prompt.append(String.format("- 총 거래: %d건\n", backtestResult.getTotalTrades()));
        prompt.append(
                String.format(
                        "- 승률: %s%% (승 %d, 패 %d)\n",
                        backtestResult.getWinRate(),
                        backtestResult.getWinningTrades(),
                        backtestResult.getLosingTrades()));
        prompt.append(
                String.format(
                        "- 평균 수익: %s%%, 평균 손실: %s%%\n",
                        backtestResult.getAvgWinPercent(), backtestResult.getAvgLossPercent()));
        prompt.append(
                String.format("- 손익비 (Profit Factor): %s\n", backtestResult.getProfitFactor()));
        prompt.append(String.format("- 기대값: %s\n", backtestResult.getExpectancy()));
        prompt.append(
                String.format(
                        "- 최대 연승: %d, 최대 연패: %d\n",
                        backtestResult.getMaxWinStreak(), backtestResult.getMaxLossStreak()));
        prompt.append("\n");

        prompt.append("### 리스크 지표\n");
        prompt.append(String.format("- 샤프 비율: %s\n", backtestResult.getSharpeRatio()));
        prompt.append(String.format("- 소르티노 비율: %s\n", backtestResult.getSortinoRatio()));
        prompt.append(String.format("- 칼마 비율: %s\n", backtestResult.getCalmarRatio()));
        prompt.append(String.format("- 변동성: %s%%\n", backtestResult.getVolatility()));
        prompt.append(String.format("- 최대 낙폭: %s%%\n", backtestResult.getMaxDrawdown()));
        prompt.append(String.format("- 평균 보유 기간: %s일\n", backtestResult.getAvgHoldingDays()));
        prompt.append("\n");

        prompt.append(
                """
                ### 최적화 분석 요청
                위 백테스트 결과를 분석하여 다음을 제공해주세요:

                1. **전략 평가**: 현재 전략의 전반적인 품질 평가
                2. **강점**: 전략의 우수한 점 (2-3가지)
                3. **약점**: 전략의 개선이 필요한 점 (2-3가지)
                4. **파라미터 제안**: 구체적인 파라미터 변경 제안 (현재값 → 제안값)
                5. **추가 필터 제안**: 시그널 품질 향상을 위한 필터 아이디어
                6. **리스크 관리 제안**: 손절/익절 전략 개선안
                7. **예상 효과**: 제안 사항 적용 시 예상되는 개선 효과

                실현 가능하고 구체적인 제안을 해주세요.
                """);

        return prompt.toString();
    }

    /** 자유 대화 프롬프트 (채팅용) */
    public String buildChatPrompt(String userMessage, String context) {
        StringBuilder prompt = new StringBuilder();

        if (context != null && !context.isEmpty()) {
            prompt.append("### 참고 컨텍스트\n");
            prompt.append(context);
            prompt.append("\n\n");
        }

        prompt.append("### 사용자 질문\n");
        prompt.append(userMessage);
        prompt.append("\n\n");
        prompt.append("위 질문에 대해 트레이딩 전문가로서 도움이 되는 답변을 제공해주세요.");

        return prompt.toString();
    }

    /** 숫자 포맷팅 헬퍼 */
    private String formatNumber(Object number) {
        if (number == null) return "N/A";
        if (number instanceof java.math.BigDecimal) {
            return String.format("%,.0f", ((java.math.BigDecimal) number).doubleValue());
        }
        return number.toString();
    }
}
