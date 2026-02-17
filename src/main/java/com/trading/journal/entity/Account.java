package com.trading.journal.entity;

import com.trading.journal.security.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "accounts",
        indexes = {
            @Index(name = "idx_account_name", columnList = "name"),
            @Index(name = "idx_account_type", columnList = "accountType"),
            @Index(name = "idx_account_is_default", columnList = "isDefault"),
            @Index(name = "idx_account_user_id", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "user_id")
    private Long userId;
}
