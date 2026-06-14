package com.hieulc.insightragworker.validation;

import com.hieulc.insightragworker.config.properties.DocumentValidationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentValidationService {

    private final DocumentValidationProperties documentValidationProperties;

    public boolean isValidDocument(String contentType, double sizeInMb){
        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        boolean isSupportedFormat = documentValidationProperties.allowedContentTypes().stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(allowed -> allowed.equals(contentType.trim().toLowerCase()));

        boolean isSizeValid = sizeInMb > 0 && sizeInMb <= documentValidationProperties.maxFileSizeMb();

        return isSupportedFormat && isSizeValid;
    }
}
