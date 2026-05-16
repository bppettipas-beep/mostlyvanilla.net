const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');

const dataDir = path.join(__dirname, '..', 'data');
if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'verification.db'));

db.exec(`
    CREATE TABLE IF NOT EXISTS pending_codes (
        code        TEXT    PRIMARY KEY,
        mc_uuid     TEXT    NOT NULL,
        mc_name     TEXT    NOT NULL,
        created_at  INTEGER NOT NULL,
        expires_at  INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS verified_players (
        discord_id  TEXT    PRIMARY KEY,
        mc_uuid     TEXT    NOT NULL UNIQUE,
        mc_name     TEXT    NOT NULL,
        verified_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS settings (
        key   TEXT PRIMARY KEY,
        value TEXT NOT NULL
    );
`);

const stmts = {
    insertCode: db.prepare(
        'INSERT OR REPLACE INTO pending_codes (code, mc_uuid, mc_name, created_at, expires_at) VALUES (?, ?, ?, ?, ?)'
    ),
    getCode: db.prepare(
        'SELECT * FROM pending_codes WHERE code = ? AND expires_at > ?'
    ),
    deleteCode: db.prepare(
        'DELETE FROM pending_codes WHERE code = ?'
    ),
    insertVerified: db.prepare(
        'INSERT OR REPLACE INTO verified_players (discord_id, mc_uuid, mc_name, verified_at) VALUES (?, ?, ?, ?)'
    ),
    getByDiscord: db.prepare(
        'SELECT * FROM verified_players WHERE discord_id = ?'
    ),
    getByMinecraft: db.prepare(
        'SELECT * FROM verified_players WHERE mc_uuid = ?'
    ),
    cleanupExpired: db.prepare(
        'DELETE FROM pending_codes WHERE expires_at <= ?'
    ),
    getSetting: db.prepare(
        'SELECT value FROM settings WHERE key = ?'
    ),
    setSetting: db.prepare(
        'INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)'
    ),
    deleteSetting: db.prepare(
        'DELETE FROM settings WHERE key = ?'
    ),
};

module.exports = {
    insertCode(code, mcUuid, mcName, expiryMinutes = 10) {
        const now = Date.now();
        stmts.insertCode.run(code, mcUuid, mcName, now, now + expiryMinutes * 60_000);
    },
    getCode: (code) => stmts.getCode.get(code, Date.now()),
    deleteCode: (code) => stmts.deleteCode.run(code),
    insertVerified(discordId, mcUuid, mcName) {
        stmts.insertVerified.run(discordId, mcUuid, mcName, Date.now());
    },
    getByDiscord: (discordId) => stmts.getByDiscord.get(discordId),
    getByMinecraft: (uuid) => stmts.getByMinecraft.get(uuid),
    cleanupExpired: () => stmts.cleanupExpired.run(Date.now()),
    getSetting: (key) => stmts.getSetting.get(key)?.value ?? null,
    setSetting: (key, value) => stmts.setSetting.run(key, value),
    deleteSetting: (key) => stmts.deleteSetting.run(key),
};
