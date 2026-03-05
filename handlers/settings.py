from pyrogram import Client, filters
from pyrogram.types import CallbackQuery, InlineKeyboardMarkup, InlineKeyboardButton
import config
from db import Database


def _auth(user_id: int) -> bool:
    return user_id in config.ALLOWED_USERS


def settings_keyboard(channel: str, mode: str, keywords: list[str]) -> InlineKeyboardMarkup:
    rows = []

    # Mode selector — only show include/exclude if keywords exist
    if keywords:
        mode_options = ["all", "include", "exclude"]
    else:
        mode_options = ["all"]

    mode_row = []
    for opt in mode_options:
        label = f"[{opt}]" if mode == opt else opt
        mode_row.append(InlineKeyboardButton(label, callback_data=f"setmode:{channel}:{opt}"))
    rows.append(mode_row)

    # Keywords list
    for kw in keywords:
        rows.append([
            InlineKeyboardButton(f"x {kw}", callback_data=f"delkw:{channel}:{kw}")
        ])

    rows.append([InlineKeyboardButton("+ Add keyword", callback_data=f"addkw:{channel}")])
    rows.append([InlineKeyboardButton("<< Back", callback_data=f"channel_menu:{channel}")])
    return InlineKeyboardMarkup(rows)


def register(app: Client, get_db):

    @app.on_callback_query(filters.regex(r"^settings:(.+)$"))
    async def show_settings(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        channel = callback.matches[0].group(1)
        async with get_db() as db:
            subs = await db.get_user_subscriptions(user_id=callback.from_user.id)
            sub = next((s for s in subs if s["channel"] == channel), None)
            keywords = await db.get_keywords(user_id=callback.from_user.id, channel=channel)

        if not sub:
            await callback.answer("Subscription not found.")
            return

        await callback.message.edit_text(
            f"Settings for @{channel}\nMode: {sub['mode']}\nKeywords: {', '.join(keywords) or 'none'}",
            reply_markup=settings_keyboard(channel, sub["mode"], keywords)
        )
        await callback.answer()

    @app.on_callback_query(filters.regex(r"^setmode:([^:]+):(.+)$"))
    async def set_mode(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        channel = callback.matches[0].group(1)
        mode = callback.matches[0].group(2)
        async with get_db() as db:
            try:
                await db.set_mode(user_id=callback.from_user.id, channel=channel, mode=mode)
                keywords = await db.get_keywords(user_id=callback.from_user.id, channel=channel)
                subs = await db.get_user_subscriptions(user_id=callback.from_user.id)
                sub = next(s for s in subs if s["channel"] == channel)
            except ValueError as e:
                await callback.answer(str(e), show_alert=True)
                return

        await callback.message.edit_text(
            f"Settings for @{channel}\nMode: {sub['mode']}\nKeywords: {', '.join(keywords) or 'none'}",
            reply_markup=settings_keyboard(channel, sub["mode"], keywords)
        )
        await callback.answer()

    @app.on_callback_query(filters.regex(r"^addkw:(.+)$"))
    async def add_keyword_prompt(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        channel = callback.matches[0].group(1)
        await callback.message.reply(f"Send the keyword to add for @{channel}:")
        await callback.answer()

    @app.on_callback_query(filters.regex(r"^delkw:([^:]+):(.+)$"))
    async def delete_keyword(client: Client, callback: CallbackQuery):
        if not _auth(callback.from_user.id):
            return
        channel = callback.matches[0].group(1)
        keyword = callback.matches[0].group(2)
        async with get_db() as db:
            await db.remove_keyword(user_id=callback.from_user.id, channel=channel, keyword=keyword)
            keywords = await db.get_keywords(user_id=callback.from_user.id, channel=channel)
            subs = await db.get_user_subscriptions(user_id=callback.from_user.id)
            sub = next(s for s in subs if s["channel"] == channel)

        await callback.message.edit_text(
            f"Settings for @{channel}\nMode: {sub['mode']}\nKeywords: {', '.join(keywords) or 'none'}",
            reply_markup=settings_keyboard(channel, sub["mode"], keywords)
        )
        await callback.answer()
