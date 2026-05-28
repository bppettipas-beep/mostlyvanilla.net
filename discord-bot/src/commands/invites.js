const { SlashCommandBuilder, EmbedBuilder, PermissionFlagsBits } = require('discord.js');
const db = require('../database');
const { success, error, warning } = require('../ticketUtils');

const UPDATE_MS = 30 * 1000; // 30 seconds
let liveInterval = null;

// ── Embed builder ──────────────────────────────────────────────────────────

async function buildInviteEmbed(guild) {
    let invites;
    try {
        invites = await guild.invites.fetch();
    } catch {
        return null;
    }

    const wiped = new Set(db.inviteWipes.getAll(guild.id));

    const counts = new Map();
    for (const invite of invites.values()) {
        if (!invite.inviter || !invite.uses) continue;
        const id = invite.inviter.id;
        if (wiped.has(id)) continue;
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
        second: '2-digit',
    });

    return new EmbedBuilder()
        .setColor(0x2ECC71)
        .setTitle('🔗 Invite Leaderboard')
        .setDescription(lines)
        .addFields({ name: 'Total Invites', value: `**${total}** across all tracked links` })
        .setFooter({ text: `MostlyVanilla Beacon • Updates every 30s • Last updated ${updatedAt}` })
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

function startLiveBoard(client) {
    if (liveInterval) clearInterval(liveInterval);

    const channelId = db.getSetting('invite_board_channel_id');
    if (!channelId) return;

    console.log('[Invites] Restoring live leaderboard — updating immediately then every 30s.');
    updateLiveBoard(client);
    liveInterval = setInterval(() => updateLiveBoard(client), UPDATE_MS);
}

// ── /invites ───────────────────────────────────────────────────────────────

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
        return interaction.editReply({ content: 'I need the **Manage Guild** permission to read invites.' });
    }

    const wiped  = new Set(db.inviteWipes.getAll(interaction.guildId));
    const counts = new Map();
    for (const invite of invites.values()) {
        if (!invite.inviter || !invite.uses) continue;
        const id = invite.inviter.id;
        if (wiped.has(id)) continue;
        if (!counts.has(id)) counts.set(id, { uses: 0 });
        counts.get(id).uses += invite.uses;
    }

    const target = interaction.options.getUser('user');

    if (target) {
        const uses = counts.get(target.id)?.uses ?? 0;
        return interaction.editReply({
            embeds: [
                new EmbedBuilder()
                    .setColor(0x2ECC71)
                    .setTitle('🔗 Invite Count')
                    .setThumbnail(target.displayAvatarURL())
                    .setDescription(`<@${target.id}> has invited **${uses}** member${uses !== 1 ? 's' : ''} to the server.`)
                    .setFooter({ text: 'MostlyVanilla Beacon' })
                    .setTimestamp(),
            ],
        });
    }

    const embed = await buildInviteEmbed(interaction.guild);
    if (!embed) return interaction.editReply({ content: 'No invite data found — no invites have been used yet.' });

    const message = await interaction.editReply({ embeds: [embed] });

    db.setSetting('invite_board_channel_id', interaction.channelId);
    db.setSetting('invite_board_message_id', message.id);
    console.log(`[Invites] Live board registered — channel ${interaction.channelId}, message ${message.id}`);

    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(() => updateLiveBoard(interaction.client), UPDATE_MS);
}

// ── /invitewipe ────────────────────────────────────────────────────────────

const invitewipeData = new SlashCommandBuilder()
    .setName('invitewipe')
    .setDescription('Remove a user from the invite leaderboard.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addUserOption(o => o.setName('user').setDescription('User to remove from the leaderboard.').setRequired(true));

async function invitewipeExecute(interaction) {
    const target = interaction.options.getUser('user');

    if (db.inviteWipes.isWiped(interaction.guildId, target.id))
        return interaction.reply({ embeds: [warning('Already wiped', `**${target.tag}** is already hidden from the leaderboard.`)], ephemeral: true });

    db.inviteWipes.add(interaction.guildId, target.id);
    await updateLiveBoard(interaction.client);
    await interaction.reply({ embeds: [success('Wiped', `**${target.tag}** has been removed from the invite leaderboard.`)], ephemeral: true });
}

// ── /inviterestore ─────────────────────────────────────────────────────────

const inviterestoreData = new SlashCommandBuilder()
    .setName('inviterestore')
    .setDescription('Restore a wiped user back onto the invite leaderboard.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addUserOption(o => o.setName('user').setDescription('User to restore to the leaderboard.').setRequired(true));

async function inviterestoreExecute(interaction) {
    const target = interaction.options.getUser('user');

    if (!db.inviteWipes.isWiped(interaction.guildId, target.id))
        return interaction.reply({ embeds: [warning('Not wiped', `**${target.tag}** is not currently hidden from the leaderboard.`)], ephemeral: true });

    db.inviteWipes.remove(interaction.guildId, target.id);
    await updateLiveBoard(interaction.client);
    await interaction.reply({ embeds: [success('Restored', `**${target.tag}** has been restored to the invite leaderboard.`)], ephemeral: true });
}

module.exports = { data, execute, startLiveBoard, invitewipeData, invitewipeExecute, inviterestoreData, inviterestoreExecute };
