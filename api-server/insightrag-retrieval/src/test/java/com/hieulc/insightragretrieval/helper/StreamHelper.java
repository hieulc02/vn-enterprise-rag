package com.hieulc.insightragretrieval.helper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

public class StreamHelper {
  public static String resourceToString(Resource resource) throws IOException {
    try (InputStream is = resource.getInputStream()) {
      return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
    }
  }
}
