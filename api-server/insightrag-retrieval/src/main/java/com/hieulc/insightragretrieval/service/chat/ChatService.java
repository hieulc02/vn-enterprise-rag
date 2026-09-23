package com.hieulc.insightragretrieval.service.chat;

import com.hieulc.insightragretrieval.dto.chat.ChatRequestDto;
import jakarta.validation.Valid;

public interface ChatService {
  String chat(@Valid ChatRequestDto chatRequestDto);

  void streamChat(@Valid ChatRequestDto chatRequestDto, ChatStreamCallback callback);
}
