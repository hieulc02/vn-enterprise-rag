package com.hieulc.insightragworker.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3EventPayload {

    @JsonProperty("EventName")
    private String rootEventName;

    @JsonProperty("Key")
    private String rootKey;

    @JsonProperty("Records")
    private List<Record> records;

    public boolean isUploadEvent(){
        return records != null && !records.isEmpty()
                && records.getFirst().getEventName().startsWith("s3:ObjectCreated");
    }

    public boolean isDeleteEvent(){
        return records != null && !records.isEmpty()
                && records.getFirst().getEventName().startsWith("s3:ObjectCreated");
    }

    public String getSequencer(){
        if(records != null && !records.isEmpty()){
            return records.getFirst().getS3().getObject().getSequencer();
        }
        return null;
    }

    public String getObjectKey(){
        if(records != null && !records.isEmpty()){
            return records.getFirst().getS3().getObject().getKey();
        }
        return null;
    }

    public String getContentType(){
        if(records != null && !records.isEmpty()){
            return records.getFirst().getS3().getObject().getContentType();
        }
        return null;
    }

    public String getETag(){
        if(records != null && !records.isEmpty()){
            return records.getFirst().getS3().getObject().getETag();
        }
        return null;
    }

    public long getSize(){
        if(records != null && !records.isEmpty()){
            return records.getFirst().getS3().getObject().getSize();
        }
        return 0;
    }

    public String getBucketName(){
        if(records != null && !records.isEmpty()){
            return records.getFirst().getS3().getBucketObject().getName();
        }
        return null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Record{
        @JsonProperty("eventName")
        private String eventName;

        @JsonProperty("s3")
        private S3 s3;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3{
        @JsonProperty("bucket")
        private BucketObject bucketObject;

        @JsonProperty("object")
        private S3Object object;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BucketObject{
        @JsonProperty("name")
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class S3Object{
        @JsonProperty("key")
        String key;

        @JsonProperty("size")
        long size;

        @JsonProperty("eTag")
        String eTag;

        @JsonProperty("contentType")
        String contentType;

        @JsonProperty("sequencer")
        String sequencer;
    }


}
