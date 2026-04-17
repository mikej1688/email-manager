package com.emailmanager.service;

import com.emailmanager.entity.Email;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-powered email classification service using OpenAI
 */
@Service
@Slf4j
public class AIClassificationService {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.enabled:false}")
    private boolean openaiEnabled;

    /**
     * Classify email importance using AI
     */
    public void classifyImportance(Email email) {
        if (!openaiEnabled || openaiApiKey == null || openaiApiKey.isEmpty()) {
            log.debug("OpenAI is not enabled, skipping AI classification");
            return;
        }

        try {
            OpenAiService service = new OpenAiService(openaiApiKey);

            String prompt = buildClassificationPrompt(email);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("gpt-3.5-turbo")
                    .messages(List.of(
                            new ChatMessage("system",
                                    "You are an email classification assistant. Classify emails into importance levels: URGENT, HIGH, NORMAL, or LOW. Respond with only the importance level and a brief reason."),
                            new ChatMessage("user", prompt)))
                    .temperature(0.3)
                    .maxTokens(150)
                    .build();

            var response = service.createChatCompletion(request).getChoices().get(0).getMessage().getContent();

            parseAIResponse(email, response);

            log.debug("AI classification completed for email: {}", email.getSubject());
        } catch (Exception e) {
            log.error("Failed to classify email with AI", e);
        }
    }

    /**
     * Build classification prompt for AI
     */
    private String buildClassificationPrompt(Email email) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Classify this email:\n\n");
        prompt.append("From: ").append(email.getFromAddress()).append("\n");
        prompt.append("Subject: ").append(email.getSubject()).append("\n");

        if (email.getBodyPlainText() != null) {
            String body = email.getBodyPlainText();
            // Limit body to 500 characters for token efficiency
            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            prompt.append("Body: ").append(body).append("\n");
        }

        prompt.append("\nDetermine the importance level (URGENT/HIGH/NORMAL/LOW) and explain why.");

        return prompt.toString();
    }

    /**
     * Parse AI response and update email
     */
    private void parseAIResponse(Email email, String response) {
        response = response.toUpperCase();

        Email.ImportanceLevel importance = Email.ImportanceLevel.NORMAL;

        if (response.contains("URGENT")) {
            importance = Email.ImportanceLevel.URGENT;
            email.setImportanceScore(1.0);
        } else if (response.contains("HIGH")) {
            importance = Email.ImportanceLevel.HIGH;
            email.setImportanceScore(0.75);
        } else if (response.contains("LOW")) {
            importance = Email.ImportanceLevel.LOW;
            email.setImportanceScore(0.25);
        } else {
            email.setImportanceScore(0.5);
        }

        email.setImportance(importance);
        email.setClassificationReason("AI: " + response.substring(0, Math.min(response.length(), 200)));
    }
}
