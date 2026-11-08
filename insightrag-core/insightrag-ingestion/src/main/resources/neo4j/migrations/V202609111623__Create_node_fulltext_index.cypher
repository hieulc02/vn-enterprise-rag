CREATE FULLTEXT INDEX chunk_fulltext IF NOT EXISTS
FOR (c:DocumentChunk) ON EACH [c.text];

CREATE FULLTEXT INDEX node_fulltext IF NOT EXISTS
FOR (e:Node) ON EACH [e.title, e.description, e.aliases];
