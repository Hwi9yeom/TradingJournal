package com.trading.journal.service;

import com.trading.journal.dto.AccountRiskSettingsDto;
import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountRiskSettings;
import com.trading.journal.repository.AccountRiskSettingsRepository;
import com.trading.journal.repository.PortfolioRepository;
import com.trading.journal.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AccountRiskSettingsService {

    private final AccountRiskSettingsRepository riskSettingsRepository;
    private final AccountService accountService;
    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;

    /** 계정 리스크 설정 조회 (없으면 기본값으로 생성) */
    @Transactional(readOnly = true)
    public AccountRiskSettingsDto getRiskSettings(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettings settings =
                riskSettingsRepository.findByAccountIdWithAccount(targetAccountId).orElse(null);

        if (settings == null) {
            // 기본값으로 DTO 반환 (DB에는 저장하지 않음)
            Account account = accountService.getAccountEntity(targetAccountId);
            return createDefaultDto(account);
        }

        return convertToDto(settings);
    }

    /** 리스크 설정 생성 */
    public AccountRiskSettingsDto createRiskSettings(AccountRiskSettingsDto dto) {
        Long accountId = dto.getAccountId();
        if (accountId == null) {
            accountId = accountService.getDefaultAccount().getId();
        }

        if (riskSettingsRepository.existsByAccountId(accountId)) {
            throw new RuntimeException("Risk settings already exist for account: " + accountId);
        }

        Account account = accountService.getAccountEntity(accountId);

        AccountRiskSettings settings =
                AccountRiskSettings.builder()
                        .account(account)
                        .maxRiskPerTradePercent(dto.getMaxRiskPerTradePercent())
                        .maxDailyLossPercent(dto.getMaxDailyLossPercent())
                        .maxWeeklyLossPercent(dto.getMaxWeeklyLossPercent())
                        .maxOpenPositions(dto.getMaxOpenPositions())
                        .maxPositionSizePercent(dto.getMaxPositionSizePercent())
                        .maxSectorConcentrationPercent(dto.getMaxSectorConcentrationPercent())
                        .maxStockConcentrationPercent(dto.getMaxStockConcentrationPercent())
                        .accountCapital(dto.getAccountCapital())
                        .kellyFraction(dto.getKellyFraction())
                        .dailyLossAlertEnabled(dto.getDailyLossAlertEnabled())
                        .concentrationAlertEnabled(dto.getConcentrationAlertEnabled())
                        .build();

        settings = riskSettingsRepository.save(settings);
        log.info("Created risk settings for account: {}", accountId);

        return convertToDto(settings);
    }

    /** 리스크 설정 업데이트 */
    public AccountRiskSettingsDto updateRiskSettings(Long accountId, AccountRiskSettingsDto dto) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettings settings =
                riskSettingsRepository
                        .findByAccountId(targetAccountId)
                        .orElseGet(
                                () -> {
                                    // 설정이 없으면 새로 생성
                                    Account account =
                                            accountService.getAccountEntity(targetAccountId);
                                    return AccountRiskSettings.builder().account(account).build();
                                });

        settings.setMaxRiskPerTradePercent(dto.getMaxRiskPerTradePercent());
        settings.setMaxDailyLossPercent(dto.getMaxDailyLossPercent());
        settings.setMaxWeeklyLossPercent(dto.getMaxWeeklyLossPercent());
        settings.setMaxOpenPositions(dto.getMaxOpenPositions());
        settings.setMaxPositionSizePercent(dto.getMaxPositionSizePercent());
        settings.setMaxSectorConcentrationPercent(dto.getMaxSectorConcentrationPercent());
        settings.setMaxStockConcentrationPercent(dto.getMaxStockConcentrationPercent());
        settings.setAccountCapital(dto.getAccountCapital());
        settings.setKellyFraction(dto.getKellyFraction());
        settings.setDailyLossAlertEnabled(dto.getDailyLossAlertEnabled());
        settings.setConcentrationAlertEnabled(dto.getConcentrationAlertEnabled());

        settings = riskSettingsRepository.save(settings);
        log.info("Updated risk settings for account: {}", targetAccountId);

        return convertToDto(settings);
    }

    /** 계좌 자본금 업데이트 */
    public AccountRiskSettingsDto updateAccountCapital(Long accountId, BigDecimal capital) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettings settings =
                riskSettingsRepository
                        .findByAccountId(targetAccountId)
                        .orElseGet(
                                () -> {
                                    Account account =
                                            accountService.getAccountEntity(targetAccountId);
                                    return AccountRiskSettings.builder().account(account).build();
                                });

        settings.setAccountCapital(capital);
        settings = riskSettingsRepository.save(settings);

        log.info("Updated account capital for account {}: {}", targetAccountId, capital);

        return convertToDto(settings);
    }

    /** 일일 손실 한도 체크 */
    @Transactional(readOnly = true)
    public boolean isDailyLossLimitBreached(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettings settings =
                riskSettingsRepository.findByAccountId(targetAccountId).orElse(null);
        if (settings == null
                || settings.getAccountCapital() == null
                || settings.getAccountCapital().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal todayPnl = getTodayPnl(targetAccountId);
        BigDecimal maxLoss =
                settings.getAccountCapital()
                        .multiply(settings.getMaxDailyLossPercent())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        .negate();

        return todayPnl.compareTo(maxLoss) < 0;
    }

    /** 주간 손실 한도 체크 */
    @Transactional(readOnly = true)
    public boolean isWeeklyLossLimitBreached(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettings settings =
                riskSettingsRepository.findByAccountId(targetAccountId).orElse(null);
        if (settings == null
                || settings.getAccountCapital() == null
                || settings.getAccountCapital().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal weekPnl = getWeekPnl(targetAccountId);
        BigDecimal maxLoss =
                settings.getAccountCapital()
                        .multiply(settings.getMaxWeeklyLossPercent())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        .negate();

        return weekPnl.compareTo(maxLoss) < 0;
    }

    /** 오픈 포지션 수 한도 체크 */
    @Transactional(readOnly = true)
    public boolean isPositionCountLimitBreached(Long accountId) {
        Long targetAccountId = resolveAccountId(accountId);

        AccountRiskSettings settings =
                riskSettingsRepository.findByAccountId(targetAccountId).orElse(null);
        if (settings == null) {
            return false;
        }

        int currentPositions = portfolioRepository.countByAccountId(targetAccountId);
        return currentPositions >= settings.getMaxOpenPositions();
    }

    /** 오늘 실현 손익 조회 */
    @Transactional(readOnly = true)
    public BigDecimal getTodayPnl(Long accountId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        return transactionRepository.sumRealizedPnlByAccountAndDateRange(
                accountId, startOfDay, endOfDay);
    }

    /** 이번 주 실현 손익 조회 */
    @Transactional(readOnly = true)
    public BigDecimal getWeekPnl(Long accountId) {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime startDateTime = startOfWeek.atStartOfDay();
        LocalDateTime endDateTime = today.atTime(LocalTime.MAX);

        return transactionRepository.sumRealizedPnlByAccountAndDateRange(
                accountId, startDateTime, endDateTime);
    }

    /** 현재 오픈 포지션 수 조회 */
    @Transactional(readOnly = true)
    public int getCurrentOpenPositions(Long accountId) {
        return portfolioRepository.countByAccountId(accountId);
    }

    private Long resolveAccountId(Long accountId) {
        if (accountId == null) {
            return accountService.getDefaultAccount().getId();
        }
        return accountId;
    }

    private AccountRiskSettingsDto createDefaultDto(Account account) {
        return AccountRiskSettingsDto.builder()
                .accountId(account.getId())
                .accountName(account.getName())
                .maxRiskPerTradePercent(new BigDecimal("2.00"))
                .maxDailyLossPercent(new BigDecimal("6.00"))
                .maxWeeklyLossPercent(new BigDecimal("10.00"))
                .maxOpenPositions(10)
                .maxPositionSizePercent(new BigDecimal("20.00"))
                .maxSectorConcentrationPercent(new BigDecimal("30.00"))
                .maxStockConcentrationPercent(new BigDecimal("15.00"))
                .kellyFraction(new BigDecimal("0.50"))
                .dailyLossAlertEnabled(true)
                .concentrationAlertEnabled(true)
                .currentDayPnl(getTodayPnl(account.getId()))
                .currentWeekPnl(getWeekPnl(account.getId()))
                .currentOpenPositions(getCurrentOpenPositions(account.getId()))
                .isDailyLimitBreached(false)
                .isWeeklyLimitBreached(false)
                .isPositionLimitBreached(false)
                .build();
    }

    private AccountRiskSettingsDto convertToDto(AccountRiskSettings settings) {
        Long accountId = settings.getAccount().getId();

        AccountRiskSettingsDto.AccountRiskSettingsDtoBuilder builder =
                AccountRiskSettingsDto.builder()
                        .id(settings.getId())
                        .accountId(accountId)
                        .accountName(settings.getAccount().getName())
                        .maxRiskPerTradePercent(settings.getMaxRiskPerTradePercent())
                        .maxDailyLossPercent(settings.getMaxDailyLossPercent())
                        .maxWeeklyLossPercent(settings.getMaxWeeklyLossPercent())
                        .maxOpenPositions(settings.getMaxOpenPositions())
                        .maxPositionSizePercent(settings.getMaxPositionSizePercent())
                        .maxSectorConcentrationPercent(settings.getMaxSectorConcentrationPercent())
                        .maxStockConcentrationPercent(settings.getMaxStockConcentrationPercent())
                        .accountCapital(settings.getAccountCapital())
                        .kellyFraction(settings.getKellyFraction())
                        .dailyLossAlertEnabled(settings.getDailyLossAlertEnabled())
                        .concentrationAlertEnabled(settings.getConcentrationAlertEnabled())
                        .createdAt(settings.getCreatedAt())
                        .updatedAt(settings.getUpdatedAt());

        // 현재 상태 계산
        builder.currentDayPnl(getTodayPnl(accountId))
                .currentWeekPnl(getWeekPnl(accountId))
                .currentOpenPositions(getCurrentOpenPositions(accountId))
                .isDailyLimitBreached(isDailyLossLimitBreached(accountId))
                .isWeeklyLimitBreached(isWeeklyLossLimitBreached(accountId))
                .isPositionLimitBreached(isPositionCountLimitBreached(accountId));

        return builder.build();
    }
}
