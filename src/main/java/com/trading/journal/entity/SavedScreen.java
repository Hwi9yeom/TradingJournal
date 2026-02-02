package com.trading.journal.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "saved_screen",
        indexes = {
            @Index(name = "idx_saved_screen_user_id", columnList = "user_id"),
            @Index(name = "idx_saved_screen_name", columnList = "name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedScreen extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String criteriaJson;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = false;
}
