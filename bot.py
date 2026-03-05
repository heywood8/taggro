import asyncio
import logging
import os
import aiosqlite
from pyrogram import Client
from contextlib import asynccontextmanager

import config
from db import Database
from handlers import start as start_handler
from handlers import channels as channels_handler
from handlers import settings as settings_handler
import poller

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def get_db():
    os.makedirs(os.path.dirname(config.DB_PATH), exist_ok=True)
    async with aiosqlite.connect(config.DB_PATH) as conn:
        db = Database(conn)
        await db.initialize()
        yield db


async def main():
    bot = Client("bot", api_id=config.API_ID, api_hash=config.API_HASH, bot_token=config.BOT_TOKEN)
    user = Client("user", api_id=config.API_ID, api_hash=config.API_HASH)

    start_handler.register(bot, get_db)
    channels_handler.register(bot, get_db)
    settings_handler.register(bot, get_db)

    async with bot, user:
        logger.info("Bot started")
        asyncio.create_task(poller.run_poller(user, bot, get_db))
        await asyncio.Event().wait()


if __name__ == "__main__":
    asyncio.run(main())
