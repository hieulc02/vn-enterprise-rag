package com.hieulc.insightragworker.document.port;

import com.hieulc.insightragworker.document.model.Document;
import com.hieulc.insightragworker.document.model.DocumentDeleteResult;
import java.util.Optional;

public interface DocumentCustomRepository {
  Optional<Boolean> upsertWithSequencerCheck(Document document);

  Optional<DocumentDeleteResult> updateSoftDeleteDocument(String fileKey, String sequencer);

  Optional<String> findFileKeyByHash(String hashContent, String currentFileKey);
}
