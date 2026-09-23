CREATE (d:Document {document_id: "test-document"}),
(c1:DocumentChunk {chunk_id: "c1", text: "Chunk text is an Object and Entity", chunk_index: 1, embedding: [x IN range(1, 1024) | 0.1], document_id: "test-document", page_number: 1}),
(c2:DocumentChunk {chunk_id: "c2", text: "Object is not a Chunk text but a Subject", chunk_index: 2, embedding: [x IN range(1, 1024) | -0.2], document_id: "test-document", page_number: 2}),
(e:Node:Entity {id: "node-1", title: "Entity-1", description: "Entity 1 Description", aliases: "Entity 1", embedding: [x IN range(1, 1024) | 0.1] }),
(sub:Node:Subject {id: "node-2", title: "Subject-1", description: "Subject Description", aliases: "Subject 1", embedding: [x IN range(1, 1024) | CASE WHEN x % 2 = 0 THEN 0.2 ELSE -0.2 END] }),
(obs:Node:Observation {id: "node-3", value: 123.0, is_numeric: TRUE }),
(c1)-[:PART_OF]->(d),
(c2)-[:PART_OF]->(d),
(e)-[:EXTRACTED_FROM]->(c1),
(sub)-[:EXTRACTED_FROM]->(c2),
(e)-[:COMPONENT_OF]->(sub),
(sub)-[r:RELATED_TO {description: "Subject is a component of Entity", embedding: [x IN range(1, 1024) | 0.1]}]->(e),
(sub)-[:HAS_OBSERVATION]->(obs);


