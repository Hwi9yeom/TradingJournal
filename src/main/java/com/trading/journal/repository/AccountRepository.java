package com.trading.journal.repository;

import com.trading.journal.entity.Account;
import com.trading.journal.entity.AccountType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByIsDefaultTrue();

    List<Account> findByAccountType(AccountType accountType);

    Optional<Account> findByName(String name);

    boolean existsByName(String name);

    List<Account> findAllByOrderByIsDefaultDescCreatedAtAsc();
}
