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
