import asyncio
import logging
import aiohttp
from pyrogram import Client

import config
from db import Database
from filters import should_forward, remove_emojis, remove_links
from scraper import fetch_new_messages

logger = logging.getLogger(__name__)

MAX_CONSECUTIVE_FAILURES = 5
_failure_counts: dict[str, int] = {}


async def poll_once(bot: Client, db: Database, session: aiohttp.ClientSession):
    channels = await db.get_active_channels()

    for channel in channels:
        try:
            last_id = await db.get_last_seen(channel)
            channel_title, messages = await fetch_new_messages(session, channel, after_id=last_id)

            if not messages:
                _failure_counts[channel] = 0
                continue

            subscribers = await db.get_active_subscribers(channel)
            max_id = last_id

            for msg in messages:
                max_id = max(max_id, msg["id"])
                for sub in subscribers:
                    if should_forward(text=msg["text"], mode=sub["mode"], keywords=sub["keywords"]):
                        link = f"https://t.me/{channel}/{msg['id']}"
                        body = msg["text"]
                        if sub["strip_emojis"]:
                            body = remove_emojis(body)
                        if sub["strip_links"]:
                            body = remove_links(body)
                        footer = f"[{channel_title}]({link})"
                        text = f"{body}\n\n{footer}" if body else footer
                        try:
                            await bot.send_message(
                                chat_id=sub["user_id"],
                                text=text,
                                disable_web_page_preview=True,
                            )
                        except Exception as e:
                            err_name = type(e).__name__
                            if "UserIsBlocked" in err_name or "InputUserDeactivated" in err_name:
                                logger.warning("User %s blocked bot, deactivating", sub["user_id"])
                                await db.set_user_active(user_id=sub["user_id"], active=False)
                            else:
                                logger.error("Send error for user %s: %s", sub["user_id"], e)

            await db.update_last_seen(channel=channel, message_id=max_id)
            _failure_counts[channel] = 0

        except Exception as e:
            _failure_counts[channel] = _failure_counts.get(channel, 0) + 1
            logger.error("Error polling %s (%d): %s", channel, _failure_counts[channel], e)
            if _failure_counts[channel] >= MAX_CONSECUTIVE_FAILURES:
                subscribers = await db.get_active_subscribers(channel)
                for sub in subscribers:
                    try:
                        await bot.send_message(
                            sub["user_id"],
                            f"Warning: @{channel} appears to be unavailable.",
                        )
                    except Exception:
                        pass


async def run_poller(bot: Client, get_db):
    logger.info("Poller started, interval=%ss", config.POLL_INTERVAL_SECONDS)
    async with aiohttp.ClientSession() as session:
        while True:
            try:
                async with get_db() as db:
                    await poll_once(bot, db, session)
            except Exception as e:
                logger.error("Poller cycle error: %s", e)
            await asyncio.sleep(config.POLL_INTERVAL_SECONDS)
