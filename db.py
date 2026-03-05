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
