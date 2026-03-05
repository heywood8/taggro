import logging
import aiohttp
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

HEADERS = {"User-Agent": "Mozilla/5.0 (compatible; TelegramNewsBot/1.0)"}


async def fetch_new_messages(session: aiohttp.ClientSession, channel: str, after_id: int) -> list[dict]:
    """Fetch messages from t.me/s/{channel} newer than after_id.

    Returns list of dicts with keys: id, text. Sorted oldest-first.
    """
    url = f"https://t.me/s/{channel}"
    try:
        async with session.get(url, headers=HEADERS, timeout=aiohttp.ClientTimeout(total=10)) as resp:
            if resp.status != 200:
                logger.warning("t.me/s/%s returned HTTP %s", channel, resp.status)
                return []
            html = await resp.text()
    except Exception as e:
        logger.error("HTTP error fetching %s: %s", channel, e)
        return []

    soup = BeautifulSoup(html, "html.parser")
    messages = []

    for el in soup.find_all("div", class_="tgme_widget_message"):
        post_attr = el.get("data-post", "")  # e.g. "channelname/123"
        try:
            msg_id = int(post_attr.split("/")[-1])
        except (ValueError, IndexError):
            continue

        if msg_id <= after_id:
            continue

        text_el = el.find("div", class_="tgme_widget_message_text")
        text = text_el.get_text(separator="\n").strip() if text_el else ""

        messages.append({"id": msg_id, "text": text})

    return sorted(messages, key=lambda m: m["id"])
