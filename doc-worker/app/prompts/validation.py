HEADER_VALIDATION_PROMPT = """
    You are a document structure classifier.
    Classify given sections and return ONLY JSON object mapping the 'id' to a boolean (True=genuine header, False=fake).
    RULE:
        - Return ONLY JSON object mapping the 'id' to a boolean (True=genuine header, False=fake).
        - A genuine section header introduces a structural topic
        - Signatures, personal names, dates, and short conversational phrases are False

    Example input:
        [{
            "id": "#/texts/0",
            "text": "TITLE"
        }]
"""

TABLE_VALIDATION_PROMPT = """
    You are a document structure classifier.
    Classify table headers to determine if they are genuine structural tables or layout artifacts.

    RULE:
        - Return ONLY a valid JSON object mapping the 'id' to a boolean (true = genuine header, false = fake)
        - A genuine table header introduces a structural topic, data columns, or financial metrics
        - Signatures, personal names, dates, professional titles, administrative sign-offs are False

    Example input:
        [{
            "id": "#/table/0",
            "text": "HEADING1 HEADING2 MERGED_HEADING"
        }]
"""
