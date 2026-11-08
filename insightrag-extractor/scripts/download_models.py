import os
from docling.document_converter import DocumentConverter
from sentence_transformers import SentenceTransformer


def pre_fetch():
    print("Downloading Docling models")
    DocumentConverter()

    embedding_model = os.getenv("MODEL_NAME", "AITeamVN/Vietnamese_Embedding")
    print(f"Downloading Sentence-Transformer: {embedding_model}")
    SentenceTransformer(embedding_model)


if __name__ == "__main__":
    pre_fetch()
