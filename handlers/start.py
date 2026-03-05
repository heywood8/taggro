from pyrogram import Client, filters
from pyrogram.types import Message
import aiosqlite
import config
from db import Database


def register(app: Client, get_db: callable):

    @app.on_message(filters.command("start") & filters.private)
    async def start_handler(client: Client, message: Message):
        user_id = message.from_user.id

        # Silent rejection for unauthorized users
        if user_id not in config.ALLOWED_USERS:
            return

        async with get_db() as db:
            # Reactivate subscriptions if user had previously blocked the bot
            await db.set_user_active(user_id=user_id, active=True)

        await message.reply(
            "Welcome to the News Aggregator Bot!\n\n"
            "Use /channels to manage your channel subscriptions."
        )
