const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');

const data = new SlashCommandBuilder()
    .setName('invites')
    .setDescription('Show the server invite leaderboard')
    .addUserOption(opt =>
        opt.setName('user')
           .setDescription('Check a specific user\'s invite count')
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

    // Aggregate uses per inviter
    const counts = new Map(); // userId → { username, uses }
    for (const invite of invites.values()) {
        if (!invite.inviter || !invite.uses) continue;
        const id = invite.inviter.id;
        if (!counts.has(id)) counts.set(id, { username: invite.inviter.username, uses: 0 });
        counts.get(id).uses += invite.uses;
    }

    const target = interaction.options.getUser('user');

    if (target) {
        // Single-user lookup
        const entry = counts.get(target.id);
        const uses  = entry?.uses ?? 0;
        const embed = new EmbedBuilder()
            .setColor(0x2ECC71)
            .setTitle('🔗 Invite Count')
            .setThumbnail(target.displayAvatarURL({ dynamic: true }))
            .setDescription(`<@${target.id}> has invited **${uses}** member${uses !== 1 ? 's' : ''} to the server.`)
            .setFooter({ text: 'Mostly Vanilla' })
            .setTimestamp();
        return interaction.editReply({ embeds: [embed] });
    }

    // Full leaderboard
    const sorted = [...counts.entries()]
        .sort((a, b) => b[1].uses - a[1].uses)
        .slice(0, 15);

    if (sorted.length === 0) {
        return interaction.editReply({ content: 'No invite data found — no invites have been used yet.' });
    }

    const medals = ['🥇', '🥈', '🥉'];
    const lines = sorted.map(([id, v], i) => {
        const rank = medals[i] ?? `\`#${i + 1}\``;
        return `${rank} <@${id}> — **${v.uses}** invite${v.uses !== 1 ? 's' : ''}`;
    }).join('\n');

    const total = sorted.reduce((sum, [, v]) => sum + v.uses, 0);

    const embed = new EmbedBuilder()
        .setColor(0x2ECC71)
        .setTitle('🔗 Invite Leaderboard')
        .setDescription(lines)
        .addFields({ name: 'Total Invites', value: `**${total}** across all tracked links`, inline: false })
        .setFooter({ text: `Mostly Vanilla • Top ${sorted.length} inviters` })
        .setTimestamp();

    return interaction.editReply({ embeds: [embed] });
}

module.exports = { data, execute };
