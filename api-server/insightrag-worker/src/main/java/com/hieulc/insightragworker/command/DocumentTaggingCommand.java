package com.hieulc.insightragworker.command;

public record DocumentTaggingCommand(
        String fileKey,
        String bucketName
) {
    public DocumentTaggingCommand{
        if(fileKey == null || fileKey.isBlank()){
            throw new IllegalArgumentException("File key is required");
        }
    }
}
