CREATE TABLE IF NOT EXISTS files (
    id TEXT PRIMARY KEY, -- store UUID as TEXT
    filename TEXT NOT NULL,
    path TEXT NOT NULL,
    content_type TEXT NOT NULL,
    uploaded_at TEXT DEFAULT CURRENT_TIMESTAMP
);