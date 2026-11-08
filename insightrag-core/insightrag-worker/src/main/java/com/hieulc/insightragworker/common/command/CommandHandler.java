package com.hieulc.insightragworker.common.command;

public interface CommandHandler<C> {
  void handle(C command);
}
