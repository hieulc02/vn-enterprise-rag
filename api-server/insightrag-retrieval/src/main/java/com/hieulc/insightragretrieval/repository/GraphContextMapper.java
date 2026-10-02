package com.hieulc.insightragretrieval.repository;

import com.hieulc.insightragretrieval.dto.context.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class GraphContextMapper {

  DocumentChunkContext mapToChunkContext(TypeSystem typeSystem, Record record) {
    log.debug("Chunk record: {}", record.get("chunk_id").asString());
    Value metadata = record.get("metadata");
    DocumentChunkContext.ChunkMetadata chunkMetadata =
        new DocumentChunkContext.ChunkMetadata(
            metadata.get("document_id").asString(), metadata.get("page_number").asInt());

    return new DocumentChunkContext(
        record.get("chunk_id").asString(), record.get("text").asString(), chunkMetadata);
  }

  DocumentNodeContext mapToNodeContext(TypeSystem typeSystem, Record record) {
    log.debug("Node record: {}", record.get("anchor_title").asString());
    String anchorId = record.get("anchor_id").asString();
    String anchorTitle = record.get("anchor_title").asString();

    List<String> anchorLabels = record.get("anchor_label").asList(Value::asString);
    Map<String, Object> anchorProps = record.get("anchor_props").asMap();

    DocumentNode anchorNodeRef = new DocumentNode(anchorId, anchorTitle, anchorLabels, anchorProps);

    List<DocumentChunkContext> sourceChunks = new ArrayList<>();
    Value sourceChunksValues = record.get("source_chunks");
    if (sourceChunksValues != null && !sourceChunksValues.isNull()) {
      sourceChunks =
          sourceChunksValues.asList(
              value -> {
                Value metadata = value.get("metadata");
                return new DocumentChunkContext(
                    value.get("chunk_id").asString(),
                    "",
                    new DocumentChunkContext.ChunkMetadata(
                        metadata.get("document_id").asString(),
                        metadata.get("page_number").asInt()));
              });
    }

    List<DocumentNodeContext.RelatedNodesContext> relatedEntities = new ArrayList<>();
    Value relatedEntitiesValues = record.get("related_entities");
    if (relatedEntitiesValues != null && !relatedEntitiesValues.isNull()) {
      relatedEntities =
          relatedEntitiesValues.asList(
              value -> {
                DocumentNode neighborNode =
                    new DocumentNode(
                        value.get("target_id").asString(),
                        value.get("target_title").asObject(),
                        value.get("target_labels").asList(Value::asString),
                        value.get("target_props").asMap());

                NodeEdge edge =
                    new NodeEdge(
                        value.get("rel_type").asString(),
                        value.get("rel_desc").isNull() ? "" : value.get("rel_desc").asString());

                String direction = value.get("direction").asString();
                DocumentNode source = "OUTGOING".equals(direction) ? anchorNodeRef : neighborNode;
                DocumentNode target = "OUTGOING".equals(direction) ? neighborNode : anchorNodeRef;

                return new DocumentNodeContext.RelatedNodesContext(source, edge, target);
              });
    }

    return new DocumentNodeContext(anchorNodeRef, relatedEntities, sourceChunks);
  }

  DocumentRelationshipContext mapToRelationshipContext(TypeSystem typeSystem, Record record) {
    log.debug("Relationship record: {}", record.get("edge_desc").asString());
    Value sourceMap = record.get("source_node");
    DocumentNode source =
        new DocumentNode(
            sourceMap.get("id").asString(),
            sourceMap.get("title").asString(),
            sourceMap.get("labels").asList(Value::asString),
            Map.of());

    Value targetMap = record.get("target_node");
    DocumentNode target =
        new DocumentNode(
            targetMap.get("id").asString(),
            targetMap.get("title").asString(),
            targetMap.get("labels").asList(Value::asString),
            Map.of());

    return new DocumentRelationshipContext(
        new NodeEdge(record.get("edge_type").asString(), record.get("edge_desc").asString()),
        record.get("semantic_score").asDouble(),
        source,
        target);
  }
}
