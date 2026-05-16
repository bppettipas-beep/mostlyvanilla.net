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

// ── Ticket migrations (no-op if columns already exist or table absent) ─────────
try { db.exec('ALTER TABLE ticket_config ADD COLUMN support_role_ids TEXT'); } catch {}
try { db.exec('ALTER TABLE ticket_questions ADD COLUMN category_id TEXT'); } catch {}
try { db.exec('ALTER TABLE ticket_questions ADD COLUMN support_role_ids TEXT'); } catch {}

db.exec(`
    CREATE TABLE IF NOT EXISTS ticket_config (
        guild_id         TEXT PRIMARY KEY,
        category_id      TEXT,
        log_channel_id   TEXT,
        support_role_id  TEXT,
        support_role_ids TEXT,
        panel_channel_id TEXT,
        panel_message_id TEXT,
        next_ticket_num  INTEGER DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS tickets (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        guild_id   TEXT NOT NULL,
        channel_id TEXT NOT NULL UNIQUE,
        ticket_num INTEGER NOT NULL,
        owner_id   TEXT NOT NULL,
        claimed_by TEXT,
        status     TEXT DEFAULT 'open',
        reason     TEXT,
        created_at INTEGER DEFAULT (strftime('%s','now')),
        closed_at  INTEGER
    );

    CREATE TABLE IF NOT EXISTS ticket_members (
        ticket_id INTEGER REFERENCES tickets(id) ON DELETE CASCADE,
        user_id   TEXT NOT NULL,
        PRIMARY KEY (ticket_id, user_id)
    );

    CREATE TABLE IF NOT EXISTS ticket_questions (
        guild_id         TEXT NOT NULL,
        prefix           TEXT NOT NULL,
        questions        TEXT NOT NULL DEFAULT '[]',
        category_id      TEXT,
        support_role_ids TEXT,
        PRIMARY KEY (guild_id, prefix)
    );
`);

const ticketConfig = {
    get: db.prepare('SELECT * FROM ticket_config WHERE guild_id = ?'),
    upsert: db.prepare(`
        INSERT INTO ticket_config (guild_id, category_id, log_channel_id, support_role_id,
            support_role_ids, panel_channel_id, panel_message_id, next_ticket_num)
        VALUES (@guild_id, @category_id, @log_channel_id, @support_role_id,
            @support_role_ids, @panel_channel_id, @panel_message_id, @next_ticket_num)
        ON CONFLICT(guild_id) DO UPDATE SET
            category_id      = COALESCE(excluded.category_id, category_id),
            log_channel_id   = COALESCE(excluded.log_channel_id, log_channel_id),
            support_role_id  = COALESCE(excluded.support_role_id, support_role_id),
            support_role_ids = COALESCE(excluded.support_role_ids, support_role_ids),
            panel_channel_id = COALESCE(excluded.panel_channel_id, panel_channel_id),
            panel_message_id = COALESCE(excluded.panel_message_id, panel_message_id),
            next_ticket_num  = COALESCE(excluded.next_ticket_num, next_ticket_num)
    `),
    bumpTicketNum: db.prepare('UPDATE ticket_config SET next_ticket_num = next_ticket_num + 1 WHERE guild_id = ?'),
};

function getSupportRoleIds(cfg) {
    if (cfg?.support_role_ids) {
        try { return JSON.parse(cfg.support_role_ids); } catch {}
    }
    if (cfg?.support_role_id) return [cfg.support_role_id];
    return [];
}

const ticketQuestions = {
    get: db.prepare('SELECT * FROM ticket_questions WHERE guild_id = ? AND prefix = ?'),
    upsert: db.prepare(`
        INSERT INTO ticket_questions (guild_id, prefix, questions, category_id, support_role_ids)
        VALUES (@guild_id, @prefix, @questions, @category_id, @support_role_ids)
        ON CONFLICT(guild_id, prefix) DO UPDATE SET
            questions        = COALESCE(excluded.questions, questions),
            category_id      = excluded.category_id,
            support_role_ids = excluded.support_role_ids
    `),
};

const tickets = {
    create:       db.prepare('INSERT INTO tickets (guild_id, channel_id, ticket_num, owner_id, reason) VALUES (@guild_id, @channel_id, @ticket_num, @owner_id, @reason)'),
    getByChannel: db.prepare('SELECT * FROM tickets WHERE channel_id = ?'),
    getById:      db.prepare('SELECT * FROM tickets WHERE id = ?'),
    listOpen:     db.prepare("SELECT * FROM tickets WHERE guild_id = ? AND status = 'open'"),
    update:       db.prepare('UPDATE tickets SET status = @status, claimed_by = @claimed_by, closed_at = @closed_at WHERE id = @id'),
};

db.exec(`
    CREATE TABLE IF NOT EXISTS staffapp_config (
        guild_id         TEXT PRIMARY KEY,
        log_channel_id   TEXT,
        questions        TEXT DEFAULT '[]',
        role_ids         TEXT DEFAULT '[]',
        title            TEXT DEFAULT 'Staff Applications',
        description      TEXT DEFAULT 'Click below to apply for a staff position.',
        panel_channel_id TEXT,
        panel_message_id TEXT
    );

    CREATE TABLE IF NOT EXISTS staffapp_applications (
        id              INTEGER PRIMARY KEY AUTOINCREMENT,
        guild_id        TEXT NOT NULL,
        user_id         TEXT NOT NULL,
        applied_role_id TEXT NOT NULL,
        answers         TEXT DEFAULT '[]',
        status          TEXT DEFAULT 'pending',
        log_message_id  TEXT,
        reviewed_by     TEXT,
        submitted_at    INTEGER DEFAULT (strftime('%s','now'))
    );
`);

const staffappConfig = {
    get: db.prepare('SELECT * FROM staffapp_config WHERE guild_id = ?'),
    upsert: db.prepare(`
        INSERT INTO staffapp_config (guild_id, log_channel_id, questions, role_ids, title, description, panel_channel_id, panel_message_id)
        VALUES (@guild_id, @log_channel_id, @questions, @role_ids, @title, @description, @panel_channel_id, @panel_message_id)
        ON CONFLICT(guild_id) DO UPDATE SET
            log_channel_id   = excluded.log_channel_id,
            questions        = excluded.questions,
            role_ids         = excluded.role_ids,
            title            = excluded.title,
            description      = excluded.description,
            panel_channel_id = excluded.panel_channel_id,
            panel_message_id = excluded.panel_message_id
    `),
};

const staffappApplications = {
    create:        db.prepare('INSERT INTO staffapp_applications (guild_id, user_id, applied_role_id, answers) VALUES (@guild_id, @user_id, @applied_role_id, @answers)'),
    getById:       db.prepare('SELECT * FROM staffapp_applications WHERE id = ?'),
    getPending:    db.prepare("SELECT * FROM staffapp_applications WHERE guild_id = ? AND user_id = ? AND status = 'pending' LIMIT 1"),
    updateStatus:  db.prepare('UPDATE staffapp_applications SET status = @status, reviewed_by = @reviewed_by WHERE id = @id'),
    setLogMessage: db.prepare('UPDATE staffapp_applications SET log_message_id = @log_message_id WHERE id = @id'),
};

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
    ticketConfig,
    getSupportRoleIds,
    ticketQuestions,
    tickets,
    staffappConfig,
    staffappApplications,
};
