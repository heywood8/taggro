from typing import Optional


def should_forward(text: Optional[str], mode: str, keywords: list[str]) -> bool:
    """Return True if the message should be forwarded to the user."""
    if mode == "all":
        return True

    normalized_text = (text or "").lower()
    normalized_keywords = [k.lower() for k in keywords]

    if mode == "include":
        if not normalized_keywords:
            return False
        return any(kw in normalized_text for kw in normalized_keywords)

    if mode == "exclude":
        if not normalized_keywords:
            return True
        return not any(kw in normalized_text for kw in normalized_keywords)

    return False
