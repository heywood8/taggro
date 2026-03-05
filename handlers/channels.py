import logging
from pyrogram import Client, filters
from pyrogram.types import Message, CallbackQuery, InlineKeyboardMarkup, InlineKeyboardButton
from pyrogram.errors import UsernameNotOccupied, UsernameInvalid, ChannelPrivate, PeerIdInvalid
import config
from db import Database

logger = logging.getLogger(__name__)


def _auth(user_id: int) -> bool:
    return user_id in config.ALLOWED_USERS


def channel_menu_keyboard(channel: str) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup([
        [InlineKeyboardButton("Filter settings", callback_data=f"settings:{channel}")],
        [InlineKeyboardButton("Remove channel", callback_data=f"remove:{channel}")],
    ])


def channels_list_keyboard(subscriptions: list[dict]) -> InlineKeyboardMarkup:
    rows = []
    for sub in subscriptions:
        rows.append([
            InlineKeyboardButton(
                f"@{sub['channel']} [{sub['mode']}]",
                callback_data=f"channel_menu:{sub['channel']}"
            )
        ])
    rows.append([InlineKeyboardButton("+ Add channel", callback_data="add_channel")])
    return InlineKeyboardMarkup(rows)


def register(app: Client, get_db):

    @app.on_message(filters.command("channels") & filters.private)
    async def channels_cmd(client: Client, message: Message):
        if not _auth(message.from_user.id):
            return
        async with get_db() as db:
            subs = await db.get_user_subscriptions(user_id=message.from_user.id)
        if not subs:
            await message.reply(
                "You have no channels yet.",
                reply_markup=InlineKeyboardMarkup([[
                    InlineKeyboardButton("+ Add channel", callback_data="add_channel")
                ]])
            )
        else:
            await message.reply(
                "Your subscribed channels:",
                reply_markup=channels_list_keyboard(subs)
            )

    @app.on_callback_query(filters.regex(r"^add_channel$"))
    async def add_channel_prompt(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        await callback.message.reply("Send me the channel username (e.g. @channelname):")
        await callback.answer()

    @app.on_message(filters.private & filters.regex(r"^@?[a-zA-Z0-9_]{5,}$"))
    async def handle_channel_input(client: Client, message: Message):
        if not _auth(message.from_user.id):
            return
        username = message.text.lstrip("@").strip()
        try:
            chat = await client.get_chat(username)
            # Reject non-channel or private chats
            if chat.type.name not in ("CHANNEL", "SUPERGROUP"):
                await message.reply("That doesn't appear to be a public channel. Please try again.")
                return
        except (UsernameNotOccupied, UsernameInvalid, ChannelPrivate, PeerIdInvalid):
            await message.reply("Channel not found or is private. Please try again.")
            return

        async with get_db() as db:
            await db.add_subscription(user_id=message.from_user.id, channel=username)

        await message.reply(
            f"Added @{username}!",
            reply_markup=channel_menu_keyboard(username)
        )

    @app.on_callback_query(filters.regex(r"^channel_menu:(.+)$"))
    async def channel_menu(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        channel = callback.matches[0].group(1)
        await callback.message.edit_reply_markup(channel_menu_keyboard(channel))
        await callback.answer()

    @app.on_callback_query(filters.regex(r"^remove:(.+)$"))
    async def remove_channel(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        channel = callback.matches[0].group(1)
        async with get_db() as db:
            await db.remove_subscription(user_id=callback.from_user.id, channel=channel)
        await callback.message.edit_text(f"Removed @{channel}.")
        await callback.answer()

    @app.on_message(filters.private & filters.reply & filters.text)
    async def handle_keyword_input(client: Client, message: Message):
        if not _auth(message.from_user.id):
            return
        reply_text = message.reply_to_message.text or ""
        if "keyword to add for @" not in reply_text:
            return
        # Extract channel from the prompt message
        try:
            channel = reply_text.split("@")[1].strip().rstrip(":")
        except IndexError:
            return

        keyword = message.text.strip().lower()
        if not keyword:
            return

        async with get_db() as db:
            await db.add_keyword(user_id=message.from_user.id, channel=channel, keyword=keyword)
            keywords = await db.get_keywords(user_id=message.from_user.id, channel=channel)
            subs = await db.get_user_subscriptions(user_id=message.from_user.id)
            sub = next((s for s in subs if s["channel"] == channel), None)

        if sub:
            from handlers.settings import settings_keyboard
            await message.reply(
                f"Added keyword '{keyword}' for @{channel}.\n"
                f"Mode: {sub['mode']}\nKeywords: {', '.join(keywords)}",
                reply_markup=settings_keyboard(channel, sub["mode"], keywords)
            )
