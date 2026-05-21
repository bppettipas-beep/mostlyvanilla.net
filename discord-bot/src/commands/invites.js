const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const db = require('../database');

const UPDATE_MS = 5 * 60 * 1000; // 5 minutes
let liveInterval = null;

// ── Embed builder ──────────────────────────────────────────────────────────

async function buildInviteEmbed(guild) {
    let invites;
    try {
        invites = await guild.invites.fetch();
    } catch {
        return null;
    }

    const counts = new Map();
    for (const invite of invites.values()) {
        if (!invite.inviter || !invite.uses) continue;
        const id = invite.inviter.id;
        if (!counts.has(id)) counts.set(id, { uses: 0 });
        counts.get(id).uses += invite.uses;
    }

    const sorted = [...counts.entries()]
        .sort((a, b) => b[1].uses - a[1].uses)
        .slice(0, 15);

    if (sorted.length === 0) return null;

    const medals = ['🥇', '🥈', '🥉'];
    const lines = sorted.map(([id, v], i) => {
        const rank = medals[i] ?? `\`#${i + 1}\``;
        return `${rank} <@${id}> — **${v.uses}** invite${v.uses !== 1 ? 's' : ''}`;
    }).join('\n');

    const total = sorted.reduce((sum, [, v]) => sum + v.uses, 0);
    const updatedAt = new Date().toLocaleString('en-US', {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });

    return new EmbedBuilder()
        .setColor(0x2ECC71)
        .setTitle('🔗 Invite Leaderboard')
        .setDescription(lines)
        .addFields({ name: 'Total Invites', value: `**${total}** across all tracked links` })
        .setFooter({ text: `MostlyVanilla Beacon • Updates every 5 min • Last updated ${updatedAt}` })
        .setTimestamp();
}

// ── Live updater ───────────────────────────────────────────────────────────

async function updateLiveBoard(client) {
    const channelId = db.getSetting('invite_board_channel_id');
    const messageId = db.getSetting('invite_board_message_id');
    if (!channelId || !messageId) return;

    try {
        const channel = await client.channels.fetch(channelId).catch(() => null);
        if (!channel) return;

        const message = await channel.messages.fetch(messageId).catch(() => null);
        if (!message) {
            // Message was deleted — clear the saved IDs so we stop trying
            db.deleteSetting('invite_board_channel_id');
            db.deleteSetting('invite_board_message_id');
            console.log('[Invites] Live board message was deleted — cleared saved IDs.');
            return;
        }

        const embed = await buildInviteEmbed(channel.guild);
        if (!embed) return;

        await message.edit({ embeds: [embed] });
    } catch (err) {
        console.error('[Invites] Live update failed:', err.message);
    }
}

/** Call this on bot ready to restore the live board after a restart. */
function startLiveBoard(client) {
    if (liveInterval) clearInterval(liveInterval);

    const channelId = db.getSetting('invite_board_channel_id');
    if (!channelId) return; // no board posted yet

    console.log('[Invites] Restoring live leaderboard — updating immediately then every 5 min.');
    updateLiveBoard(client);
    liveInterval = setInterval(() => updateLiveBoard(client), UPDATE_MS);
}

// ── Slash command ──────────────────────────────────────────────────────────

const data = new SlashCommandBuilder()
    .setName('invites')
    .setDescription('Show the server invite leaderboard')
    .addUserOption(opt =>
        opt.setName('user')
           .setDescription("Check a specific user's invite count")
           .setRequired(false)
    );

async function execute(interaction) {
    await interaction.deferReply();

    let invites;
    try {
        invites = await interaction.guild.invites.fetch();
    } catch {
        return interaction.editReply({ content: 'I need the **Manage Guild** permission to read invites.', ephemeral: true });
    }

    const counts = new Map();
    for (const invite of invites.values()) {
        if (!invite.inviter || !invite.uses) continue;
        const id = invite.inviter.id;
        if (!counts.has(id)) counts.set(id, { uses: 0 });
        counts.get(id).uses += invite.uses;
    }

    const target = interaction.options.getUser('user');

    if (target) {
        // Single-user lookup — not live
        const uses = counts.get(target.id)?.uses ?? 0;
        return interaction.editReply({
            embeds: [
                new EmbedBuilder()
                    .setColor(0x2ECC71)
                    .setTitle('🔗 Invite Count')
                    .setThumbnail(target.displayAvatarURL({ dynamic: true }))
                    .setDescription(`<@${target.id}> has invited **${uses}** member${uses !== 1 ? 's' : ''} to the server.`)
                    .setFooter({ text: 'MostlyVanilla Beacon' })
                    .setTimestamp(),
            ],
        });
    }

    // Full leaderboard — post and register as the live board
    const embed = await buildInviteEmbed(interaction.guild);
    if (!embed) return interaction.editReply({ content: 'No invite data found — no invites have been used yet.' });

    const message = await interaction.editReply({ embeds: [embed] });

    // Persist channel + message ID to the database (survives restarts)
    db.setSetting('invite_board_channel_id', interaction.channelId);
    db.setSetting('invite_board_message_id', message.id);
    console.log(`[Invites] Live board registered — channel ${interaction.channelId}, message ${message.id}`);

    // Start (or restart) the live update interval
    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(() => updateLiveBoard(interaction.client), UPDATE_MS);
}

module.exports = { data, execute, startLiveBoard };
