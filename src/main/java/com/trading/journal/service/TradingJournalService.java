package com.trading.journal.service;

import com.trading.journal.dto.TradingJournalDto;
import com.trading.journal.dto.TradingJournalDto.*;
import com.trading.journal.entity.EmotionState;
import com.trading.journal.entity.TradingJournal;
import com.trading.journal.entity.Transaction;
import com.trading.journal.entity.TransactionType;
import com.trading.journal.repository.TradingJournalRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 트레이딩 일지 서비스 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradingJournalService {

    private final TradingJournalRepository journalRepository;
    private final TransactionRepository transactionRepository;

    /** 일지 생성 */
    @Transactional
    public TradingJournalDto createJournal(JournalRequest request) {
        log.info("일지 생성: date={}", request.getJournalDate());

        // 중복 체크
        Optional<TradingJournal> existing =
                journalRepository.findByAccountIdOrNullAndJournalDate(
                        request.getAccountId(), request.getJournalDate());
        if (existing.isPresent()) {
            throw new RuntimeException("해당 날짜의 일지가 이미 존재합니다: " + request.getJournalDate());
        }

        TradingJournal journal =
                TradingJournal.builder()
                        .accountId(request.getAccountId())
                        .journalDate(request.getJournalDate())
                        .marketOverview(request.getMarketOverview())
                        .tradingPlan(request.getTradingPlan())
                        .executionReview(request.getExecutionReview())
                        .morningEmotion(request.getMorningEmotion())
                        .eveningEmotion(request.getEveningEmotion())
                        .focusScore(request.getFocusScore())
                        .disciplineScore(request.getDisciplineScore())
                        .lessonsLearned(request.getLessonsLearned())
                        .tomorrowPlan(request.getTomorrowPlan())
                        .tags(request.getTags())
                        .build();

        // 거래 요약 계산
        calculateTradeSummary(journal);

        TradingJournal saved = journalRepository.save(journal);
        return convertToDto(saved);
    }

    /** 일지 수정 */
    @Transactional
    public TradingJournalDto updateJournal(Long id, JournalRequest request) {
        log.info("일지 수정: id={}", id);

        TradingJournal journal =
                journalRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("일지를 찾을 수 없습니다: " + id));

        journal.setMarketOverview(request.getMarketOverview());
        journal.setTradingPlan(request.getTradingPlan());
        journal.setExecutionReview(request.getExecutionReview());
        journal.setMorningEmotion(request.getMorningEmotion());
        journal.setEveningEmotion(request.getEveningEmotion());
        journal.setFocusScore(request.getFocusScore());
        journal.setDisciplineScore(request.getDisciplineScore());
        journal.setLessonsLearned(request.getLessonsLearned());
        journal.setTomorrowPlan(request.getTomorrowPlan());
        journal.setTags(request.getTags());

        // 거래 요약 재계산
        calculateTradeSummary(journal);

        TradingJournal saved = journalRepository.save(journal);
        return convertToDto(saved);
    }

    /** 날짜로 일지 조회 */
    public TradingJournalDto getJournalByDate(Long accountId, LocalDate date) {
        Optional<TradingJournal> journal =
                journalRepository.findByAccountIdOrNullAndJournalDate(accountId, date);

        if (journal.isPresent()) {
            return convertToDto(journal.get());
        }

        // 일지가 없으면 빈 템플릿 반환
        return createEmptyJournalDto(accountId, date);
    }

    /** 일지 삭제 */
    @Transactional
    public void deleteJournal(Long id) {
        log.info("일지 삭제: id={}", id);
        if (!journalRepository.existsById(id)) {
            throw new RuntimeException("일지를 찾을 수 없습니다: " + id);
        }
        journalRepository.deleteById(id);
    }

    /** 기간별 일지 조회 */
    public List<TradingJournalDto> getJournalRange(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        List<TradingJournal> journals =
                journalRepository.findByAccountIdAndDateRange(accountId, startDate, endDate);
        return journals.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /** 일지 목록 (캘린더 뷰용) */
    public List<JournalListItem> getJournalList(
            Long accountId, LocalDate startDate, LocalDate endDate) {
        List<TradingJournal> journals =
                journalRepository.findByAccountIdAndDateRange(accountId, startDate, endDate);
        return journals.stream().map(this::convertToListItem).collect(Collectors.toList());
    }

    /** 일지 통계 조회 */
    public JournalStatistics getStatistics(Long accountId, LocalDate startDate, LocalDate endDate) {
        Long totalJournals =
                journalRepository.countByAccountIdAndDateRange(accountId, startDate, endDate);
        Double avgFocusScore =
                journalRepository.getAverageFocusScore(accountId, startDate, endDate);
        Double avgDisciplineScore =
                journalRepository.getAverageDisciplineScore(accountId, startDate, endDate);

        // 감정 통계
        List<Object[]> morningStats =
                journalRepository.getMorningEmotionStats(accountId, startDate, endDate);
        Map<String, Long> morningEmotionCounts =
                morningStats.stream()
                        .collect(
                                Collectors.toMap(
                                        arr -> ((EmotionState) arr[0]).name(),
                                        arr -> (Long) arr[1]));

        List<Object[]> eveningStats =
                journalRepository.getEveningEmotionStats(accountId, startDate, endDate);
        Map<String, Long> eveningEmotionCounts =
                eveningStats.stream()
                        .collect(
                                Collectors.toMap(
                                        arr -> ((EmotionState) arr[0]).name(),
                                        arr -> (Long) arr[1]));

        // 감정 추이
        List<TradingJournal> journals =
                journalRepository.findByAccountIdAndDateRange(accountId, startDate, endDate);
        List<EmotionTrend> emotionTrends =
                journals.stream()
                        .map(
                                j ->
                                        EmotionTrend.builder()
                                                .date(j.getJournalDate())
                                                .morningEmotion(j.getMorningEmotion())
                                                .eveningEmotion(j.getEveningEmotion())
                                                .dailyProfit(j.getTradeSummaryProfit())
                                                .build())
                        .collect(Collectors.toList());

        // 점수 추이
        List<ScoreTrend> scoreTrends =
                journals.stream()
                        .map(
                                j ->
                                        ScoreTrend.builder()
                                                .date(j.getJournalDate())
                                                .focusScore(j.getFocusScore())
                                                .disciplineScore(j.getDisciplineScore())
                                                .dailyProfit(j.getTradeSummaryProfit())
                                                .build())
                        .collect(Collectors.toList());

        // 최근 교훈
        List<String> recentLessons =
                journals.stream()
                        .filter(
                                j ->
                                        j.getLessonsLearned() != null
                                                && !j.getLessonsLearned().isBlank())
                        .sorted(Comparator.comparing(TradingJournal::getJournalDate).reversed())
                        .limit(5)
                        .map(TradingJournal::getLessonsLearned)
                        .collect(Collectors.toList());

        return JournalStatistics.builder()
                .totalJournals(totalJournals)
                .avgFocusScore(avgFocusScore)
                .avgDisciplineScore(avgDisciplineScore)
                .morningEmotionCounts(morningEmotionCounts)
                .eveningEmotionCounts(eveningEmotionCounts)
                .emotionTrends(emotionTrends)
                .scoreTrends(scoreTrends)
                .recentLessons(recentLessons)
                .build();
    }

    // === Helper Methods ===

    /** 거래 요약 계산 */
    private void calculateTradeSummary(TradingJournal journal) {
        LocalDate date = journal.getJournalDate();
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Transaction> dayTransactions;
        if (journal.getAccountId() != null) {
            dayTransactions =
                    transactionRepository.findByAccountIdAndDateRange(
                            journal.getAccountId(), startOfDay, endOfDay);
        } else {
            dayTransactions = transactionRepository.findByDateRange(startOfDay, endOfDay);
        }

        List<Transaction> sellTrades =
                dayTransactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.SELL
                                                && t.getRealizedPnl() != null)
                        .toList();

        int tradeCount = sellTrades.size();
        BigDecimal totalProfit =
                sellTrades.stream()
                        .map(Transaction::getRealizedPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        long winCount =
                sellTrades.stream()
                        .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                        .count();
        BigDecimal winRate =
                tradeCount > 0
                        ? BigDecimal.valueOf(winCount * 100.0 / tradeCount)
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        journal.setTradeSummaryCount(tradeCount);
        journal.setTradeSummaryProfit(totalProfit);
        journal.setTradeSummaryWinRate(winRate);
    }

    /** Entity → DTO 변환 */
    private TradingJournalDto convertToDto(TradingJournal journal) {
        String dayOfWeek =
                journal.getJournalDate()
                        .getDayOfWeek()
                        .getDisplayName(TextStyle.FULL, Locale.KOREAN);

        return TradingJournalDto.builder()
                .id(journal.getId())
                .accountId(journal.getAccountId())
                .journalDate(journal.getJournalDate())
                .dayOfWeek(dayOfWeek)
                .marketOverview(journal.getMarketOverview())
                .tradingPlan(journal.getTradingPlan())
                .executionReview(journal.getExecutionReview())
                .lessonsLearned(journal.getLessonsLearned())
                .tomorrowPlan(journal.getTomorrowPlan())
                .tags(journal.getTags())
                .morningEmotion(journal.getMorningEmotion())
                .morningEmotionLabel(
                        journal.getMorningEmotion() != null
                                ? journal.getMorningEmotion().getLabel()
                                : null)
                .eveningEmotion(journal.getEveningEmotion())
                .eveningEmotionLabel(
                        journal.getEveningEmotion() != null
                                ? journal.getEveningEmotion().getLabel()
                                : null)
                .focusScore(journal.getFocusScore())
                .disciplineScore(journal.getDisciplineScore())
                .tradeSummaryCount(journal.getTradeSummaryCount())
                .tradeSummaryProfit(journal.getTradeSummaryProfit())
                .tradeSummaryWinRate(journal.getTradeSummaryWinRate())
                .createdAt(journal.getCreatedAt())
                .updatedAt(journal.getUpdatedAt())
                .build();
    }

    /** Entity → ListItem 변환 */
    private JournalListItem convertToListItem(TradingJournal journal) {
        String dayOfWeek =
                journal.getJournalDate()
                        .getDayOfWeek()
                        .getDisplayName(TextStyle.SHORT, Locale.KOREAN);

        boolean hasContent =
                (journal.getMarketOverview() != null && !journal.getMarketOverview().isBlank())
                        || (journal.getTradingPlan() != null && !journal.getTradingPlan().isBlank())
                        || (journal.getExecutionReview() != null
                                && !journal.getExecutionReview().isBlank())
                        || (journal.getLessonsLearned() != null
                                && !journal.getLessonsLearned().isBlank());

        return JournalListItem.builder()
                .id(journal.getId())
                .journalDate(journal.getJournalDate())
                .dayOfWeek(dayOfWeek)
                .morningEmotion(journal.getMorningEmotion())
                .eveningEmotion(journal.getEveningEmotion())
                .focusScore(journal.getFocusScore())
                .disciplineScore(journal.getDisciplineScore())
                .tradeSummaryCount(journal.getTradeSummaryCount())
                .tradeSummaryProfit(journal.getTradeSummaryProfit())
                .hasContent(hasContent)
                .build();
    }

    /** 빈 일지 DTO 생성 (날짜만 있는 템플릿) */
    private TradingJournalDto createEmptyJournalDto(Long accountId, LocalDate date) {
        String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);

        // 해당 날짜의 거래 요약 계산
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Transaction> dayTransactions;
        if (accountId != null) {
            dayTransactions =
                    transactionRepository.findByAccountIdAndDateRange(
                            accountId, startOfDay, endOfDay);
        } else {
            dayTransactions = transactionRepository.findByDateRange(startOfDay, endOfDay);
        }

        List<Transaction> sellTrades =
                dayTransactions.stream()
                        .filter(
                                t ->
                                        t.getType() == TransactionType.SELL
                                                && t.getRealizedPnl() != null)
                        .toList();

        int tradeCount = sellTrades.size();
        BigDecimal totalProfit =
                sellTrades.stream()
                        .map(Transaction::getRealizedPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        long winCount =
                sellTrades.stream()
                        .filter(t -> t.getRealizedPnl().compareTo(BigDecimal.ZERO) > 0)
                        .count();
        BigDecimal winRate =
                tradeCount > 0
                        ? BigDecimal.valueOf(winCount * 100.0 / tradeCount)
                                .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        return TradingJournalDto.builder()
                .accountId(accountId)
                .journalDate(date)
                .dayOfWeek(dayOfWeek)
                .tradeSummaryCount(tradeCount)
                .tradeSummaryProfit(totalProfit)
                .tradeSummaryWinRate(winRate)
                .build();
    }
}
