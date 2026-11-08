package com.hieulc.insightragretrieval.service.chat.cache;

import com.github.benmanes.caffeine.cache.Cache;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CaffeineChatMemoryStore implements ChatMemoryStore {

  private final Cache<Object, List<ChatMessage>> cache;

  public CaffeineChatMemoryStore(
      @Qualifier("chatSessionCache") Cache<Object, List<ChatMessage>> cache) {
    this.cache = cache;
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    List<ChatMessage> messages = cache.getIfPresent(memoryId);
    return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> list) {
    cache.put(memoryId, List.copyOf(list));
  }

  @Override
  public void deleteMessages(Object memoryId) {
    cache.invalidate(memoryId);
  }
}
