package com.hieulc.insightragworker.outbox.enums;

public enum EventType {
  NEW,
  UPDATED,
  DELETED;

  public static EventType isNewOrUpdated(boolean isNewDocument) {
    return isNewDocument ? NEW : UPDATED;
  }
}
