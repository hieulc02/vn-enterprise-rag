package com.hieulc.insightragworker.command;

public record DocumentRemoveCommand(
        String fileKey
) {
    public DocumentRemoveCommand {
        if(fileKey == null || fileKey.isBlank()){
            throw new IllegalArgumentException("File key is required");
        }
    }
}
