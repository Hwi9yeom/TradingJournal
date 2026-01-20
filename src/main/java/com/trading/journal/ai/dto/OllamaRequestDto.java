package com.trading.journal.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** Ollama API 요청 DTO https://github.com/ollama/ollama/blob/main/docs/api.md */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OllamaRequestDto {

    private String model;
    private String prompt;
    private List<ChatMessageDto> messages;
    private Boolean stream;
    private Map<String, Object> options;
    private String system;
    private String template;
    private String context;

    @JsonProperty("keep_alive")
    private String keepAlive;

    public OllamaRequestDto() {}

    public OllamaRequestDto(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
        this.stream = false;
    }

    public static OllamaRequestDto forGenerate(String model, String prompt, boolean stream) {
        OllamaRequestDto dto = new OllamaRequestDto();
        dto.setModel(model);
        dto.setPrompt(prompt);
        dto.setStream(stream);
        return dto;
    }

    public static OllamaRequestDto forChat(
            String model, List<ChatMessageDto> messages, boolean stream) {
        OllamaRequestDto dto = new OllamaRequestDto();
        dto.setModel(model);
        dto.setMessages(messages);
        dto.setStream(stream);
        return dto;
    }

    // Getters and Setters
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<ChatMessageDto> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessageDto> messages) {
        this.messages = messages;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(String keepAlive) {
        this.keepAlive = keepAlive;
    }
}
