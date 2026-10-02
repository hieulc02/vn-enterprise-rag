package com.hieulc.insightragretrieval.service.context;

import static com.hieulc.insightragretrieval.factory.ContextDataTestFactory.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.hieulc.insightragretrieval.dto.context.*;
import org.junit.jupiter.api.Test;

class GraphContextFormatterTest {

  @Test
  void formatNodeContext_formats_graph_node_prompt_and_filter_labels() {
    DocumentNodeContext nodeContext = createDocumentNodeContext();
    String expectedPrompt =
        """
        <entity title="anchor" labels="Anchor">
            <properties>
              - value: props-anchor
            </properties>
            <relationships>
              - [anchor] -[COMPONENT_OF]-> [target]
              - [anchor] -[HAS_OBSERVATION]-> [Observation {value: 123.0}]
            </relationships>
            <source_chunks>
              <chunk doc_id="doc-test" page="1"/>
            </source_chunks>
        </entity>
        """;

    String actualPrompt = GraphContextFormatter.formatNodeContext(nodeContext);
    assertThat(actualPrompt).isEqualToNormalizingWhitespace(expectedPrompt);
  }

  @Test
  void formatChunkContext_formats_chunk_prompt() {
    String expectedPrompt =
        """
        <chunk doc_id="doc-test" page="1">
          prompt-chunk-desc
        </chunk>
        """;

    String actualPrompt =
        GraphContextFormatter.formatChunkContext(createDocumentChunkContext("prompt-chunk-desc"));

    assertThat(actualPrompt).isEqualToNormalizingWhitespace(expectedPrompt);
  }

  @Test
  void buildGraphEdgeContext_returns_formated_edge_prompt_and_filter_prop_embedding() {
    DocumentRelationshipContext relationshipContext = createRelationshipContext("source", "target");

    String expectedPrompt =
        """
      <connection>
        <relationship>[source] -[RELATED_TO: edge-desc]-> [target]</relationship>
      </connection>
    """;
    String actualPrompt = GraphContextFormatter.formatRelationshipContext(relationshipContext);
    assertThat(actualPrompt).isEqualToNormalizingWhitespace(expectedPrompt);
  }
}
