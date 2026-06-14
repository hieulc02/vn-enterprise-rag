package com.hieulc.insightragworker.repository;

import com.hieulc.insightragworker.entity.Document;

import java.util.Optional;

public interface DocumentCustomRepository {
    Optional<Boolean> upsertWithSequencerCheck(Document document);
}
