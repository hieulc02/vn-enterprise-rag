package com.hieulc.insightragworker.helper;

import com.hieulc.insightragworker.infrastructure.messaging.rabbitmq.S3EventName;
import com.hieulc.insightragworker.infrastructure.messaging.rabbitmq.S3EventPayload;
import java.util.ArrayList;
import java.util.List;

public class S3EventPayloadDataTest {
  public static S3EventPayload createS3EventPayload(
      S3EventName eventName,
      String bucketName,
      String objectKey,
      Long size,
      String contentType,
      int recordNumber) {

    return S3EventPayload.builder()
        .rootEventName(eventName.getEventName())
        .rootKey(objectKey)
        .records(
            generateEventRecords(recordNumber, eventName, objectKey, size, contentType, bucketName))
        .build();
  }

  public static List<S3EventPayload.Record> generateEventRecords(
      int n,
      S3EventName eventName,
      String objectKey,
      Long size,
      String contentType,
      String bucketName) {
    List<S3EventPayload.Record> eventRecords = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      var record =
          S3EventPayload.Record.builder()
              .eventName(eventName)
              .s3(
                  S3EventPayload.S3
                      .builder()
                      .bucketObject(S3EventPayload.BucketObject.builder().name(bucketName).build())
                      .object(generateS3Object(objectKey, size, contentType))
                      .build())
              .build();
      eventRecords.add(record);
    }

    return eventRecords;
  }

  public static S3EventPayload.S3Object generateS3Object(
      String objectKey, Long size, String contentType) {
    S3EventPayload.S3Object s3Object = new S3EventPayload.S3Object();
    s3Object.setKey(objectKey);
    s3Object.setSize(size);
    s3Object.setETag("eTag-12345");
    s3Object.setContentType(contentType);
    s3Object.setSequencer("0055AED6DCD90281E2");

    return s3Object;
  }
}
