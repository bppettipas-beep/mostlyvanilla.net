const {
    SlashCommandBuilder, PermissionFlagsBits,
    EmbedBuilder, ChannelType,
} = require('discord.js');
const db = require('../database');
const { success, error, info } = require('../ticketUtils');

const CHUNK_SIZE = 20;

const data = new SlashCommandBuilder()
    .setName('chatlog')
    .setDescription('Manage in-game chat logs.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addSubcommand(sub =>
        sub.setName('setchannel')
           .setDescription('Set a channel to stream every in-game chat message live.')
           .addChannelOption(o => o
               .setName('channel')
               .setDescription('Channel to stream chat to.')
               .setRequired(true)
               .addChannelTypes(ChannelType.GuildText)
           )
    )
    .addSubcommand(sub =>
        sub.setName('clearchannel')
           .setDescription('Stop streaming live in-game chat to Discord.')
    )
    .addSubcommand(sub =>
        sub.setName('view')
           .setDescription('Dump stored in-game chat logs to this channel.')
           .addIntegerOption(o => o
               .setName('limit')
               .setDescription('Number of messages to show (default 50, max 200).')
               .setMinValue(1)
               .setMaxValue(200)
           )
           .addStringOption(o => o
               .setName('player')
               .setDescription('Filter by player name (case-insensitive).')
           )
           .addIntegerOption(o => o
               .setName('hours')
               .setDescription('How many hours back to look (default 24, max 168).')
               .setMinValue(1)
               .setMaxValue(168)
           )
    );

async function execute(interaction) {
    const sub     = interaction.options.getSubcommand();
    const guildId = interaction.guildId;

    if (sub === 'setchannel') {
        const channel = interaction.options.getChannel('channel');
        db.setSetting(`chatlog_channel_${guildId}`, channel.id);
        return interaction.reply({
            embeds: [success('Chat Log Channel Set', `In-game chat will now stream live to ${channel}.\n\nEach message shows the timestamp, player name, role, world, and coordinates.`)],
            ephemeral: true,
        });
    }

    if (sub === 'clearchannel') {
        db.deleteSetting(`chatlog_channel_${guildId}`);
        return interaction.reply({
            embeds: [success('Chat Log Disabled', 'Live in-game chat streaming has been turned off.')],
            ephemeral: true,
        });
    }

    if (sub === 'view') {
        await interaction.deferReply({ ephemeral: true });

        const limit  = interaction.options.getInteger('limit') ?? 50;
        const player = interaction.options.getString('player');
        const hours  = interaction.options.getInteger('hours') ?? 24;
        const since  = Math.floor(Date.now() / 1000) - hours * 3600;

        const rows = player
            ? db.chatLogs.getByPlayer.all(guildId, player, since, limit)
            : db.chatLogs.getRecent.all(guildId, since, limit);

        if (!rows.length) {
            const desc = `No chat messages found in the last ${hours} hour${hours !== 1 ? 's' : ''}${player ? ` for **${player}**` : ''}.`;
            return interaction.editReply({ embeds: [info('No Logs Found', desc)] });
        }

        // Split into pages of CHUNK_SIZE messages each
        const pages = [];
        for (let i = 0; i < rows.length; i += CHUNK_SIZE) pages.push(rows.slice(i, i + CHUNK_SIZE));

        for (const [idx, chunk] of pages.entries()) {
            const lines = chunk.map(r => {
                const ts      = new Date(r.timestamp * 1000);
                const timeStr = ts.toISOString().slice(0, 19).replace('T', ' ') + 'Z';
                const roleTag = r.player_role ? `[${r.player_role}] ` : '';
                const loc     = (r.world && r.x != null) ? ` (${r.world} ${r.x},${r.y},${r.z})` : '';
                // Truncate very long messages so the embed doesn't overflow
                const msg = r.message.length > 120 ? r.message.slice(0, 117) + '...' : r.message;
                return `[${timeStr}] ${roleTag}${r.player_name}${loc}: ${msg}`;
            });

            const embed = new EmbedBuilder()
                .setColor(0x2ECC71)
                .setDescription('```\n' + lines.join('\n') + '\n```')
                .setFooter({ text: `Page ${idx + 1}/${pages.length} · ${rows.length} message${rows.length !== 1 ? 's' : ''} · last ${hours}h${player ? ` · ${player}` : ''}` });

            if (idx === 0) {
                embed.setTitle('📋 In-Game Chat Log');
            }

            await interaction.channel.send({ embeds: [embed] });
        }

        return interaction.editReply({ content: `✅ Posted ${rows.length} chat log entr${rows.length !== 1 ? 'ies' : 'y'} above.` });
    }
}

module.exports = { data, execute };
