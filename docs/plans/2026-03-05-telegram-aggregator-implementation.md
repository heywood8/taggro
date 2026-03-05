# Telegram News Aggregator Bot Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Telegram bot that monitors multiple public channels and forwards filtered messages to whitelisted users, with per-channel keyword-based filtering configured via a button UI.

**Architecture:** Single async Python process with two concurrent tasks: a Pyrogram bot handler for user interactions and a 60-second polling loop that fetches new messages from subscribed channels, applies per-user filters, and forwards matches. State is stored in SQLite via aiosqlite.

**Tech Stack:** Python 3.12+, Pyrogram (bot token + MTProto), aiosqlite, pytest, pytest-asyncio

---

### Task 1: Project scaffold

**Files:**
- Create: `requirements.txt`
- Create: `.env.example`
- Create: `config.py`
- Create: `.gitignore`
- Create: `Dockerfile`

**Step 1: Create `.gitignore`**

```
__pycache__/
*.pyc
.env
data/
*.session
*.session-journal
```

**Step 2: Create `requirements.txt`**

```
pyrogram==2.0.106
tgcrypto==1.2.5
aiosqlite==0.20.0
pytest==8.3.5
pytest-asyncio==0.24.0
```

> Note: `tgcrypto` is required by Pyrogram for encryption. Pin versions for reproducible builds.

**Step 3: Create `.env.example`**

```
BOT_TOKEN=123456:ABC-your-bot-token-here
ALLOWED_USERS=123456789,987654321
POLL_INTERVAL_SECONDS=60
DB_PATH=data/bot.db
```

**Step 4: Create `config.py`**

```python
import os

BOT_TOKEN = os.environ["BOT_TOKEN"]
ALLOWED_USERS = set(int(uid) for uid in os.environ["ALLOWED_USERS"].split(","))
POLL_INTERVAL_SECONDS = int(os.getenv("POLL_INTERVAL_SECONDS", "60"))
DB_PATH = os.getenv("DB_PATH", "data/bot.db")
```

**Step 5: Create `Dockerfile`**

```dockerfile
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN mkdir -p data

CMD ["python", "bot.py"]
```

**Step 6: Install dependencies locally**

```bash
pip install -r requirements.txt
```

**Step 7: Commit**

```bash
git add requirements.txt .env.example config.py .gitignore Dockerfile
git commit -m "feat: project scaffold with config and dependencies"
```

---

### Task 2: Database layer

**Files:**
- Create: `db.py`
- Create: `tests/__init__.py`
- Create: `tests/test_db.py`

**Step 1: Create `tests/__init__.py`** (empty file)

**Step 2: Write failing tests in `tests/test_db.py`**

```python
import asyncio
import pytest
import aiosqlite
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from db import Database


@pytest.fixture
async def db():
    async with aiosqlite.connect(":memory:") as conn:
        database = Database(conn)
        await database.initialize()
        yield database


@pytest.mark.asyncio
async def test_add_subscription(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    subs = await db.get_user_subscriptions(user_id=1)
    assert len(subs) == 1
    assert subs[0]["channel"] == "testchannel"
    assert subs[0]["mode"] == "all"
    assert subs[0]["active"] == 1


@pytest.mark.asyncio
async def test_remove_subscription(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    await db.remove_subscription(user_id=1, channel="testchannel")
    subs = await db.get_user_subscriptions(user_id=1)
    assert len(subs) == 0


@pytest.mark.asyncio
async def test_add_keyword(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    await db.add_keyword(user_id=1, channel="testchannel", keyword="crypto")
    keywords = await db.get_keywords(user_id=1, channel="testchannel")
    assert "crypto" in keywords


@pytest.mark.asyncio
async def test_remove_keyword_resets_mode_to_all(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    await db.add_keyword(user_id=1, channel="testchannel", keyword="crypto")
    await db.set_mode(user_id=1, channel="testchannel", mode="include")
    await db.remove_keyword(user_id=1, channel="testchannel", keyword="crypto")
    subs = await db.get_user_subscriptions(user_id=1)
    assert subs[0]["mode"] == "all"


@pytest.mark.asyncio
async def test_set_mode(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    await db.add_keyword(user_id=1, channel="testchannel", keyword="crypto")
    await db.set_mode(user_id=1, channel="testchannel", mode="exclude")
    subs = await db.get_user_subscriptions(user_id=1)
    assert subs[0]["mode"] == "exclude"


@pytest.mark.asyncio
async def test_set_mode_rejected_without_keywords(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    with pytest.raises(ValueError):
        await db.set_mode(user_id=1, channel="testchannel", mode="include")


@pytest.mark.asyncio
async def test_deactivate_and_reactivate_user(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    await db.set_user_active(user_id=1, active=False)
    subs = await db.get_user_subscriptions(user_id=1)
    assert subs[0]["active"] == 0

    await db.set_user_active(user_id=1, active=True)
    subs = await db.get_user_subscriptions(user_id=1)
    assert subs[0]["active"] == 1


@pytest.mark.asyncio
async def test_last_seen(db):
    await db.update_last_seen(channel="testchannel", message_id=42)
    result = await db.get_last_seen(channel="testchannel")
    assert result == 42


@pytest.mark.asyncio
async def test_last_seen_default_zero(db):
    result = await db.get_last_seen(channel="newchannel")
    assert result == 0


@pytest.mark.asyncio
async def test_get_active_channels(db):
    await db.add_subscription(user_id=1, channel="channelA")
    await db.add_subscription(user_id=2, channel="channelB")
    await db.set_user_active(user_id=2, active=False)
    channels = await db.get_active_channels()
    assert "channelA" in channels
    assert "channelB" not in channels


@pytest.mark.asyncio
async def test_get_active_subscribers(db):
    await db.add_subscription(user_id=1, channel="testchannel")
    await db.add_subscription(user_id=2, channel="testchannel")
    await db.set_user_active(user_id=2, active=False)
    subs = await db.get_active_subscribers(channel="testchannel")
    user_ids = [s["user_id"] for s in subs]
    assert 1 in user_ids
    assert 2 not in user_ids
```

**Step 3: Run tests to verify they fail**

```bash
pytest tests/test_db.py -v
```

Expected: `ModuleNotFoundError: No module named 'db'`

**Step 4: Create `db.py`**

```python
import aiosqlite
from typing import Optional


class Database:
    def __init__(self, conn: aiosqlite.Connection):
        self.conn = conn

    async def initialize(self):
        await self.conn.executescript("""
            CREATE TABLE IF NOT EXISTS subscriptions (
                user_id  INTEGER NOT NULL,
                channel  TEXT    NOT NULL,
                mode     TEXT    NOT NULL DEFAULT 'all',
                active   INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (user_id, channel)
            );

            CREATE TABLE IF NOT EXISTS keywords (
                user_id  INTEGER NOT NULL,
                channel  TEXT    NOT NULL,
                keyword  TEXT    NOT NULL,
                PRIMARY KEY (user_id, channel, keyword)
            );

            CREATE TABLE IF NOT EXISTS last_seen (
                channel     TEXT    PRIMARY KEY,
                message_id  INTEGER NOT NULL DEFAULT 0
            );
        """)
        await self.conn.commit()

    async def add_subscription(self, user_id: int, channel: str):
        await self.conn.execute(
            "INSERT OR IGNORE INTO subscriptions (user_id, channel) VALUES (?, ?)",
            (user_id, channel),
        )
        await self.conn.execute(
            "INSERT OR IGNORE INTO last_seen (channel, message_id) VALUES (?, 0)",
            (channel,),
        )
        await self.conn.commit()

    async def remove_subscription(self, user_id: int, channel: str):
        await self.conn.execute(
            "DELETE FROM subscriptions WHERE user_id = ? AND channel = ?",
            (user_id, channel),
        )
        await self.conn.execute(
            "DELETE FROM keywords WHERE user_id = ? AND channel = ?",
            (user_id, channel),
        )
        await self.conn.commit()

    async def get_user_subscriptions(self, user_id: int) -> list[dict]:
        async with self.conn.execute(
            "SELECT channel, mode, active FROM subscriptions WHERE user_id = ?",
            (user_id,),
        ) as cursor:
            rows = await cursor.fetchall()
        return [{"channel": r[0], "mode": r[1], "active": r[2]} for r in rows]

    async def add_keyword(self, user_id: int, channel: str, keyword: str):
        await self.conn.execute(
            "INSERT OR IGNORE INTO keywords (user_id, channel, keyword) VALUES (?, ?, ?)",
            (user_id, channel, keyword.lower()),
        )
        await self.conn.commit()

    async def remove_keyword(self, user_id: int, channel: str, keyword: str):
        await self.conn.execute(
            "DELETE FROM keywords WHERE user_id = ? AND channel = ? AND keyword = ?",
            (user_id, channel, keyword.lower()),
        )
        # If no keywords remain, reset mode to 'all'
        async with self.conn.execute(
            "SELECT COUNT(*) FROM keywords WHERE user_id = ? AND channel = ?",
            (user_id, channel),
        ) as cursor:
            (count,) = await cursor.fetchone()
        if count == 0:
            await self.conn.execute(
                "UPDATE subscriptions SET mode = 'all' WHERE user_id = ? AND channel = ?",
                (user_id, channel),
            )
        await self.conn.commit()

    async def get_keywords(self, user_id: int, channel: str) -> list[str]:
        async with self.conn.execute(
            "SELECT keyword FROM keywords WHERE user_id = ? AND channel = ?",
            (user_id, channel),
        ) as cursor:
            rows = await cursor.fetchall()
        return [r[0] for r in rows]

    async def set_mode(self, user_id: int, channel: str, mode: str):
        if mode in ("include", "exclude"):
            keywords = await self.get_keywords(user_id, channel)
            if not keywords:
                raise ValueError(f"Cannot set mode '{mode}' without keywords")
        await self.conn.execute(
            "UPDATE subscriptions SET mode = ? WHERE user_id = ? AND channel = ?",
            (mode, user_id, channel),
        )
        await self.conn.commit()

    async def set_user_active(self, user_id: int, active: bool):
        await self.conn.execute(
            "UPDATE subscriptions SET active = ? WHERE user_id = ?",
            (1 if active else 0, user_id),
        )
        await self.conn.commit()

    async def get_last_seen(self, channel: str) -> int:
        async with self.conn.execute(
            "SELECT message_id FROM last_seen WHERE channel = ?", (channel,)
        ) as cursor:
            row = await cursor.fetchone()
        return row[0] if row else 0

    async def update_last_seen(self, channel: str, message_id: int):
        await self.conn.execute(
            "INSERT INTO last_seen (channel, message_id) VALUES (?, ?) "
            "ON CONFLICT(channel) DO UPDATE SET message_id = excluded.message_id",
            (channel, message_id),
        )
        await self.conn.commit()

    async def get_active_channels(self) -> list[str]:
        async with self.conn.execute(
            "SELECT DISTINCT channel FROM subscriptions WHERE active = 1"
        ) as cursor:
            rows = await cursor.fetchall()
        return [r[0] for r in rows]

    async def get_active_subscribers(self, channel: str) -> list[dict]:
        async with self.conn.execute(
            "SELECT s.user_id, s.mode, GROUP_CONCAT(k.keyword) as keywords "
            "FROM subscriptions s "
            "LEFT JOIN keywords k ON s.user_id = k.user_id AND s.channel = k.channel "
            "WHERE s.channel = ? AND s.active = 1 "
            "GROUP BY s.user_id, s.mode",
            (channel,),
        ) as cursor:
            rows = await cursor.fetchall()
        return [
            {
                "user_id": r[0],
                "mode": r[1],
                "keywords": r[2].split(",") if r[2] else [],
            }
            for r in rows
        ]
```

**Step 5: Run tests to verify they pass**

```bash
pytest tests/test_db.py -v
```

Expected: all 11 tests PASS

**Step 6: Commit**

```bash
git add db.py tests/__init__.py tests/test_db.py
git commit -m "feat: database layer with full CRUD and tests"
```

---

### Task 3: Filtering logic

**Files:**
- Create: `filters.py`
- Create: `tests/test_filters.py`

**Step 1: Write failing tests in `tests/test_filters.py`**

```python
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from filters import should_forward


def test_mode_all_forwards_everything():
    assert should_forward(text="hello world", mode="all", keywords=[]) is True
    assert should_forward(text="crypto news", mode="all", keywords=["crypto"]) is True


def test_mode_include_matches_keyword():
    assert should_forward(text="Bitcoin price up", mode="include", keywords=["bitcoin"]) is True


def test_mode_include_no_match():
    assert should_forward(text="weather report", mode="include", keywords=["bitcoin"]) is False


def test_mode_include_empty_keywords_forwards_nothing():
    assert should_forward(text="any text", mode="include", keywords=[]) is False


def test_mode_exclude_no_match_forwards():
    assert should_forward(text="weather report", mode="exclude", keywords=["bitcoin"]) is True


def test_mode_exclude_match_blocks():
    assert should_forward(text="Bitcoin price up", mode="exclude", keywords=["bitcoin"]) is False


def test_mode_exclude_empty_keywords_forwards_everything():
    assert should_forward(text="any text", mode="exclude", keywords=[]) is True


def test_case_insensitive():
    assert should_forward(text="BITCOIN is rising", mode="include", keywords=["bitcoin"]) is True
    assert should_forward(text="Bitcoin news", mode="exclude", keywords=["BITCOIN"]) is False


def test_partial_word_match():
    assert should_forward(text="cryptocurrency market", mode="include", keywords=["crypto"]) is True


def test_none_text_treated_as_empty():
    assert should_forward(text=None, mode="include", keywords=["crypto"]) is False
    assert should_forward(text=None, mode="all", keywords=[]) is True
```

**Step 2: Run tests to verify they fail**

```bash
pytest tests/test_filters.py -v
```

Expected: `ModuleNotFoundError: No module named 'filters'`

**Step 3: Create `filters.py`**

```python
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
```

**Step 4: Run tests to verify they pass**

```bash
pytest tests/test_filters.py -v
```

Expected: all 10 tests PASS

**Step 5: Commit**

```bash
git add filters.py tests/test_filters.py
git commit -m "feat: filtering logic with full unit tests"
```

---

### Task 4: Bot entry point and auth guard

**Files:**
- Create: `handlers/__init__.py`
- Create: `handlers/start.py`
- Create: `bot.py`

**Step 1: Create `handlers/__init__.py`** (empty)

**Step 2: Create `handlers/start.py`**

```python
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
```

**Step 3: Create `bot.py`**

```python
import asyncio
import logging
import os
import aiosqlite
from pyrogram import Client
from contextlib import asynccontextmanager

import config
from db import Database
from handlers import start as start_handler

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

    async with app:
        logger.info("Bot started")
        await asyncio.Event().wait()  # run forever


if __name__ == "__main__":
    asyncio.run(main())
```

**Step 4: Create a `.env` file from the example and fill in your values**

```bash
cp .env.example .env
# Edit .env with your BOT_TOKEN and your Telegram user ID in ALLOWED_USERS
```

**Step 5: Test manually**

```bash
export $(cat .env | xargs) && python bot.py
```

Send `/start` to your bot. Verify it replies with the welcome message. Try from an unauthorized account — verify no response.

Stop the bot with Ctrl+C.

**Step 6: Commit**

```bash
git add handlers/__init__.py handlers/start.py bot.py
git commit -m "feat: bot entry point with /start and auth guard"
```

---

### Task 5: Channel management UI

**Files:**
- Create: `handlers/channels.py`
- Modify: `bot.py` (register new handler)

**Step 1: Create `handlers/channels.py`**

```python
from pyrogram import Client, filters
from pyrogram.types import Message, CallbackQuery, InlineKeyboardMarkup, InlineKeyboardButton
from pyrogram.errors import UsernameNotOccupied, UsernameInvalid, ChannelPrivate, PeerIdInvalid
import config
from db import Database


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
```

**Step 2: Register the handler in `bot.py`**

Add this import near the top:
```python
from handlers import channels as channels_handler
```

Add this line after `start_handler.register(...)`:
```python
channels_handler.register(app, get_db)
```

**Step 3: Test manually**

```bash
export $(cat .env | xargs) && python bot.py
```

- Send `/channels` — verify it shows "no channels yet" with an Add button
- Tap Add, send `@telegram` — verify it's added and a menu appears
- Tap Remove — verify it disappears
- Try a private/nonexistent username — verify error message

**Step 4: Commit**

```bash
git add handlers/channels.py bot.py
git commit -m "feat: channel add/remove UI with inline buttons"
```

---

### Task 6: Per-channel filter settings UI

**Files:**
- Create: `handlers/settings.py`
- Modify: `bot.py` (register new handler)

**Step 1: Create `handlers/settings.py`**

```python
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
        # Store pending state in message context using a simple reply chain
        # The next text message from this user will be caught by a separate handler
        # We use the message text to pass channel context via a reply
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
```

> **Note on keyword input UX:** The "add keyword" flow asks the user to reply to the bot's prompt message. We detect this via `reply_to_message` in the channels handler. Add the following to `handlers/channels.py` inside the `register` function, after the existing `handle_channel_input` handler:

```python
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
```

**Step 2: Register in `bot.py`**

Add import:
```python
from handlers import settings as settings_handler
```

Add registration after channels:
```python
settings_handler.register(app, get_db)
```

**Step 3: Test manually**

```bash
export $(cat .env | xargs) && python bot.py
```

- Add a channel, tap "Filter settings"
- Verify mode buttons show only "all" when no keywords exist
- Add a keyword — verify it appears in the list with an x button
- Switch mode to "include" / "exclude" — verify mode label updates
- Remove all keywords — verify mode resets to "all"

**Step 4: Commit**

```bash
git add handlers/settings.py handlers/channels.py bot.py
git commit -m "feat: per-channel filter settings UI with mode and keyword management"
```

---

### Task 7: Polling loop

**Files:**
- Create: `poller.py`
- Modify: `bot.py` (start poller task)

**Step 1: Create `poller.py`**

```python
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
            messages = await client.get_messages(
                chat_id=channel,
                # Fetch up to 100 messages newer than last_id
                # Pyrogram doesn't support min_id directly in get_messages;
                # use get_chat_history with offset_id instead
            )
            # get_chat_history returns messages newest-first; we want older than newest
            # but newer than last_id
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
```

**Step 2: Modify `bot.py` to start the poller**

Replace the `main` function with:

```python
async def main():
    app = Client("bot", bot_token=config.BOT_TOKEN)

    start_handler.register(app, get_db)
    channels_handler.register(app, get_db)
    settings_handler.register(app, get_db)

    async with app:
        logger.info("Bot started")
        asyncio.create_task(poller.run_poller(app, get_db))
        await asyncio.Event().wait()
```

Add the import at the top of `bot.py`:
```python
import poller
```

**Step 3: Test manually**

```bash
export $(cat .env | xargs) && python bot.py
```

- Add a channel (e.g., `@telegram`)
- Wait 60s (or temporarily set `POLL_INTERVAL_SECONDS=5`)
- Verify new messages from that channel are forwarded to you
- Add a keyword filter and verify only matching messages come through

**Step 4: Commit**

```bash
git add poller.py bot.py
git commit -m "feat: polling loop with filtering and forward"
```

---

### Task 8: Run all tests and finalize

**Step 1: Run the full test suite**

```bash
pytest tests/ -v
```

Expected: all tests PASS (no failures)

**Step 2: Create `pytest.ini` for asyncio mode**

```ini
[pytest]
asyncio_mode = auto
```

```bash
git add pytest.ini
```

**Step 3: Verify Docker build**

```bash
docker build -t telegram-news-bot .
```

Expected: builds successfully with no errors.

**Step 4: Final commit**

```bash
git add pytest.ini
git commit -m "chore: add pytest config and verify docker build"
```

**Step 5: Verify end-to-end**

Run the bot locally:
```bash
export $(cat .env | xargs) && python bot.py
```

Checklist:
- [ ] `/start` replies with welcome message
- [ ] Unauthorized user gets no response
- [ ] `/channels` shows channel list or add prompt
- [ ] Adding a valid public channel works
- [ ] Adding an invalid/private channel shows error
- [ ] Filter settings menu appears with correct mode buttons
- [ ] Adding keywords enables include/exclude modes
- [ ] Removing all keywords resets mode to "all"
- [ ] Messages from subscribed channels are forwarded within ~60s
- [ ] Keyword filtering works correctly (include/exclude/all)
