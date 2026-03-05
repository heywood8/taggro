import asyncio
import logging
from pyrogram import Client
from pyrogram.errors import FloodWait, ChannelInvalid, UsernameNotOccupied

import config
from db import Database
from filters import should_forward

logger = logging.getLogger(__name__)

MAX_CONSECUTIVE_FAILURES = 5
_failure_counts: dict[str, int] = {}


async def poll_once(client: Client, db: Database):
    channels = await db.get_active_channels()

    for channel in channels:
        try:
            last_id = await db.get_last_seen(channel)
            new_messages = []
            async for msg in client.get_chat_history(channel, limit=50):
                if msg.id <= last_id:
                    break
                new_messages.append(msg)

            if not new_messages:
                _failure_counts[channel] = 0
                continue

            # Process oldest first
            new_messages.reverse()

            subscribers = await db.get_active_subscribers(channel)
            max_id = last_id

            for msg in new_messages:
                max_id = max(max_id, msg.id)
                text = msg.text or msg.caption or ""
                for sub in subscribers:
                    if should_forward(text=text, mode=sub["mode"], keywords=sub["keywords"]):
                        try:
                            await client.forward_messages(
                                chat_id=sub["user_id"],
                                from_chat_id=channel,
                                message_ids=msg.id,
                            )
                        except Exception as fwd_err:
                            # User likely blocked the bot
                            err_name = type(fwd_err).__name__
                            if "UserIsBlocked" in err_name or "InputUserDeactivated" in err_name:
                                logger.warning("User %s blocked bot, deactivating", sub["user_id"])
                                await db.set_user_active(user_id=sub["user_id"], active=False)
                            else:
                                logger.error("Forward error for user %s: %s", sub["user_id"], fwd_err)

            await db.update_last_seen(channel=channel, message_id=max_id)
            _failure_counts[channel] = 0

        except FloodWait as e:
            logger.warning("FloodWait for %s: sleeping %ss", channel, e.value)
            await asyncio.sleep(e.value)

        except (ChannelInvalid, UsernameNotOccupied) as e:
            _failure_counts[channel] = _failure_counts.get(channel, 0) + 1
            logger.error("Channel %s error (%d): %s", channel, _failure_counts[channel], e)
            if _failure_counts[channel] >= MAX_CONSECUTIVE_FAILURES:
                subscribers = await db.get_active_subscribers(channel)
                for sub in subscribers:
                    try:
                        await client.send_message(
                            sub["user_id"],
                            f"Warning: @{channel} appears to be unavailable and may have been deleted or made private."
                        )
                    except Exception:
                        pass

        except Exception as e:
            _failure_counts[channel] = _failure_counts.get(channel, 0) + 1
            logger.error("Unexpected error polling %s: %s", channel, e)


async def run_poller(client: Client, get_db):
    logger.info("Poller started, interval=%ss", config.POLL_INTERVAL_SECONDS)
    while True:
        try:
            async with get_db() as db:
                await poll_once(client, db)
        except Exception as e:
            logger.error("Poller cycle error: %s", e)
        await asyncio.sleep(config.POLL_INTERVAL_SECONDS)
