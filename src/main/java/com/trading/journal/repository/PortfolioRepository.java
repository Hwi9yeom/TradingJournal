package com.trading.journal.repository;

import com.trading.journal.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    Optional<Portfolio> findByStockId(Long stockId);
    Optional<Portfolio> findByStockSymbol(String symbol);
}