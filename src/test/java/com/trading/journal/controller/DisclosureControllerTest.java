package com.trading.journal.controller;

import com.trading.journal.dto.DisclosureDto;
import com.trading.journal.dto.DisclosureSummaryDto;
import com.trading.journal.service.DisclosureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DisclosureController.class)
class DisclosureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisclosureService disclosureService;

    private DisclosureDto mockDisclosureDto;
    private DisclosureSummaryDto mockSummaryDto;

    @BeforeEach
    void setUp() {
        mockDisclosureDto = DisclosureDto.builder()
                .id(1L)
                .stockSymbol("AAPL")
                .reportName("분기보고서")
                .receivedDate(LocalDateTime.now())
                .isRead(false)
                .isImportant(false)
                .build();

        mockSummaryDto = DisclosureSummaryDto.builder()
                .totalCount(10L)
                .unreadCount(5L)
                .importantCount(2L)
                .build();
    }

    @Test
    @DisplayName("포트폴리오 공시 요약 조회")
    void getPortfolioDisclosureSummary() throws Exception {
        // Given
        when(disclosureService.getPortfolioDisclosureSummary()).thenReturn(mockSummaryDto);

        // When & Then
        mockMvc.perform(get("/api/disclosures/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(10))
                .andExpect(jsonPath("$.unreadCount").value(5))
                .andExpect(jsonPath("$.importantCount").value(2));

        verify(disclosureService).getPortfolioDisclosureSummary();
    }

    @Test
    @DisplayName("종목별 공시 조회")
    void getDisclosuresByStock() throws Exception {
        // Given
        List<DisclosureDto> disclosures = Arrays.asList(mockDisclosureDto);
        when(disclosureService.getDisclosuresByStock("AAPL")).thenReturn(disclosures);

        // When & Then
        mockMvc.perform(get("/api/disclosures/stock/aapl"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].stockSymbol").value("AAPL"))
                .andExpect(jsonPath("$[0].reportName").value("분기보고서"))
                .andExpect(jsonPath("$[0].isRead").value(false))
                .andExpect(jsonPath("$[0].isImportant").value(false));

        verify(disclosureService).getDisclosuresByStock("AAPL");
    }

    @Test
    @DisplayName("공시 읽음 처리")
    void markAsRead() throws Exception {
        // Given
        DisclosureDto readDisclosure = DisclosureDto.builder()
                .id(1L)
                .stockSymbol("AAPL")
                .reportName("분기보고서")
                .receivedDate(LocalDateTime.now())
                .isRead(true)
                .isImportant(false)
                .build();

        when(disclosureService.markAsRead(1L)).thenReturn(readDisclosure);

        // When & Then
        mockMvc.perform(put("/api/disclosures/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isRead").value(true));

        verify(disclosureService).markAsRead(1L);
    }

    @Test
    @DisplayName("공시 중요 표시 토글")
    void toggleImportant() throws Exception {
        // Given
        DisclosureDto importantDisclosure = DisclosureDto.builder()
                .id(1L)
                .stockSymbol("AAPL")
                .reportName("분기보고서")
                .receivedDate(LocalDateTime.now())
                .isRead(false)
                .isImportant(true)
                .build();

        when(disclosureService.toggleImportant(1L)).thenReturn(importantDisclosure);

        // When & Then
        mockMvc.perform(put("/api/disclosures/1/important"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isImportant").value(true));

        verify(disclosureService).toggleImportant(1L);
    }

    @Test
    @DisplayName("종목 공시 동기화")
    void syncDisclosuresForStock() throws Exception {
        // Given
        doNothing().when(disclosureService).syncDisclosuresForStock("AAPL");

        // When & Then
        mockMvc.perform(post("/api/disclosures/sync/aapl"))
                .andExpect(status().isAccepted());

        verify(disclosureService).syncDisclosuresForStock("AAPL");
    }

    @Test
    @DisplayName("전체 포트폴리오 공시 동기화")
    void syncAllDisclosures() throws Exception {
        // Given
        doNothing().when(disclosureService).syncAllPortfolioDisclosures();

        // When & Then
        mockMvc.perform(post("/api/disclosures/sync"))
                .andExpect(status().isAccepted());

        verify(disclosureService).syncAllPortfolioDisclosures();
    }
}