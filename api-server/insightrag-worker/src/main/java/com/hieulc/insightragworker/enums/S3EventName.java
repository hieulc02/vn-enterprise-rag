package com.hieulc.insightragworker.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public enum S3EventName {

    DOCUMENT_UPLOADED("s3:ObjectCreated:Put"),
    TAGS_MODIFIED("s3:ObjectCreated:PutTagging"),
    DOCUMENT_DELETED("s3:ObjectRemoved:Delete"),

    UNSUPPORTED_EVENT("unsupported_event");

    private final String eventName;

    S3EventName(String eventName){
        this.eventName = eventName;
    }

    @JsonCreator
    public static S3EventName fromS3EventName(String s3EventName){
        if(s3EventName == null){
            return UNSUPPORTED_EVENT;
        }

        for(S3EventName event : S3EventName.values()){
            if(event.eventName.equalsIgnoreCase(s3EventName)){
                return event;
            }
        }

        log.debug("Received unmapped event type: {}. Set default event to UNSUPPORTED_EVENT", s3EventName);
        return UNSUPPORTED_EVENT;
    }
}
