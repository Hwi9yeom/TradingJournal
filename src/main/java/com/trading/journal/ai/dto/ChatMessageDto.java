package com.trading.journal.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 채팅 메시지 DTO
 * Ollama chat API 형식
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageDto {

    public enum Role {
        system,
        user,
        assistant
    }

    private String role;
    private String content;

    public ChatMessageDto() {}

    public ChatMessageDto(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public ChatMessageDto(Role role, String content) {
        this.role = role.name();
        this.content = content;
    }

    public static ChatMessageDto system(String content) {
        return new ChatMessageDto(Role.system, content);
    }

    public static ChatMessageDto user(String content) {
        return new ChatMessageDto(Role.user, content);
    }

    public static ChatMessageDto assistant(String content) {
        return new ChatMessageDto(Role.assistant, content);
    }

    // Getters and Setters
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
