import os
from dotenv import load_dotenv

load_dotenv()

API_ID = int(os.environ["API_ID"])
API_HASH = os.environ["API_HASH"]
BOT_TOKEN = os.environ["BOT_TOKEN"]
ALLOWED_USERS = set(int(uid) for uid in os.environ["ALLOWED_USERS"].split(","))
POLL_INTERVAL_SECONDS = int(os.getenv("POLL_INTERVAL_SECONDS", "60"))
DB_PATH = os.getenv("DB_PATH", "data/bot.db")
