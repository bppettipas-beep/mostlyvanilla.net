const express = require('express');
const db = require('./database');
const { client } = require('./bot');

const app = express();
app.use(express.json());

// Characters chosen to avoid visual ambiguity (no 0/O, 1/I/L)
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function generateCode() {
    let code = '';
    for (let i = 0; i < 6; i++) {
        code += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
    }
    return code;
}

function requireSecret(req, res, next) {
    if (req.headers['x-api-secret'] !== process.env.API_SECRET) {
        return res.status(401).json({ error: 'Unauthorized' });
    }
    next();
}

async function createInvite(mcName, expiryMinutes) {
    try {
        const guild = client.guilds.cache.get(process.env.GUILD_ID);
        const channel = guild?.channels.cache.get(process.env.INVITE_CHANNEL_ID);
        if (!channel) {
            console.warn('[API] INVITE_CHANNEL_ID not set or channel not found — skipping invite creation');
            return null;
        }
        const invite = await channel.createInvite({
            maxAge: expiryMinutes * 60,
            maxUses: 1,
            unique: true,
            reason: `Verification invite for ${mcName}`,
        });
        return invite.url;
    } catch (err) {
        console.warn('[API] Could not create invite:', err.message);
        return null;
    }
}

// Called by the Minecraft plugin when a player runs /discord or joins unverified
app.post('/api/register-code', requireSecret, async (req, res) => {
    const { minecraft_uuid, minecraft_name } = req.body;
    if (!minecraft_uuid || !minecraft_name) {
        return res.status(400).json({ error: 'Missing minecraft_uuid or minecraft_name' });
    }

    db.cleanupExpired();

    // Ensure unique code
    let code;
    let tries = 0;
    do { code = generateCode(); } while (db.getCode(code) && ++tries < 10);

    const expiryMinutes = parseInt(process.env.CODE_EXPIRY_MINUTES) || 10;
    db.insertCode(code, minecraft_uuid, minecraft_name, expiryMinutes);

    // Generate a fresh single-use invite alongside the code
    const inviteUrl = await createInvite(minecraft_name, expiryMinutes);

    console.log(`[API] Code ${code} issued for ${minecraft_name} (${minecraft_uuid})${inviteUrl ? ' with invite' : ''}`);
    res.json({ success: true, code, invite_url: inviteUrl, expires_in_minutes: expiryMinutes });
});

// Called by the Minecraft plugin to check if a player is verified
app.get('/api/verified/:uuid', requireSecret, (req, res) => {
    const row = db.getByMinecraft(req.params.uuid);
    res.json({ verified: !!row, data: row ?? null });
});

// Called by the Minecraft plugin when /role link is used
app.post('/api/role-links', requireSecret, (req, res) => {
    const { game_role, discord_role_id } = req.body;
    if (!game_role || !discord_role_id) return res.status(400).json({ error: 'Missing game_role or discord_role_id' });
    db.roleLinks.set(game_role, discord_role_id);
    console.log(`[API] Role link set: ${game_role} → ${discord_role_id}`);
    res.json({ success: true });
});

// Called by the Minecraft plugin when /role unlink is used
app.delete('/api/role-links/:gameRole', requireSecret, (req, res) => {
    db.roleLinks.delete(req.params.gameRole);
    console.log(`[API] Role link removed: ${req.params.gameRole}`);
    res.json({ success: true });
});

// Called by the Minecraft plugin to add/remove a Discord role from a user
app.post('/api/discord-role/assign', requireSecret, async (req, res) => {
    const { discord_user_id, discord_role_id, assign } = req.body;
    if (!discord_user_id || !discord_role_id) return res.status(400).json({ error: 'Missing fields' });
    try {
        const guild = client.guilds.cache.get(process.env.GUILD_ID);
        if (!guild) return res.status(503).json({ error: 'Guild not available' });
        const member = await guild.members.fetch(discord_user_id).catch(() => null);
        if (!member) return res.status(404).json({ error: 'Member not in guild' });
        const role = guild.roles.cache.get(discord_role_id);
        if (!role) return res.status(404).json({ error: 'Role not found' });
        if (assign) {
            await member.roles.add(role);
        } else {
            await member.roles.remove(role);
        }
        console.log(`[API] ${assign ? 'Assigned' : 'Removed'} Discord role ${role.name} for ${member.user.tag}`);
        res.json({ success: true });
    } catch (err) {
        console.error('[API] discord-role/assign error:', err.message);
        res.status(500).json({ error: err.message });
    }
});

// Returns all Discord role IDs held by the verified player (used by Minecraft to sync highest role)
app.get('/api/discord-roles/:uuid', requireSecret, async (req, res) => {
    const row = db.getByMinecraft(req.params.uuid);
    if (!row) return res.json([]);
    try {
        const guild = client.guilds.cache.get(process.env.GUILD_ID);
        if (!guild) return res.json([]);
        const member = await guild.members.fetch(row.discord_id).catch(() => null);
        if (!member) return res.json([]);
        // Return every role ID except the @everyone role
        const roleIds = member.roles.cache
            .filter(r => r.id !== guild.id)
            .map(r => r.id);
        res.json(roleIds);
    } catch (err) {
        console.error('[API] discord-roles error:', err.message);
        res.json([]);
    }
});

// Called by the Minecraft plugin for every chat message
app.post('/api/chat-message', requireSecret, async (req, res) => {
    const { player_uuid, player_name, player_role, message, world, x, y, z } = req.body;
    if (!player_uuid || !player_name || message === undefined) {
        return res.status(400).json({ error: 'Missing required fields' });
    }

    const guildId = process.env.GUILD_ID;

    db.chatLogs.insert({
        guild_id:    guildId,
        player_uuid,
        player_name,
        player_role: player_role || null,
        message,
        world:       world  || null,
        x:           x      ?? null,
        y:           y      ?? null,
        z:           z      ?? null,
    });

    // Relay to live chat-log channel if one is configured
    const channelId = db.getSetting(`chatlog_channel_${guildId}`);
    if (channelId) {
        try {
            const guild = client.guilds.cache.get(guildId);
            const ch = guild?.channels.cache.get(channelId);
            if (ch) {
                const timeStr  = new Date().toISOString().slice(11, 19) + ' UTC';
                const roleTag  = player_role ? ` \`[${player_role}]\`` : '';
                const locStr   = (world && x != null) ? ` · *${world}* \`${x}, ${y}, ${z}\`` : '';
                await ch.send(`\`${timeStr}\`${roleTag} **${player_name}**${locStr}: ${message}`);
            }
        } catch (err) {
            console.error('[API] Failed to relay chat message:', err.message);
        }
    }

    res.json({ success: true });
});

// Polled by the Minecraft plugin every poll-interval seconds
app.get('/api/pending-game-roles', requireSecret, (req, res) => {
    const rows = db.pendingGameRoles.drainAll();
    res.json(rows);
});

module.exports = {
    startApi(port) {
        app.listen(port, () => console.log(`[API] Listening on port ${port}`));
    },
};
