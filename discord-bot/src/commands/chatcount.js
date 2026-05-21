const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const db = require('../database');

const UPDATE_MS = 5 * 60 * 1000;
let liveInterval = null;

// ── Embed builder ──────────────────────────────────────────────────────────

function buildChatEmbed(guildId) {
    const rows = db.chatCounts.getTop.all(guildId);
    if (!rows.length) return null;

    const medals = ['🥇', '🥈', '🥉'];
    const lines = rows.map(({ user_id, count }, i) => {
        const rank = medals[i] ?? `\`#${i + 1}\``;
        return `${rank} <@${user_id}> — **${count.toLocaleString()}** message${count !== 1 ? 's' : ''}`;
    }).join('\n');

    const total = rows.reduce((sum, r) => sum + r.count, 0);
    const updatedAt = new Date().toLocaleString('en-US', {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
    });

    return new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('💬 Chat Leaderboard')
        .setDescription(lines)
        .addFields({ name: 'Total Messages', value: `**${total.toLocaleString()}** tracked messages` })
        .setFooter({ text: `MostlyVanilla Beacon • Updates every 5 min • Last updated ${updatedAt}` })
        .setTimestamp();
}

// ── Live updater ───────────────────────────────────────────────────────────

async function updateChatBoard(client) {
    const channelId = db.getSetting('chat_board_channel_id');
    const messageId = db.getSetting('chat_board_message_id');
    if (!channelId || !messageId) return;

    try {
        const channel = await client.channels.fetch(channelId).catch(() => null);
        if (!channel) return;

        const message = await channel.messages.fetch(messageId).catch(() => null);
        if (!message) {
            db.deleteSetting('chat_board_channel_id');
            db.deleteSetting('chat_board_message_id');
            console.log('[ChatCount] Live board message was deleted — cleared saved IDs.');
            return;
        }

        const embed = buildChatEmbed(channel.guild.id);
        if (!embed) return;

        await message.edit({ embeds: [embed] });
    } catch (err) {
        console.error('[ChatCount] Live update failed:', err.message);
    }
}

function startChatBoard(client) {
    if (liveInterval) clearInterval(liveInterval);

    const channelId = db.getSetting('chat_board_channel_id');
    if (!channelId) return;

    console.log('[ChatCount] Restoring live leaderboard — updating immediately then every 5 min.');
    updateChatBoard(client);
    liveInterval = setInterval(() => updateChatBoard(client), UPDATE_MS);
}

function incrementChat(guildId, userId) {
    db.chatCounts.increment(guildId, userId);
    // Update live board on next interval — no need to refresh on every message
}

// ── Slash command ──────────────────────────────────────────────────────────

const data = new SlashCommandBuilder()
    .setName('chatcount')
    .setDescription('Show the server chat message leaderboard')
    .addUserOption(opt =>
        opt.setName('user')
           .setDescription("Check a specific user's message count")
           .setRequired(false)
    );

async function execute(interaction) {
    await interaction.deferReply();

    const target = interaction.options.getUser('user');

    if (target) {
        const row = db.chatCounts.getUser.get(interaction.guildId, target.id);
        const count = row?.count ?? 0;
        return interaction.editReply({
            embeds: [
                new EmbedBuilder()
                    .setColor(0x5865F2)
                    .setTitle('💬 Message Count')
                    .setThumbnail(target.displayAvatarURL({ dynamic: true }))
                    .setDescription(`<@${target.id}> has sent **${count.toLocaleString()}** message${count !== 1 ? 's' : ''} in the server.`)
                    .setFooter({ text: 'MostlyVanilla Beacon' })
                    .setTimestamp(),
            ],
        });
    }

    const embed = buildChatEmbed(interaction.guildId);
    if (!embed) return interaction.editReply({ content: 'No chat data yet — messages need to be sent first.' });

    const message = await interaction.editReply({ embeds: [embed] });

    db.setSetting('chat_board_channel_id', interaction.channelId);
    db.setSetting('chat_board_message_id', message.id);
    console.log(`[ChatCount] Live board registered — channel ${interaction.channelId}, message ${message.id}`);

    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(() => updateChatBoard(interaction.client), UPDATE_MS);
}

module.exports = { data, execute, startChatBoard, incrementChat };
