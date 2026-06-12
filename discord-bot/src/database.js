const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');

const dataDir = path.join(__dirname, '..', 'data');
if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });

const db = new Database(path.join(dataDir, 'verification.db'));

db.exec(`
    CREATE TABLE IF NOT EXISTS settings (
        key   TEXT PRIMARY KEY,
        value TEXT NOT NULL
    );
`);

// ── Ticket migrations ─────────────────────────────────────────────────────────
try { db.exec('ALTER TABLE ticket_config ADD COLUMN support_role_ids TEXT'); } catch {}
try { db.exec('ALTER TABLE ticket_questions ADD COLUMN category_id TEXT'); } catch {}
try { db.exec('ALTER TABLE ticket_questions ADD COLUMN support_role_ids TEXT'); } catch {}
try { db.exec("ALTER TABLE tickets ADD COLUMN prefix TEXT NOT NULL DEFAULT 'ticket'"); } catch {}

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
    create:       db.prepare('INSERT INTO tickets (guild_id, channel_id, ticket_num, owner_id, reason, prefix) VALUES (@guild_id, @channel_id, @ticket_num, @owner_id, @reason, @prefix)'),
    getByChannel: db.prepare('SELECT * FROM tickets WHERE channel_id = ?'),
    getById:      db.prepare('SELECT * FROM tickets WHERE id = ?'),
    listOpen:     db.prepare("SELECT * FROM tickets WHERE guild_id = ? AND status = 'open'"),
    update:       db.prepare('UPDATE tickets SET status = @status, claimed_by = @claimed_by, closed_at = @closed_at WHERE id = @id'),
};

// ── Staff applications ────────────────────────────────────────────────────────
try { db.exec('ALTER TABLE staffapp_applications ADD COLUMN reason TEXT'); } catch {}

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
        reason          TEXT,
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
    updateStatus:  db.prepare('UPDATE staffapp_applications SET status = @status, reviewed_by = @reviewed_by, reason = @reason WHERE id = @id'),
    setLogMessage: db.prepare('UPDATE staffapp_applications SET log_message_id = @log_message_id WHERE id = @id'),
};

// ── Chat counts ───────────────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS chat_counts (
        guild_id TEXT NOT NULL,
        user_id  TEXT NOT NULL,
        count    INTEGER DEFAULT 0,
        PRIMARY KEY (guild_id, user_id)
    );
`);

const chatCountStmts = {
    increment: db.prepare(`
        INSERT INTO chat_counts (guild_id, user_id, count) VALUES (?, ?, 1)
        ON CONFLICT(guild_id, user_id) DO UPDATE SET count = count + 1
    `),
    getUser: db.prepare('SELECT * FROM chat_counts WHERE guild_id = ? AND user_id = ?'),
    getTop:  db.prepare('SELECT * FROM chat_counts WHERE guild_id = ? ORDER BY count DESC LIMIT 15'),
};

// ── Invite wipes ──────────────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS invite_wipes (
        guild_id TEXT NOT NULL,
        user_id  TEXT NOT NULL,
        PRIMARY KEY (guild_id, user_id)
    );
`);

const inviteWipeStmts = {
    add:    db.prepare('INSERT OR IGNORE INTO invite_wipes (guild_id, user_id) VALUES (?, ?)'),
    remove: db.prepare('DELETE FROM invite_wipes WHERE guild_id = ? AND user_id = ?'),
    isWiped: db.prepare('SELECT 1 FROM invite_wipes WHERE guild_id = ? AND user_id = ?'),
    getAll: db.prepare('SELECT user_id FROM invite_wipes WHERE guild_id = ?'),
};

// ── Strikes ───────────────────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS strikes (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        guild_id   TEXT NOT NULL,
        user_id    TEXT NOT NULL,
        mod_id     TEXT NOT NULL,
        reason     TEXT NOT NULL,
        created_at INTEGER DEFAULT (strftime('%s','now'))
    );
`);

const strikeStmts = {
    create:        db.prepare('INSERT INTO strikes (guild_id, user_id, mod_id, reason) VALUES (@guild_id, @user_id, @mod_id, @reason)'),
    getUser:       db.prepare('SELECT * FROM strikes WHERE guild_id = ? AND user_id = ? ORDER BY created_at ASC'),
    countUser:     db.prepare('SELECT COUNT(*) as count FROM strikes WHERE guild_id = ? AND user_id = ?'),
    removeLast:    db.prepare('DELETE FROM strikes WHERE id = (SELECT id FROM strikes WHERE guild_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT 1)'),
    clearUser:     db.prepare('DELETE FROM strikes WHERE guild_id = ? AND user_id = ?'),
    clearAll:      db.prepare('DELETE FROM strikes WHERE guild_id = ?'),
    getLeaderboard: db.prepare('SELECT user_id, COUNT(*) as count, MAX(reason) as last_reason, MAX(created_at) as last_at FROM strikes WHERE guild_id = ? GROUP BY user_id ORDER BY count DESC, last_at DESC LIMIT 20'),
};

// ── Moderation ────────────────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS warnings (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        guild_id   TEXT NOT NULL,
        user_id    TEXT NOT NULL,
        mod_id     TEXT NOT NULL,
        reason     TEXT NOT NULL,
        created_at INTEGER DEFAULT (strftime('%s','now'))
    );

    CREATE TABLE IF NOT EXISTS mod_cases (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        guild_id   TEXT NOT NULL,
        user_id    TEXT NOT NULL,
        mod_id     TEXT NOT NULL,
        action     TEXT NOT NULL,
        reason     TEXT,
        duration   INTEGER,
        created_at INTEGER DEFAULT (strftime('%s','now'))
    );
`);

const warningStmts = {
    create:    db.prepare('INSERT INTO warnings (guild_id, user_id, mod_id, reason) VALUES (@guild_id, @user_id, @mod_id, @reason)'),
    getUser:   db.prepare('SELECT * FROM warnings WHERE guild_id = ? AND user_id = ? ORDER BY created_at ASC'),
    countUser: db.prepare('SELECT COUNT(*) as count FROM warnings WHERE guild_id = ? AND user_id = ?'),
    clearUser: db.prepare('DELETE FROM warnings WHERE guild_id = ? AND user_id = ?'),
};

const modCaseStmts = {
    create:  db.prepare('INSERT INTO mod_cases (guild_id, user_id, mod_id, action, reason, duration) VALUES (@guild_id, @user_id, @mod_id, @action, @reason, @duration)'),
    get:     db.prepare('SELECT * FROM mod_cases WHERE id = ?'),
    getUser: db.prepare('SELECT * FROM mod_cases WHERE guild_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT 20'),
};

// ── Chat logs ─────────────────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS chat_logs (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        guild_id    TEXT NOT NULL,
        player_uuid TEXT NOT NULL,
        player_name TEXT NOT NULL,
        player_role TEXT,
        message     TEXT NOT NULL,
        world       TEXT,
        x           INTEGER,
        y           INTEGER,
        z           INTEGER,
        timestamp   INTEGER DEFAULT (strftime('%s','now'))
    );
`);

// Prune logs older than 30 days on startup
db.prepare('DELETE FROM chat_logs WHERE timestamp < ?').run(Math.floor(Date.now() / 1000) - 30 * 86400);

const chatLogStmts = {
    insert: db.prepare(`
        INSERT INTO chat_logs (guild_id, player_uuid, player_name, player_role, message, world, x, y, z)
        VALUES (@guild_id, @player_uuid, @player_name, @player_role, @message, @world, @x, @y, @z)
    `),
    getRecent: db.prepare(`
        SELECT * FROM (
            SELECT * FROM chat_logs WHERE guild_id = ? AND timestamp >= ?
            ORDER BY timestamp DESC LIMIT ?
        ) ORDER BY timestamp ASC
    `),
    getByPlayer: db.prepare(`
        SELECT * FROM (
            SELECT * FROM chat_logs WHERE guild_id = ? AND lower(player_name) = lower(?) AND timestamp >= ?
            ORDER BY timestamp DESC LIMIT ?
        ) ORDER BY timestamp ASC
    `),
};

// ── Verification & linking ────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS pending_codes (
        code       TEXT PRIMARY KEY,
        mc_uuid    TEXT NOT NULL,
        mc_name    TEXT NOT NULL,
        expires_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS verified_players (
        mc_uuid    TEXT PRIMARY KEY,
        discord_id TEXT NOT NULL UNIQUE,
        mc_name    TEXT NOT NULL,
        linked_at  INTEGER DEFAULT (strftime('%s','now'))
    );
`);

db.prepare('DELETE FROM pending_codes WHERE expires_at < ?').run(Math.floor(Date.now() / 1000));

// Migration: existing DB may have a NOT NULL created_at column from a prior schema
try { db.exec('ALTER TABLE pending_codes ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0'); } catch {}

const codeStmts = {
    insert:         db.prepare('INSERT OR REPLACE INTO pending_codes (code, mc_uuid, mc_name, expires_at, created_at) VALUES (?, ?, ?, ?, ?)'),
    get:            db.prepare('SELECT * FROM pending_codes WHERE code = ?'),
    del:            db.prepare('DELETE FROM pending_codes WHERE code = ?'),
    cleanupExpired: db.prepare('DELETE FROM pending_codes WHERE expires_at < ?'),
};

const verifiedStmts = {
    link:           db.prepare('INSERT OR REPLACE INTO verified_players (mc_uuid, discord_id, mc_name) VALUES (?, ?, ?)'),
    getByMcUuid:    db.prepare('SELECT * FROM verified_players WHERE mc_uuid = ?'),
    getByDiscordId: db.prepare('SELECT * FROM verified_players WHERE discord_id = ?'),
    unlinkByMcUuid: db.prepare('DELETE FROM verified_players WHERE mc_uuid = ?'),
};

// ── Role sync ─────────────────────────────────────────────────────────────────
db.exec(`
    CREATE TABLE IF NOT EXISTS role_links (
        game_role       TEXT PRIMARY KEY,
        discord_role_id TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS pending_game_roles (
        id        INTEGER PRIMARY KEY AUTOINCREMENT,
        mc_uuid   TEXT NOT NULL,
        game_role TEXT,
        assign    INTEGER NOT NULL DEFAULT 0
    );
`);

// Migrations for tables that may already exist without these columns
try { db.exec('ALTER TABLE pending_game_roles ADD COLUMN mc_uuid TEXT NOT NULL DEFAULT ""'); } catch {}
try { db.exec('ALTER TABLE pending_game_roles ADD COLUMN game_role TEXT'); } catch {}
try { db.exec('ALTER TABLE pending_game_roles ADD COLUMN assign INTEGER NOT NULL DEFAULT 0'); } catch {}

const roleLinkStmts = {
    set:    db.prepare('INSERT OR REPLACE INTO role_links (game_role, discord_role_id) VALUES (?, ?)'),
    get:    db.prepare('SELECT discord_role_id FROM role_links WHERE game_role = ?'),
    del:    db.prepare('DELETE FROM role_links WHERE game_role = ?'),
    getAll: db.prepare('SELECT * FROM role_links'),
};

const pendingRoleStmts = {
    enqueue:   db.prepare('INSERT INTO pending_game_roles (mc_uuid, game_role, assign) VALUES (?, ?, ?)'),
    selectAll: db.prepare('SELECT * FROM pending_game_roles ORDER BY id ASC'),
    deleteAll: db.prepare('DELETE FROM pending_game_roles'),
};

const drainPendingRoles = db.transaction(() => {
    const rows = pendingRoleStmts.selectAll.all();
    pendingRoleStmts.deleteAll.run();
    return rows;
});

// ── Settings ──────────────────────────────────────────────────────────────────
const stmts = {
    getSetting:    db.prepare('SELECT value FROM settings WHERE key = ?'),
    setSetting:    db.prepare('INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)'),
    deleteSetting: db.prepare('DELETE FROM settings WHERE key = ?'),
};

module.exports = {
    getSetting:    (key)        => stmts.getSetting.get(key)?.value ?? null,
    setSetting:    (key, value) => stmts.setSetting.run(key, value),
    deleteSetting: (key)        => stmts.deleteSetting.run(key),
    codes: {
        insert:         (code, mcUuid, mcName, expiresAt) => codeStmts.insert.run(code, mcUuid, mcName, expiresAt, Math.floor(Date.now() / 1000)),
        get:            (code) => codeStmts.get.get(code),
        delete:         (code) => codeStmts.del.run(code),
        cleanupExpired: ()     => codeStmts.cleanupExpired.run(Math.floor(Date.now() / 1000)),
    },
    verified: {
        link:           (mcUuid, discordId, mcName) => verifiedStmts.link.run(mcUuid, discordId, mcName),
        getByMcUuid:    (mcUuid)    => verifiedStmts.getByMcUuid.get(mcUuid),
        getByDiscordId: (discordId) => verifiedStmts.getByDiscordId.get(discordId),
        unlink:         (mcUuid)    => verifiedStmts.unlinkByMcUuid.run(mcUuid),
    },
    roleLinks: {
        set:    (gameRole, discordRoleId) => roleLinkStmts.set.run(gameRole, discordRoleId),
        get:    (gameRole) => roleLinkStmts.get.get(gameRole)?.discord_role_id ?? null,
        delete: (gameRole) => roleLinkStmts.del.run(gameRole),
        getAll: ()         => roleLinkStmts.getAll.all(),
    },
    pendingGameRoles: {
        enqueue:  (mcUuid, gameRole, assign) => pendingRoleStmts.enqueue.run(mcUuid, gameRole ?? null, assign ? 1 : 0),
        drainAll: () => drainPendingRoles(),
    },
    ticketConfig,
    getSupportRoleIds,
    ticketQuestions,
    tickets,
    staffappConfig,
    staffappApplications,
    chatCounts: {
        increment: (guildId, userId) => chatCountStmts.increment.run(guildId, userId),
        getUser:   chatCountStmts.getUser,
        getTop:    chatCountStmts.getTop,
    },
    strikes: {
        create:         strikeStmts.create,
        getUser:        strikeStmts.getUser,
        countUser:      strikeStmts.countUser,
        removeLast:     (guildId, userId) => strikeStmts.removeLast.run(guildId, userId),
        clearUser:      (guildId, userId) => strikeStmts.clearUser.run(guildId, userId),
        clearAll:       (guildId)         => strikeStmts.clearAll.run(guildId),
        getLeaderboard: strikeStmts.getLeaderboard,
    },
    inviteWipes: {
        add:     (guildId, userId) => inviteWipeStmts.add.run(guildId, userId),
        remove:  (guildId, userId) => inviteWipeStmts.remove.run(guildId, userId),
        isWiped: (guildId, userId) => !!inviteWipeStmts.isWiped.get(guildId, userId),
        getAll:  (guildId)         => inviteWipeStmts.getAll.all(guildId).map(r => r.user_id),
    },
    warnings: {
        create:    warningStmts.create,
        getUser:   warningStmts.getUser,
        countUser: warningStmts.countUser,
        clearUser: warningStmts.clearUser,
    },
    modCases: {
        create:  modCaseStmts.create,
        get:     modCaseStmts.get,
        getUser: modCaseStmts.getUser,
    },
    chatLogs: {
        insert:      (row) => chatLogStmts.insert.run(row),
        getRecent:   chatLogStmts.getRecent,
        getByPlayer: chatLogStmts.getByPlayer,
    },
};
