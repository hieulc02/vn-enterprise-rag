package com.hieulc.insightragworker.document.exception;

import com.hieulc.insightragworker.common.exception.EntityNotFoundException;

public class DocumentIdNotFoundException extends EntityNotFoundException {
  public DocumentIdNotFoundException(String fileKey) {
    super(String.format("Document ID with file key '%s' could not be found!", fileKey));
  }
}
