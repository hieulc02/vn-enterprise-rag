package com.hieulc.insightragretrieval.annotations;

import java.lang.annotation.*;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag("concurrency")
@ActiveProfiles("test")
public @interface LoadTest {}
