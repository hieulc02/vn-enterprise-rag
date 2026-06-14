package com.hieulc.insightragworker.config.properties;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@ConfigurationProperties(prefix = "insightrag.file-uploaded")
@Validated
public record DocumentValidationProperties(
        @NotEmpty Set<String> allowedContentTypes,
        @DefaultValue("50") double maxFileSizeMb,
        @DefaultValue("16")double eTagThresholdMb
        )
{}
