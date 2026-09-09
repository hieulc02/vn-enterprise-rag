CREATE VECTOR INDEX chunk_embedding IF NOT EXISTS
    FOR (c:DocumentChunk) ON (c.embedding)
    OPTIONS {
        indexConfig: {
            `vector.dimensions`: 1024,
            `vector.similarity_function`: 'cosine'
        }
    };

CREATE VECTOR INDEX entity_embedding IF NOT EXISTS
    FOR (e:Entity) ON (e.embedding)
    OPTIONS {
        indexConfig: {
            `vector.dimensions`: 1024,
            `vector.similarity_function`: 'cosine'
        }
    };