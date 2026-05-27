const {
    SlashCommandBuilder, EmbedBuilder,
    PermissionFlagsBits, ChannelType,
} = require('discord.js');
const net = require('net');
const db  = require('../database');
const { success, error } = require('../ticketUtils');

const MC_HOST   = '5.175.192.58';
const MC_PORT   = 25569;
const UPDATE_MS = 2 * 60 * 1000; // 2 minutes

let liveInterval = null;

// ── Minecraft SLP ping ────────────────────────────────────────────────────────

function writeVarInt(val) {
    const out = [];
    do {
        let b = val & 0x7F;
        val >>>= 7;
        if (val !== 0) b |= 0x80;
        out.push(b);
    } while (val !== 0);
    return Buffer.from(out);
}

function readVarInt(buf, offset) {
    let val = 0, shift = 0, b;
    do {
        if (offset >= buf.length) return null;
        b = buf[offset++];
        val |= (b & 0x7F) << shift;
        shift += 7;
    } while (b & 0x80);
    return { value: val, offset };
}

function pingMinecraft(host, port, timeoutMs = 5000) {
    return new Promise(resolve => {
        const socket = new net.Socket();
        let buf      = Buffer.alloc(0);
        let settled  = false;

        const finish = result => {
            if (settled) return;
            settled = true;
            clearTimeout(timer);
            socket.destroy();
            resolve(result);
        };

        const timer = setTimeout(() => finish(null), timeoutMs);

        socket.connect(port, host, () => {
            // Handshake packet
            const hostBytes = Buffer.from(host, 'utf8');
            const handshake = Buffer.concat([
                writeVarInt(0x00),
                writeVarInt(765),
                writeVarInt(hostBytes.length), hostBytes,
                Buffer.from([(port >> 8) & 0xFF, port & 0xFF]),
                writeVarInt(1),
            ]);
            socket.write(Buffer.concat([writeVarInt(handshake.length), handshake]));

            // Status request packet
            const req = writeVarInt(0x00);
            socket.write(Buffer.concat([writeVarInt(req.length), req]));
        });

        socket.on('data', chunk => {
            buf = Buffer.concat([buf, chunk]);
            try {
                const r1 = readVarInt(buf, 0);
                if (!r1 || buf.length < r1.offset + r1.value) return;
                const r2 = readVarInt(buf, r1.offset);
                if (!r2 || r2.value !== 0x00) return;
                const r3 = readVarInt(buf, r2.offset);
                if (!r3) return;
                const json = buf.slice(r3.offset, r3.offset + r3.value).toString('utf8');
                finish(JSON.parse(json));
            } catch {}
        });

        socket.on('error', () => finish(null));
        socket.on('close', () => finish(null));
    });
}

// ── Embed builder ─────────────────────────────────────────────────────────────

async function buildMemberEmbed(guild) {
    const totalMembers  = guild.memberCount;
    const humanMembers  = guild.members.cache.filter(m => !m.user.bot).size;
    const botCount      = guild.members.cache.filter(m => m.user.bot).size;
    const onlineMembers = guild.members.cache.filter(m =>
        !m.user.bot && m.presence?.status && m.presence.status !== 'offline'
    ).size;

    const mc = await pingMinecraft(MC_HOST, MC_PORT);

    const now = new Date().toLocaleString('en-US', {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });

    const embed = new EmbedBuilder()
        .setColor(0x2ECC71)
        .setTitle(`🏰 ${guild.name}`)
        .setThumbnail(guild.iconURL())
        .setTimestamp();

    // ── Discord field ──
    const discordLines = [
        `👥 **Members:** ${humanMembers.toLocaleString()}`,
        onlineMembers > 0
            ? `🟢 **Online:** ${onlineMembers.toLocaleString()}`
            : `🟢 **Online:** —`,
        `🤖 **Bots:** ${botCount}`,
    ];
    embed.addFields({ name: '💬 Discord', value: discordLines.join('\n'), inline: true });

    // ── Minecraft field ──
    if (mc) {
        const online  = mc.players?.online ?? 0;
        const max     = mc.players?.max    ?? 0;
        const version = mc.version?.name   ?? '?';
        const sample  = (mc.players?.sample ?? []).filter(p => p.name && !p.name.startsWith('§'));

        const mcLines = [
            `🟢 **Online** — ${online}/${max} players`,
            `📦 **Version:** ${version}`,
            `🔗 **IP:** \`${MC_HOST}:${MC_PORT}\``,
        ];

        if (sample.length > 0) {
            const names = sample.map(p => `\`${p.name}\``).join(', ');
            mcLines.push(`\n**Playing:** ${names}`);
        }

        embed.addFields({ name: '⛏️ Minecraft', value: mcLines.join('\n'), inline: true });
    } else {
        embed.addFields({
            name: '⛏️ Minecraft',
            value: [
                `🔴 **Offline**`,
                `🔗 **IP:** \`${MC_HOST}:${MC_PORT}\``,
            ].join('\n'),
            inline: true,
        });
    }

    embed.setFooter({ text: `MostlyVanilla Beacon • Updates every 2 min • Last updated ${now}` });
    return embed;
}

// ── Live updater ──────────────────────────────────────────────────────────────

async function updateMemberEmbed(client) {
    const channelId = db.getSetting('member_embed_channel_id');
    const messageId = db.getSetting('member_embed_message_id');
    if (!channelId || !messageId) return;

    try {
        const channel = await client.channels.fetch(channelId).catch(() => null);
        if (!channel) return;

        const message = await channel.messages.fetch(messageId).catch(() => null);
        if (!message) {
            db.deleteSetting('member_embed_channel_id');
            db.deleteSetting('member_embed_message_id');
            console.log('[MemberEmbed] Message deleted — cleared saved IDs.');
            return;
        }

        const embed = await buildMemberEmbed(channel.guild);
        await message.edit({ embeds: [embed] });
    } catch (err) {
        console.error('[MemberEmbed] Live update failed:', err.message);
    }
}

function startMemberEmbed(client) {
    if (liveInterval) clearInterval(liveInterval);

    const channelId = db.getSetting('member_embed_channel_id');
    if (!channelId) return;

    console.log('[MemberEmbed] Restoring live embed — updating immediately then every 2 min.');
    updateMemberEmbed(client);
    liveInterval = setInterval(() => updateMemberEmbed(client), UPDATE_MS);
}

// ── Slash command ─────────────────────────────────────────────────────────────

const data = new SlashCommandBuilder()
    .setName('memberembed')
    .setDescription('Post a live-updating member count and Minecraft server status embed.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addChannelOption(o =>
        o.setName('channel')
         .setDescription('Channel to post the embed in.')
         .addChannelTypes(ChannelType.GuildText)
         .setRequired(true)
    );

async function execute(interaction) {
    await interaction.deferReply({ ephemeral: true });

    const channel = interaction.options.getChannel('channel');
    const embed   = await buildMemberEmbed(interaction.guild);
    const msg     = await channel.send({ embeds: [embed] });

    db.setSetting('member_embed_channel_id', channel.id);
    db.setSetting('member_embed_message_id', msg.id);
    console.log(`[MemberEmbed] Live embed posted — channel ${channel.id}, message ${msg.id}`);

    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(() => updateMemberEmbed(interaction.client), UPDATE_MS);

    await interaction.editReply({
        embeds: [success('Member Embed Posted', `Live embed posted in ${channel}. It will update every 2 minutes.`)],
    });
}

module.exports = { data, execute, startMemberEmbed };
