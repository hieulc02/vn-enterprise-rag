from dataclasses import dataclass
import numpy as np
from models.extraction import GraphEntity


@dataclass
class EntityInstance:
    chunk_id: str
    norm_entity: str
    entity: GraphEntity
    context: str
    embedding: np.ndarray | None = None
    norm_embedding: np.ndarray | None = None
