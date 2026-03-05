# In-memory conversation state

# Maps user_id -> channel, set when awaiting a keyword input
pending_keyword: dict[int, str] = {}
