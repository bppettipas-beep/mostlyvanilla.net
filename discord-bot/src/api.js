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

module.exports = {
    startApi(port) {
        app.listen(port, () => console.log(`[API] Listening on port ${port}`));
    },
};
