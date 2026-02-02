package com.trading.journal.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedScreenDto {

    private Long id;
    private String name;
    private String description;
    private ScreenerRequestDto criteria;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
