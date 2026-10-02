package com.hieulc.insightragretrieval.service.context;

import com.hieulc.insightragretrieval.dto.context.*;
import java.util.*;
import java.util.stream.Collectors;

final class GraphContextFormatter {

  private static final Set<String> DEFAULT_LABELS = Set.of("Node", "Entity");

  private GraphContextFormatter() {}

  public static String formatNodeContext(DocumentNodeContext nodeContext) {
    StringBuilder context = new StringBuilder();
    var anchor = nodeContext.documentNode();
    context.append(
        String.format(
            "   <entity title=\"%s\" labels=\"%s\">\n",
            anchor.title(), formatLabel(anchor.labels())));
    context.append("      <properties>\n");

    if (anchor.properties() != null) {
      for (Map.Entry<String, Object> entry : anchor.properties().entrySet()) {
        if ((entry.getValue() instanceof List valueList && valueList.isEmpty())
            || (entry.getValue() instanceof String valueString && valueString.isEmpty())) {
          continue;
        }

        if (entry.getValue() != null) {
          context.append(String.format("        - %s: %s\n", entry.getKey(), entry.getValue()));
        }
      }
    }
    context.append("      </properties>\n");
    if (nodeContext.relatedEntities() != null && !nodeContext.relatedEntities().isEmpty()) {
      context.append("      <relationships>\n");
      for (DocumentNodeContext.RelatedNodesContext rel : nodeContext.relatedEntities()) {
        String source = resolveNode(rel.source());
        String target = resolveNode(rel.target());
        String edgeType = resolveEdge(rel.edge());
        context.append(String.format("        - [%s] -[%s]-> [%s]\n", source, edgeType, target));
      }
      context.append("      </relationships>\n");
    } else {
      context.append("      <relationships/>\n");
    }

    context.append("      <source_chunks>\n");
    if (nodeContext.sourceChunks() != null && !nodeContext.sourceChunks().isEmpty()) {
      nodeContext.sourceChunks().stream()
          .map(DocumentChunkContext::metadata)
          .filter(Objects::nonNull)
          .distinct()
          .sorted(
              Comparator.comparingInt(DocumentChunkContext.ChunkMetadata::pageNumber).reversed())
          .map(
              metadata ->
                  String.format(
                      "       <chunk doc_id=\"%s\" page=\"%s\"/>\n",
                      metadata.documentId(), metadata.pageNumber()))
          .forEach(context::append);
    }
    context.append("      </source_chunks>\n");

    context.append("    </entity>\n");
    return context.toString();
  }

  public static String formatChunkContext(DocumentChunkContext chunk) {
    return String.format(
        """
              <chunk doc_id="%s" page="%s">
                %s
              </chunk>
            """,
        chunk.metadata().documentId(), chunk.metadata().pageNumber(), chunk.text());
  }

  @SuppressWarnings("StringBufferReplaceableByString")
  public static String formatRelationshipContext(DocumentRelationshipContext edgeContext) {
    StringBuilder context = new StringBuilder();

    context.append("    <connection>\n");
    context.append(
        String.format(
            "     [%s] -[%s]-> [%s]\n",
            resolveNode(edgeContext.sourceNode()),
            resolveEdge(edgeContext.nodeEdge()),
            resolveNode(edgeContext.targetNode())));
    context.append("    </connection>\n");
    return context.toString();
  }

  private static String resolveNode(DocumentNode node) {
    if (node.title() != null && node.title() instanceof String titleStr) {
      return titleStr;
    }

    if (node.properties() != null && !node.properties().isEmpty()) {
      String inlineProps =
          node.properties().entrySet().stream()
              .filter(entry -> entry.getValue() != null)
              .map(entry -> entry.getKey() + ": " + entry.getValue())
              .collect(Collectors.joining(", "));

      if (!inlineProps.isEmpty()) {
        return String.format("%s {%s}", formatLabel(node.labels()), inlineProps);
      }
    }

    return "AnonymousNode";
  }

  private static String resolveEdge(NodeEdge node) {
    StringBuilder context = new StringBuilder();
    if (node.edgeType() != null && !node.edgeType().isEmpty()) {
      context.append(node.edgeType());
    }

    if (node.edgeDesc() != null && !node.edgeDesc().isEmpty()) {
      context.append(String.format(": %s", node.edgeDesc()));
    }
    return context.toString();
  }

  private static String formatLabel(List<String> labels) {
    return labels.stream()
        .filter(l -> !DEFAULT_LABELS.contains(l))
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.joining(","));
  }
}
