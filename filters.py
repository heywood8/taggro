import re
from typing import Optional

_EMOJI_RE = re.compile(
    "[\U0001F600-\U0001F64F"
    "\U0001F300-\U0001F5FF"
    "\U0001F680-\U0001F6FF"
    "\U0001F1E0-\U0001F1FF"
    "\U00002702-\U000027B0"
    "\U000024C2-\U0001F251"
    "\U0001F926-\U0001F937"
    "\U00010000-\U0010FFFF"
    "\u2640-\u2642"
    "\u2600-\u2B55"
    "\u200d\u23cf\u23e9\u231a\ufe0f\u3030]+",
    re.UNICODE,
)


def remove_emojis(text: str) -> str:
    return _EMOJI_RE.sub("", text).strip()


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
