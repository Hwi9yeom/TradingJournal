package com.trading.journal.service;

import com.trading.journal.dto.SavingsRecordDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.SavingsCategory;
import com.trading.journal.entity.SavingsRecord;
import com.trading.journal.exception.ResourceNotFoundException;
import com.trading.journal.repository.AccountRepository;
import com.trading.journal.repository.SavingsRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저축 기록(저축 일지) 서비스.
 *
 * <p>개별 저축 건을 기록하고 월 단위로 합산한다. 합산 결과는 {@link MonthlyBudgetService}가 월별 실제 저축액으로 사용한다.
 *
 * <p>모든 조회/수정은 현재 인증 사용자로 격리된다. 다른 사용자의 레코드는 존재 여부를 노출하지 않기 위해 404로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsRecordService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SavingsRecordRepository savingsRecordRepository;
    private final AccountRepository accountRepository;
    private final SecurityContextService securityContextService;

    /** 기록 생성 (현재 사용자 소유로 저장) */
    @Transactional
    public SavingsRecordDto create(SavingsRecordDto dto) {
        validate(dto);
        Long userId = currentUserId();
        validateAccountOwnership(dto.getAccountId(), userId);

        SavingsRecord record =
                SavingsRecord.builder()
                        .userId(userId)
                        .accountId(dto.getAccountId())
                        .savedDate(dto.getSavedDate())
                        .amount(dto.getAmount())
                        .category(
                                dto.getCategory() != null
                                        ? dto.getCategory()
                                        : SavingsCategory.REGULAR_SAVINGS)
                        .institution(dto.getInstitution())
                        .memo(dto.getMemo())
                        .build();

        SavingsRecord saved = savingsRecordRepository.save(record);
        log.info("저축 기록 추가: {} ({})", saved.getSavedDate(), saved.getCategory());
        return toDto(saved);
    }

    /** 기록 수정 (본인 소유만) */
    @Transactional
    public SavingsRecordDto update(Long id, SavingsRecordDto dto) {
        validate(dto);
        Long userId = currentUserId();
        validateAccountOwnership(dto.getAccountId(), userId);

        SavingsRecord record = findOwned(id, userId);

        record.setAccountId(dto.getAccountId());
        record.setSavedDate(dto.getSavedDate());
        record.setAmount(dto.getAmount());
        if (dto.getCategory() != null) {
            record.setCategory(dto.getCategory());
        }
        record.setInstitution(dto.getInstitution());
        record.setMemo(dto.getMemo());

        return toDto(savingsRecordRepository.save(record));
    }

    /** 기록 삭제 (본인 소유만) */
    @Transactional
    public void delete(Long id) {
        SavingsRecord record = findOwned(id, currentUserId());
        savingsRecordRepository.delete(record);
        log.info("저축 기록 삭제: {}", id);
    }

    /** 단건 조회 (본인 소유만) */
    public SavingsRecordDto get(Long id) {
        return toDto(findOwned(id, currentUserId()));
    }

    /** 현재 사용자의 전체 기록 최신순 조회 */
    public List<SavingsRecordDto> getAll() {
        return savingsRecordRepository.findAllByUserOrderBySavedDateDesc(currentUserId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 현재 사용자의 기간 조회 */
    public List<SavingsRecordDto> getRange(Long accountId, LocalDate start, LocalDate end) {
        return savingsRecordRepository
                .findByUserAndDateRange(currentUserId(), accountId, start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 현재 사용자의 특정 월 저축 합계.
     *
     * @param accountId 계좌 ID (null이면 계좌가 지정되지 않은 전체 가계 기록만 합산)
     * @param month 대상 월 (일자는 무시)
     * @return 합계. 기록이 없으면 {@link BigDecimal#ZERO}
     */
    public BigDecimal getMonthlyTotal(Long accountId, LocalDate month) {
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).minusDays(1);

        return savingsRecordRepository
                .findByUserAndExactAccountAndDateRange(currentUserId(), accountId, start, end)
                .stream()
                .map(SavingsRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 현재 사용자의 기간 내 월별 저축 합계.
     *
     * @param accountId 계좌 ID (null이면 전체 계좌)
     * @return 월 라벨(yyyy-MM) → 합계. 기록이 있는 달만 담기며 오래된 달부터 정렬된다.
     */
    public Map<String, BigDecimal> getMonthlyTotals(
            Long accountId, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        savingsRecordRepository
                .findByUserAndDateRange(currentUserId(), accountId, start, end)
                .stream()
                .sorted(Comparator.comparing(SavingsRecord::getSavedDate))
                .forEach(
                        r ->
                                totals.merge(
                                        r.getSavedDate().format(MONTH_LABEL),
                                        r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO,
                                        BigDecimal::add));

        return totals;
    }

    /** 현재 사용자의 분류별 합계 (전체 기간) */
    public Map<String, BigDecimal> getTotalsByCategory() {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (SavingsRecord r :
                savingsRecordRepository.findAllByUserOrderBySavedDateDesc(currentUserId())) {
            totals.merge(
                    r.getCategory().name(),
                    r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO,
                    BigDecimal::add);
        }
        return totals;
    }

    private Long currentUserId() {
        return securityContextService.getCurrentUserId().orElse(null);
    }

    /** ID로 조회하되 현재 사용자 소유가 아니면 존재를 숨기고 404를 던진다. */
    private SavingsRecord findOwned(Long id, Long userId) {
        SavingsRecord record =
                savingsRecordRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("저축 기록을 찾을 수 없습니다: " + id));
        if (!Objects.equals(record.getUserId(), userId)) {
            throw new ResourceNotFoundException("저축 기록을 찾을 수 없습니다: " + id);
        }
        return record;
    }

    /** 계좌를 지정했다면 존재하고 현재 사용자 소유인지 확인한다. */
    private void validateAccountOwnership(Long accountId, Long userId) {
        if (accountId == null) {
            return;
        }
        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("계좌를 찾을 수 없습니다: " + accountId));
        if (account.getUserId() != null && !account.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("계좌를 찾을 수 없습니다: " + accountId);
        }
    }

    private void validate(SavingsRecordDto dto) {
        if (dto.getSavedDate() == null) {
            throw new IllegalArgumentException("저축일(savedDate)은 필수입니다");
        }
        if (dto.getAmount() == null) {
            throw new IllegalArgumentException("저축 금액(amount)은 필수입니다");
        }
        if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("저축 금액은 0보다 커야 합니다: " + dto.getAmount());
        }
    }

    private SavingsRecordDto toDto(SavingsRecord r) {
        return SavingsRecordDto.builder()
                .id(r.getId())
                .accountId(r.getAccountId())
                .savedDate(r.getSavedDate())
                .amount(r.getAmount())
                .category(r.getCategory())
                .institution(r.getInstitution())
                .memo(r.getMemo())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .categoryLabel(categoryLabel(r.getCategory()))
                .monthLabel(r.getSavedDate() != null ? r.getSavedDate().format(MONTH_LABEL) : null)
                .build();
    }

    /** 분류 한글 라벨 */
    public static String categoryLabel(SavingsCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case EMERGENCY_FUND -> "비상금";
            case REGULAR_SAVINGS -> "정기 저축";
            case INVESTMENT_TRANSFER -> "투자 이체";
            case PENSION -> "연금";
            case DEBT_REPAYMENT -> "부채 상환";
            case OTHER -> "기타";
        };
    }
}
