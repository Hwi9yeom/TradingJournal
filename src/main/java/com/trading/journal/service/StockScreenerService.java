package com.trading.journal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.journal.dto.ScreenerRequestDto;
import com.trading.journal.dto.ScreenerResultDto;
import com.trading.journal.dto.ScreenerResultDto.FilterSummary;
import com.trading.journal.dto.ScreenerResultDto.StockResult;
import com.trading.journal.entity.SavedScreen;
import com.trading.journal.entity.Sector;
import com.trading.journal.entity.StockFundamentals;
import com.trading.journal.entity.User;
import com.trading.journal.repository.SavedScreenRepository;
import com.trading.journal.repository.StockFundamentalsRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for stock screening functionality.
 *
 * <p>This service provides:
 *
 * <ul>
 *   <li>Dynamic filtering using JPA Specifications
 *   <li>Pagination and sorting support
 *   <li>Saving and loading screen criteria
 *   <li>Converting between entities and DTOs
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockScreenerService {

    private final StockFundamentalsRepository fundamentalsRepository;
    private final SavedScreenRepository savedScreenRepository;
    private final ObjectMapper objectMapper;

    /**
     * Screen stocks based on criteria.
     *
     * @param request Screening criteria
     * @return Screening results with pagination
     */
    public ScreenerResultDto screenStocks(ScreenerRequestDto request) {
        log.info("Screening stocks with filters: {}", request);

        // Build specification from request
        Specification<StockFundamentals> spec = buildSpecification(request);

        // Build pageable
        Pageable pageable = buildPageable(request);

        // Execute query
        Page<StockFundamentals> page = fundamentalsRepository.findAll(spec, pageable);

        // Convert to DTOs
        List<StockResult> results =
                page.getContent().stream().map(this::toStockResult).collect(Collectors.toList());

        // Build filter summary
        FilterSummary filterSummary = buildFilterSummary(request);

        return ScreenerResultDto.builder()
                .stocks(results)
                .totalResults((int) page.getTotalElements())
                .page(page.getNumber())
                .totalPages(page.getTotalPages())
                .appliedFilters(filterSummary)
                .build();
    }

    /**
     * Save screen criteria for later use.
     *
     * @param user User saving the screen
     * @param name Screen name
     * @param description Screen description
     * @param request Screening criteria
     * @param isPublic Whether screen is public
     * @return Saved screen entity
     */
    @Transactional
    public SavedScreen saveScreen(
            User user,
            String name,
            String description,
            ScreenerRequestDto request,
            boolean isPublic) {
        log.info("Saving screen '{}' for user {}", name, user.getId());

        // Check if screen name already exists for this user
        Optional<SavedScreen> existing =
                savedScreenRepository.findByUserIdAndName(user.getId(), name);
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                    "Screen with name '" + name + "' already exists for this user");
        }

        // Serialize criteria to JSON
        String criteriaJson;
        try {
            criteriaJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize screen criteria", e);
            throw new RuntimeException("Failed to save screen criteria", e);
        }

        SavedScreen screen =
                SavedScreen.builder()
                        .user(user)
                        .name(name)
                        .description(description)
                        .criteriaJson(criteriaJson)
                        .isPublic(isPublic)
                        .build();

        return savedScreenRepository.save(screen);
    }

    /**
     * Load saved screen by ID.
     *
     * @param screenId Screen ID
     * @return Optional containing saved screen if found
     */
    public Optional<SavedScreen> loadScreen(Long screenId) {
        log.debug("Loading screen with ID: {}", screenId);
        return savedScreenRepository.findById(screenId);
    }

    /**
     * Get all screens for a user.
     *
     * @param userId User ID
     * @return List of saved screens
     */
    public List<SavedScreen> getUserScreens(Long userId) {
        log.debug("Loading screens for user: {}", userId);
        return savedScreenRepository.findByUserId(userId);
    }

    /**
     * Get all public screens.
     *
     * @return List of public screens
     */
    public List<SavedScreen> getPublicScreens() {
        log.debug("Loading public screens");
        return savedScreenRepository.findPublicScreens();
    }

    /**
     * Delete saved screen.
     *
     * @param screenId Screen ID
     * @param userId User ID (for authorization check)
     */
    @Transactional
    public void deleteScreen(Long screenId, Long userId) {
        log.info("Deleting screen {} for user {}", screenId, userId);

        SavedScreen screen =
                savedScreenRepository
                        .findById(screenId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Screen not found: " + screenId));

        // Check authorization
        if (!screen.getUser().getId().equals(userId)) {
            throw new SecurityException("User not authorized to delete this screen");
        }

        savedScreenRepository.delete(screen);
    }

    /**
     * Parse criteria JSON from saved screen.
     *
     * @param screen Saved screen
     * @return Parsed screening criteria
     */
    public ScreenerRequestDto parseCriteria(SavedScreen screen) {
        try {
            return objectMapper.readValue(screen.getCriteriaJson(), ScreenerRequestDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse screen criteria for screen: {}", screen.getId(), e);
            throw new RuntimeException("Failed to parse screen criteria", e);
        }
    }

    /**
     * Run saved screen and get results.
     *
     * @param screenId Screen ID
     * @return Screening results
     */
    public ScreenerResultDto runSavedScreen(Long screenId) {
        log.info("Running saved screen: {}", screenId);

        SavedScreen screen =
                savedScreenRepository
                        .findById(screenId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Screen not found: " + screenId));

        ScreenerRequestDto request = parseCriteria(screen);
        return screenStocks(request);
    }

    /**
     * Build JPA Specification from screening criteria.
     *
     * @param request Screening criteria
     * @return JPA Specification
     */
    private Specification<StockFundamentals> buildSpecification(ScreenerRequestDto request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Valuation filters
            if (request.getMinPe() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("peRatio"), request.getMinPe()));
            }
            if (request.getMaxPe() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("peRatio"), request.getMaxPe()));
            }
            if (request.getMinPb() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("pbRatio"), request.getMinPb()));
            }
            if (request.getMaxPb() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("pbRatio"), request.getMaxPb()));
            }
            if (request.getMinPeg() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("pegRatio"), request.getMinPeg()));
            }
            if (request.getMaxPeg() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("pegRatio"), request.getMaxPeg()));
            }

            // Profitability filters
            if (request.getMinRoe() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("returnOnEquity"), request.getMinRoe()));
            }
            if (request.getMaxRoe() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("returnOnEquity"), request.getMaxRoe()));
            }
            if (request.getMinRoa() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("returnOnAssets"), request.getMinRoa()));
            }
            if (request.getMinProfitMargin() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("profitMargin"), request.getMinProfitMargin()));
            }

            // Dividend filters
            if (request.getMinDividendYield() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("dividendYield"), request.getMinDividendYield()));
            }
            if (request.getMaxDividendYield() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("dividendYield"), request.getMaxDividendYield()));
            }

            // Size filters
            if (request.getMinMarketCap() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(root.get("marketCap"), request.getMinMarketCap()));
            }
            if (request.getMaxMarketCap() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(root.get("marketCap"), request.getMaxMarketCap()));
            }

            // Growth filters
            if (request.getMinRevenueGrowth() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("revenueGrowth"), request.getMinRevenueGrowth()));
            }
            if (request.getMinEarningsGrowth() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("epsGrowth"), request.getMinEarningsGrowth()));
            }

            // Debt filters
            if (request.getMaxDebtToEquity() != null) {
                predicates.add(
                        cb.lessThanOrEqualTo(
                                root.get("debtToEquity"), request.getMaxDebtToEquity()));
            }
            if (request.getMinCurrentRatio() != null) {
                predicates.add(
                        cb.greaterThanOrEqualTo(
                                root.get("currentRatio"), request.getMinCurrentRatio()));
            }

            // Category filters
            if (request.getSectors() != null && !request.getSectors().isEmpty()) {
                List<Sector> sectors =
                        request.getSectors().stream()
                                .map(Sector::valueOf)
                                .collect(Collectors.toList());
                predicates.add(root.get("sector").in(sectors));
            }
            if (request.getIndustries() != null && !request.getIndustries().isEmpty()) {
                predicates.add(root.get("industry").in(request.getIndustries()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Build Pageable from request.
     *
     * @param request Screening criteria with pagination info
     * @return Pageable
     */
    private Pageable buildPageable(ScreenerRequestDto request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 50;

        // Build sort
        Sort sort = Sort.unsorted();
        if (request.getSortBy() != null && !request.getSortBy().isEmpty()) {
            Sort.Direction direction =
                    "desc".equalsIgnoreCase(request.getSortDirection())
                            ? Sort.Direction.DESC
                            : Sort.Direction.ASC;
            sort = Sort.by(direction, request.getSortBy());
        } else {
            // Default sort by market cap descending
            sort = Sort.by(Sort.Direction.DESC, "marketCap");
        }

        return PageRequest.of(page, size, sort);
    }

    /**
     * Convert StockFundamentals entity to StockResult DTO.
     *
     * @param fundamentals Stock fundamentals entity
     * @return StockResult DTO
     */
    private StockResult toStockResult(StockFundamentals fundamentals) {
        return StockResult.builder()
                .id(fundamentals.getId())
                .symbol(fundamentals.getSymbol())
                .companyName(fundamentals.getCompanyName())
                .exchange(inferExchange(fundamentals.getSymbol()))
                .sector(fundamentals.getSector() != null ? fundamentals.getSector().name() : null)
                .industry(fundamentals.getIndustry())
                .peRatio(fundamentals.getPeRatio())
                .pbRatio(fundamentals.getPbRatio())
                .roe(fundamentals.getReturnOnEquity())
                .dividendYield(fundamentals.getDividendYield())
                .marketCap(fundamentals.getMarketCap())
                .revenueGrowth(fundamentals.getRevenueGrowth())
                .earningsGrowth(fundamentals.getEpsGrowth())
                .debtToEquity(fundamentals.getDebtToEquity())
                .updatedAt(
                        fundamentals.getLastUpdated() != null
                                ? fundamentals.getLastUpdated().atStartOfDay()
                                : null)
                .build();
    }

    private String inferExchange(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        if (symbol.endsWith(".KS") || symbol.endsWith(".KQ")) {
            return "KRX";
        }
        return "US";
    }

    /**
     * Build filter summary from request.
     *
     * @param request Screening criteria
     * @return Filter summary
     */
    private FilterSummary buildFilterSummary(ScreenerRequestDto request) {
        List<String> activeFilters = new ArrayList<>();

        // Count and describe active filters
        if (request.getMinPe() != null || request.getMaxPe() != null) {
            activeFilters.add("P/E Ratio");
        }
        if (request.getMinPb() != null || request.getMaxPb() != null) {
            activeFilters.add("P/B Ratio");
        }
        if (request.getMinPeg() != null || request.getMaxPeg() != null) {
            activeFilters.add("PEG Ratio");
        }
        if (request.getMinRoe() != null || request.getMaxRoe() != null) {
            activeFilters.add("ROE");
        }
        if (request.getMinRoa() != null) {
            activeFilters.add("ROA");
        }
        if (request.getMinProfitMargin() != null) {
            activeFilters.add("Profit Margin");
        }
        if (request.getMinDividendYield() != null || request.getMaxDividendYield() != null) {
            activeFilters.add("Dividend Yield");
        }
        if (request.getMinMarketCap() != null || request.getMaxMarketCap() != null) {
            activeFilters.add("Market Cap");
        }
        if (request.getMinRevenueGrowth() != null) {
            activeFilters.add("Revenue Growth");
        }
        if (request.getMinEarningsGrowth() != null) {
            activeFilters.add("Earnings Growth");
        }
        if (request.getMaxDebtToEquity() != null) {
            activeFilters.add("Debt/Equity");
        }
        if (request.getMinCurrentRatio() != null) {
            activeFilters.add("Current Ratio");
        }
        if (request.getSectors() != null && !request.getSectors().isEmpty()) {
            activeFilters.add("Sectors (" + request.getSectors().size() + ")");
        }
        if (request.getIndustries() != null && !request.getIndustries().isEmpty()) {
            activeFilters.add("Industries (" + request.getIndustries().size() + ")");
        }

        return FilterSummary.builder()
                .filterCount(activeFilters.size())
                .activeFilters(activeFilters)
                .build();
    }
}
