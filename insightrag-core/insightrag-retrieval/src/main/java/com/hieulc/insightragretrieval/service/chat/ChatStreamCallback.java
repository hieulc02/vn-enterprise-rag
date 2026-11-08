package com.hieulc.insightragretrieval.service.chat;

public interface ChatStreamCallback {
  void onResponse(String partialResponse);

  void onComplete();

  void onError(Throwable throwable);
}
