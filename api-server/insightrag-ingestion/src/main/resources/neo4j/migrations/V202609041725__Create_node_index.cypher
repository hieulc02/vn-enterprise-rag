CREATE VECTOR INDEX chunk_embedding IF NOT EXISTS
    FOR (c:DocumentChunk) ON (c.embedding)
    OPTIONS {
        indexConfig: {
            `vector.dimensions`: 1024,
            `vector.similarity_function`: 'cosine'
        }
    };

CREATE VECTOR INDEX node_embedding IF NOT EXISTS
    FOR (n:Node) ON (n.embedding)
    OPTIONS {
        indexConfig: {
            `vector.dimensions`: 1024,
            `vector.similarity_function`: 'cosine'
        }
    };

CREATE VECTOR INDEX related_to_embedding IF NOT EXISTS
    FOR ()-[r:RELATED_TO]-() ON (r.embedding)
    OPTIONS {
        indexConfig: {
            `vector.dimensions`: 1024,
            `vector.similarity_function`: 'cosine'
        }
    };

CREATE INDEX idx_observation_context IF NOT EXISTS
    FOR (o:Observation) ON (o.table_context, o.row_context, o.column_context)