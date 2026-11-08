package com.hieulc.insightragretrieval.dto.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.List;

public record PreparedChatPayload(
    List<ChatMessage> messages, ChatMemory chatMemory, String userQuery, String sessionId) {}
