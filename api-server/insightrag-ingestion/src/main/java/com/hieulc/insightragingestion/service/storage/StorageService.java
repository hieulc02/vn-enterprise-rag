package com.hieulc.insightragingestion.service.storage;

import java.io.InputStream;

public interface StorageService {
    InputStream download(String bucket, String key);
}
