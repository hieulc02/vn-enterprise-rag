package com.hieulc.insightragworker.exception.appli;

public class DocumentIdNotFoundException extends EntityNotFoundException {
    public DocumentIdNotFoundException(String fileKey) {
        super(String.format("Document ID with file key '%s' could not be found!", fileKey));
    }
}
