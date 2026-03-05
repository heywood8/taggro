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
    app = Client("bot", bot_token=config.BOT_TOKEN)

    start_handler.register(app, get_db)
    channels_handler.register(app, get_db)
    settings_handler.register(app, get_db)

    async with app:
        logger.info("Bot started")
        asyncio.create_task(poller.run_poller(app, get_db))
        await asyncio.Event().wait()


if __name__ == "__main__":
    asyncio.run(main())
