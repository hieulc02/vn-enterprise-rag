package com.hieulc.insightragworker.command;

public record DocumentUploadCommand(
        String fileKey,
        String sequenceId,
        String hashContent,
        String bucketName,
        String contentType
) {
    public DocumentUploadCommand {
        if(fileKey == null || fileKey.isBlank()){
            throw new IllegalArgumentException("File key is required");
        }
    }
}
