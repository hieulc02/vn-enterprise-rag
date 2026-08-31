import numpy as np


def calculate_cosine_simiarity_score(
    instance_embedding: np.ndarray | list[float],
    target_embedding: np.ndarray | list[float],
) -> float:

    if target_embedding is None or instance_embedding is None:
        return 0.0

    if len(target_embedding) == 0 or len(instance_embedding) == 0:
        return 0.0

    target_array = np.asarray(target_embedding)
    instance_array = np.asarray(instance_embedding)

    dot_product = np.dot(target_array, instance_array)
    norm_target = np.linalg.norm(target_array)
    norm_instance = np.linalg.norm(instance_array)

    return float(dot_product / ((norm_target * norm_instance) + 1e-10))
