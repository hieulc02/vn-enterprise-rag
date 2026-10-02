DROP INDEX related_to_embedding IF EXISTS;
DROP INDEX idx_observation_context IF EXISTS;

//"OWNS", "INVESTS_IN", "EXPLAINED_BY", "AFFECTED_BY", "HAS_VALUE", "RELATED_TO"

CYPHER 25
CREATE VECTOR INDEX semantic_embedding IF NOT EXISTS
    FOR ()-[r:OWNS|EXPLAINED_BY|AFFECTED_BY|HAS_VALUE|RELATED_TO]-() ON (r.embedding)
    OPTIONS {
        indexConfig: {
            `vector.dimensions`: 1024,
            `vector.similarity_function`: 'cosine'
        }
    };
