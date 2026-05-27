const {
    SlashCommandBuilder, PermissionFlagsBits, ChannelType,
    EmbedBuilder,
} = require('discord.js');
const { success, error, warning, info } = require('../ticketUtils');
const db = require('../database');

// ── Duration helpers ──────────────────────────────────────────────────────────

function parseDuration(str) {
    const match = str.match(/^(\d+)\s*(s|m|h|d|w)$/i);
    if (!match) return null;
    const amount = parseInt(match[1], 10);
    const mult   = { s: 1000, m: 60000, h: 3600000, d: 86400000, w: 604800000 };
    return amount * mult[match[2].toLowerCase()];
}

function formatDuration(ms) {
    let s = Math.floor(ms / 1000);
    const parts = [];
    if (s >= 604800) { parts.push(`${Math.floor(s / 604800)}w`); s %= 604800; }
    if (s >= 86400)  { parts.push(`${Math.floor(s / 86400)}d`);  s %= 86400;  }
    if (s >= 3600)   { parts.push(`${Math.floor(s / 3600)}h`);   s %= 3600;   }
    if (s >= 60)     { parts.push(`${Math.floor(s / 60)}m`);     s %= 60;     }
    if (s > 0)       parts.push(`${s}s`);
    return parts.join(' ') || '0s';
}

function formatSeconds(s) {
    const parts = [];
    if (s >= 3600) { parts.push(`${Math.floor(s / 3600)}h`); s %= 3600; }
    if (s >= 60)   { parts.push(`${Math.floor(s / 60)}m`);   s %= 60;   }
    if (s > 0)     parts.push(`${s}s`);
    return parts.join(' ') || '0s';
}

// ── Mod log ───────────────────────────────────────────────────────────────────

async function sendModLog(client, guildId, { action, emoji, target, mod, reason, duration, caseId, color }) {
    const channelId = db.getSetting(`mod_log_${guildId}`);
    if (!channelId) return;
    const ch = await client.channels.fetch(channelId).catch(() => null);
    if (!ch) return;

    const embed = new EmbedBuilder()
        .setColor(color ?? 0x5865F2)
        .setTitle(`${emoji} ${action} — Case #${caseId}`)
        .addFields(
            { name: 'User',      value: `${target} (${target.id})`, inline: true },
            { name: 'Moderator', value: `${mod} (${mod.id})`,       inline: true },
            { name: 'Reason',    value: reason || 'No reason given' },
        )
        .setThumbnail(target.displayAvatarURL())
        .setTimestamp();

    if (duration) embed.addFields({ name: 'Duration', value: formatDuration(duration), inline: true });

    await ch.send({ embeds: [embed] }).catch(err => console.error('[ModLog]', err.message));
}

// ── /ban ──────────────────────────────────────────────────────────────────────

const banData = new SlashCommandBuilder()
    .setName('ban')
    .setDescription('Ban a user from the server.')
    .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers)
    .addUserOption(o => o.setName('user').setDescription('User to ban.').setRequired(true))
    .addStringOption(o => o.setName('reason').setDescription('Reason for the ban.'))
    .addIntegerOption(o => o.setName('delete_days').setDescription('Days of messages to delete (0–7).').setMinValue(0).setMaxValue(7));

async function banExecute(interaction) {
    const targetMember = interaction.options.getMember('user');
    const targetUser   = interaction.options.getUser('user');
    const reason       = interaction.options.getString('reason') ?? 'No reason given';
    const deleteDays   = interaction.options.getInteger('delete_days') ?? 0;

    if (targetUser.id === interaction.user.id)
        return interaction.reply({ embeds: [error('Invalid', 'You cannot ban yourself.')], ephemeral: true });
    if (targetUser.id === interaction.client.user.id)
        return interaction.reply({ embeds: [error('Invalid', 'I cannot ban myself.')], ephemeral: true });
    if (targetMember && !targetMember.bannable)
        return interaction.reply({ embeds: [error('Cannot ban', 'I do not have permission to ban that user (they may outrank me).')], ephemeral: true });

    if (targetMember) {
        await targetMember.send({
            embeds: [new EmbedBuilder().setColor(0xED4245)
                .setTitle(`You have been banned from ${interaction.guild.name}`)
                .addFields({ name: 'Reason', value: reason })
                .setTimestamp()],
        }).catch(() => {});
    }

    await interaction.guild.members.ban(targetUser, { reason, deleteMessageDays: deleteDays });

    const caseId = db.modCases.create.run({
        guild_id: interaction.guildId, user_id: targetUser.id,
        mod_id: interaction.user.id, action: 'BAN', reason, duration: null,
    }).lastInsertRowid;

    await sendModLog(interaction.client, interaction.guildId, {
        action: 'Ban', emoji: '🔨', target: targetUser, mod: interaction.user,
        reason, caseId, color: 0xED4245,
    });

    await interaction.reply({
        embeds: [success('Banned', `**${targetUser.tag}** has been banned.\n**Reason:** ${reason}`)],
        ephemeral: true,
    });
}

// ── /unban ────────────────────────────────────────────────────────────────────

const unbanData = new SlashCommandBuilder()
    .setName('unban')
    .setDescription('Unban a user by their ID.')
    .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers)
    .addStringOption(o => o.setName('user_id').setDescription('The user ID to unban.').setRequired(true))
    .addStringOption(o => o.setName('reason').setDescription('Reason for the unban.'));

async function unbanExecute(interaction) {
    const userId = interaction.options.getString('user_id').trim();
    const reason = interaction.options.getString('reason') ?? 'No reason given';

    if (!/^\d{17,20}$/.test(userId))
        return interaction.reply({ embeds: [error('Invalid ID', 'Please provide a valid Discord user ID.')], ephemeral: true });

    const ban = await interaction.guild.bans.fetch(userId).catch(() => null);
    if (!ban)
        return interaction.reply({ embeds: [error('Not banned', 'That user does not have an active ban.')], ephemeral: true });

    await interaction.guild.bans.remove(userId, reason);

    const caseId = db.modCases.create.run({
        guild_id: interaction.guildId, user_id: userId,
        mod_id: interaction.user.id, action: 'UNBAN', reason, duration: null,
    }).lastInsertRowid;

    await sendModLog(interaction.client, interaction.guildId, {
        action: 'Unban', emoji: '✅', target: ban.user, mod: interaction.user,
        reason, caseId, color: 0x57F287,
    });

    await interaction.reply({
        embeds: [success('Unbanned', `**${ban.user.tag}** has been unbanned.\n**Reason:** ${reason}`)],
        ephemeral: true,
    });
}

// ── /kick ─────────────────────────────────────────────────────────────────────

const kickData = new SlashCommandBuilder()
    .setName('kick')
    .setDescription('Kick a member from the server.')
    .setDefaultMemberPermissions(PermissionFlagsBits.KickMembers)
    .addUserOption(o => o.setName('user').setDescription('User to kick.').setRequired(true))
    .addStringOption(o => o.setName('reason').setDescription('Reason for the kick.'));

async function kickExecute(interaction) {
    const target = interaction.options.getMember('user');
    const reason = interaction.options.getString('reason') ?? 'No reason given';

    if (!target)
        return interaction.reply({ embeds: [error('Not found', 'That user is not in this server.')], ephemeral: true });
    if (target.id === interaction.user.id)
        return interaction.reply({ embeds: [error('Invalid', 'You cannot kick yourself.')], ephemeral: true });
    if (!target.kickable)
        return interaction.reply({ embeds: [error('Cannot kick', 'I do not have permission to kick that user.')], ephemeral: true });

    await target.send({
        embeds: [new EmbedBuilder().setColor(0xFEE75C)
            .setTitle(`You have been kicked from ${interaction.guild.name}`)
            .addFields({ name: 'Reason', value: reason })
            .setTimestamp()],
    }).catch(() => {});

    await target.kick(reason);

    const caseId = db.modCases.create.run({
        guild_id: interaction.guildId, user_id: target.id,
        mod_id: interaction.user.id, action: 'KICK', reason, duration: null,
    }).lastInsertRowid;

    await sendModLog(interaction.client, interaction.guildId, {
        action: 'Kick', emoji: '👢', target: target.user, mod: interaction.user,
        reason, caseId, color: 0xFEE75C,
    });

    await interaction.reply({
        embeds: [success('Kicked', `**${target.user.tag}** has been kicked.\n**Reason:** ${reason}`)],
        ephemeral: true,
    });
}

// ── /mute ─────────────────────────────────────────────────────────────────────

const muteData = new SlashCommandBuilder()
    .setName('mute')
    .setDescription('Timeout (mute) a member.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers)
    .addUserOption(o => o.setName('user').setDescription('User to mute.').setRequired(true))
    .addStringOption(o => o.setName('duration').setDescription('Duration: e.g. 10m, 2h, 7d. Max 28d.').setRequired(true))
    .addStringOption(o => o.setName('reason').setDescription('Reason for the mute.'));

async function muteExecute(interaction) {
    const target      = interaction.options.getMember('user');
    const durationStr = interaction.options.getString('duration');
    const reason      = interaction.options.getString('reason') ?? 'No reason given';

    if (!target)
        return interaction.reply({ embeds: [error('Not found', 'That user is not in this server.')], ephemeral: true });
    if (target.id === interaction.user.id)
        return interaction.reply({ embeds: [error('Invalid', 'You cannot mute yourself.')], ephemeral: true });
    if (!target.moderatable)
        return interaction.reply({ embeds: [error('Cannot mute', 'I do not have permission to mute that user.')], ephemeral: true });

    const durationMs = parseDuration(durationStr);
    if (!durationMs)
        return interaction.reply({ embeds: [error('Invalid duration', 'Use a format like `10m`, `2h`, `7d`, `1w`.')], ephemeral: true });
    if (durationMs > 28 * 24 * 60 * 60 * 1000)
        return interaction.reply({ embeds: [error('Too long', 'Maximum mute duration is 28 days.')], ephemeral: true });

    await target.timeout(durationMs, reason);

    await target.send({
        embeds: [new EmbedBuilder().setColor(0xFEE75C)
            .setTitle(`You have been muted in ${interaction.guild.name}`)
            .addFields(
                { name: 'Duration', value: formatDuration(durationMs), inline: true },
                { name: 'Reason',   value: reason,                     inline: true },
            )
            .setTimestamp()],
    }).catch(() => {});

    const caseId = db.modCases.create.run({
        guild_id: interaction.guildId, user_id: target.id,
        mod_id: interaction.user.id, action: 'MUTE', reason, duration: durationMs,
    }).lastInsertRowid;

    await sendModLog(interaction.client, interaction.guildId, {
        action: 'Mute', emoji: '🔇', target: target.user, mod: interaction.user,
        reason, duration: durationMs, caseId, color: 0xFEE75C,
    });

    await interaction.reply({
        embeds: [success('Muted', `**${target.user.tag}** has been muted for **${formatDuration(durationMs)}**.\n**Reason:** ${reason}`)],
        ephemeral: true,
    });
}

// ── /unmute ───────────────────────────────────────────────────────────────────

const unmuteData = new SlashCommandBuilder()
    .setName('unmute')
    .setDescription('Remove a timeout from a member.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers)
    .addUserOption(o => o.setName('user').setDescription('User to unmute.').setRequired(true));

async function unmuteExecute(interaction) {
    const target = interaction.options.getMember('user');

    if (!target)
        return interaction.reply({ embeds: [error('Not found', 'That user is not in this server.')], ephemeral: true });
    if (!target.isCommunicationDisabled())
        return interaction.reply({ embeds: [warning('Not muted', 'That user is not currently muted.')], ephemeral: true });

    await target.timeout(null);

    const caseId = db.modCases.create.run({
        guild_id: interaction.guildId, user_id: target.id,
        mod_id: interaction.user.id, action: 'UNMUTE', reason: 'Mute removed', duration: null,
    }).lastInsertRowid;

    await sendModLog(interaction.client, interaction.guildId, {
        action: 'Unmute', emoji: '🔊', target: target.user, mod: interaction.user,
        reason: 'Mute removed', caseId, color: 0x57F287,
    });

    await interaction.reply({
        embeds: [success('Unmuted', `**${target.user.tag}** has been unmuted.`)],
        ephemeral: true,
    });
}

// ── /warn ─────────────────────────────────────────────────────────────────────

const warnData = new SlashCommandBuilder()
    .setName('warn')
    .setDescription('Issue a warning to a member.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers)
    .addUserOption(o => o.setName('user').setDescription('User to warn.').setRequired(true))
    .addStringOption(o => o.setName('reason').setDescription('Reason for the warning.').setRequired(true));

async function warnExecute(interaction) {
    const target = interaction.options.getMember('user');
    const reason = interaction.options.getString('reason');

    if (!target)
        return interaction.reply({ embeds: [error('Not found', 'That user is not in this server.')], ephemeral: true });
    if (target.id === interaction.user.id)
        return interaction.reply({ embeds: [error('Invalid', 'You cannot warn yourself.')], ephemeral: true });

    db.warnings.create.run({
        guild_id: interaction.guildId, user_id: target.id,
        mod_id: interaction.user.id, reason,
    });

    const count = db.warnings.countUser.get(interaction.guildId, target.id)?.count ?? 1;

    await target.send({
        embeds: [new EmbedBuilder().setColor(0xFEE75C)
            .setTitle(`⚠️ Warning from ${interaction.guild.name}`)
            .setDescription(`You have received a warning. This is your **${count}** warning.`)
            .addFields({ name: 'Reason', value: reason })
            .setTimestamp()],
    }).catch(() => {});

    const caseId = db.modCases.create.run({
        guild_id: interaction.guildId, user_id: target.id,
        mod_id: interaction.user.id, action: 'WARN', reason, duration: null,
    }).lastInsertRowid;

    await sendModLog(interaction.client, interaction.guildId, {
        action: 'Warn', emoji: '⚠️', target: target.user, mod: interaction.user,
        reason, caseId, color: 0xFEE75C,
    });

    await interaction.reply({
        embeds: [success('Warned', `**${target.user.tag}** has been warned (warning #${count}).\n**Reason:** ${reason}`)],
        ephemeral: true,
    });
}

// ── /warnings ─────────────────────────────────────────────────────────────────

const warningsData = new SlashCommandBuilder()
    .setName('warnings')
    .setDescription("View a member's warnings.")
    .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers)
    .addUserOption(o => o.setName('user').setDescription('User to check.').setRequired(true));

async function warningsExecute(interaction) {
    const target = interaction.options.getUser('user');
    const warns  = db.warnings.getUser.all(interaction.guildId, target.id);

    if (!warns.length)
        return interaction.reply({ embeds: [info('No warnings', `**${target.tag}** has no warnings on record.`)], ephemeral: true });

    const embed = new EmbedBuilder()
        .setColor(0xFEE75C)
        .setTitle(`⚠️ Warnings — ${target.tag}`)
        .setThumbnail(target.displayAvatarURL())
        .setDescription(
            warns.map((w, i) => {
                const date = `<t:${w.created_at}:d>`;
                return `**${i + 1}.** ${w.reason}\n└ by <@${w.mod_id}> · ${date}`;
            }).join('\n\n')
        )
        .setFooter({ text: `${warns.length} warning${warns.length !== 1 ? 's' : ''} total` });

    await interaction.reply({ embeds: [embed], ephemeral: true });
}

// ── /clearwarnings ────────────────────────────────────────────────────────────

const clearwarningsData = new SlashCommandBuilder()
    .setName('clearwarnings')
    .setDescription("Clear all warnings for a member.")
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addUserOption(o => o.setName('user').setDescription('User to clear warnings for.').setRequired(true));

async function clearwarningsExecute(interaction) {
    const target = interaction.options.getUser('user');
    const count  = db.warnings.countUser.get(interaction.guildId, target.id)?.count ?? 0;

    if (!count)
        return interaction.reply({ embeds: [info('No warnings', `**${target.tag}** has no warnings to clear.`)], ephemeral: true });

    db.warnings.clearUser.run(interaction.guildId, target.id);

    await interaction.reply({
        embeds: [success('Cleared', `Cleared **${count}** warning${count !== 1 ? 's' : ''} for **${target.tag}**.`)],
        ephemeral: true,
    });
}

// ── /modhistory ───────────────────────────────────────────────────────────────

const modhistoryData = new SlashCommandBuilder()
    .setName('modhistory')
    .setDescription("View a member's moderation history.")
    .setDefaultMemberPermissions(PermissionFlagsBits.ModerateMembers)
    .addUserOption(o => o.setName('user').setDescription('User to look up.').setRequired(true));

async function modhistoryExecute(interaction) {
    const target = interaction.options.getUser('user');
    const cases  = db.modCases.getUser.all(interaction.guildId, target.id);

    if (!cases.length)
        return interaction.reply({ embeds: [info('No history', `**${target.tag}** has no moderation history.`)], ephemeral: true });

    const actionEmoji = { BAN: '🔨', UNBAN: '✅', KICK: '👢', MUTE: '🔇', UNMUTE: '🔊', WARN: '⚠️' };

    const embed = new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle(`📋 Mod History — ${target.tag}`)
        .setThumbnail(target.displayAvatarURL())
        .setDescription(
            cases.map(c => {
                const emoji    = actionEmoji[c.action] ?? '•';
                const date     = `<t:${c.created_at}:d>`;
                const duration = c.duration ? ` (${formatDuration(c.duration)})` : '';
                return `**Case #${c.id}** ${emoji} ${c.action}${duration} · ${date}\n└ ${c.reason || 'No reason'} · by <@${c.mod_id}>`;
            }).join('\n\n')
        )
        .setFooter({ text: `${cases.length} total action${cases.length !== 1 ? 's' : ''}` });

    await interaction.reply({ embeds: [embed], ephemeral: true });
}

// ── /purge ────────────────────────────────────────────────────────────────────

const purgeData = new SlashCommandBuilder()
    .setName('purge')
    .setDescription('Bulk-delete messages from this channel.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageMessages)
    .addIntegerOption(o => o.setName('amount').setDescription('Number of messages to delete (1–100).').setRequired(true).setMinValue(1).setMaxValue(100))
    .addUserOption(o => o.setName('user').setDescription('Only delete messages from this user.'));

async function purgeExecute(interaction) {
    const amount     = interaction.options.getInteger('amount');
    const filterUser = interaction.options.getUser('user');

    await interaction.deferReply({ ephemeral: true });

    const fetched = await interaction.channel.messages.fetch({ limit: filterUser ? 100 : amount }).catch(() => null);
    if (!fetched) return interaction.editReply({ embeds: [error('Error', 'Could not fetch messages.')] });

    const twoWeeksAgo = Date.now() - 14 * 24 * 60 * 60 * 1000;
    let toDelete = [...fetched.values()].filter(m => m.createdTimestamp > twoWeeksAgo);
    if (filterUser) toDelete = toDelete.filter(m => m.author.id === filterUser.id).slice(0, amount);

    if (!toDelete.length)
        return interaction.editReply({ embeds: [warning('No messages', 'No messages found to delete (messages must be under 14 days old).')] });

    const deleted = await interaction.channel.bulkDelete(toDelete, true).catch(() => null);
    const count   = deleted?.size ?? toDelete.length;

    await interaction.editReply({
        embeds: [success('Purged', `Deleted **${count}** message${count !== 1 ? 's' : ''}${filterUser ? ` from **${filterUser.tag}**` : ''}.`)],
    });
}

// ── /slowmode ─────────────────────────────────────────────────────────────────

const slowmodeData = new SlashCommandBuilder()
    .setName('slowmode')
    .setDescription('Set the slowmode for this channel.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageChannels)
    .addIntegerOption(o => o.setName('seconds').setDescription('Slowmode in seconds (0 to disable, max 21600).').setRequired(true).setMinValue(0).setMaxValue(21600));

async function slowmodeExecute(interaction) {
    const seconds = interaction.options.getInteger('seconds');
    await interaction.channel.setRateLimitPerUser(seconds);

    if (seconds === 0)
        return interaction.reply({ embeds: [success('Slowmode disabled', 'Slowmode has been disabled in this channel.')], ephemeral: true });

    await interaction.reply({
        embeds: [success('Slowmode set', `Slowmode set to **${formatSeconds(seconds)}** in this channel.`)],
        ephemeral: true,
    });
}

// ── /lock / /unlock ───────────────────────────────────────────────────────────

const lockData = new SlashCommandBuilder()
    .setName('lock')
    .setDescription('Prevent @everyone from sending messages in this channel.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageChannels)
    .addStringOption(o => o.setName('reason').setDescription('Reason for the lock.'));

async function lockExecute(interaction) {
    const reason = interaction.options.getString('reason') ?? 'No reason given';
    await interaction.channel.permissionOverwrites.edit(interaction.guild.roles.everyone, {
        SendMessages: false,
    });
    await interaction.reply({
        embeds: [new EmbedBuilder().setColor(0xED4245)
            .setTitle('🔒 Channel Locked')
            .setDescription(`This channel has been locked.\n**Reason:** ${reason}`)],
    });
}

const unlockData = new SlashCommandBuilder()
    .setName('unlock')
    .setDescription('Restore @everyone message permissions in this channel.')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageChannels);

async function unlockExecute(interaction) {
    await interaction.channel.permissionOverwrites.edit(interaction.guild.roles.everyone, {
        SendMessages: null,
    });
    await interaction.reply({
        embeds: [new EmbedBuilder().setColor(0x57F287)
            .setTitle('🔓 Channel Unlocked')
            .setDescription('This channel has been unlocked.')],
    });
}

// ── /modlog ───────────────────────────────────────────────────────────────────

const modlogData = new SlashCommandBuilder()
    .setName('modlog')
    .setDescription('Set the channel where moderation actions are logged.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addChannelOption(o => o.setName('channel').setDescription('Channel to send mod log entries to.').addChannelTypes(ChannelType.GuildText).setRequired(true));

async function modlogExecute(interaction) {
    const channel = interaction.options.getChannel('channel');
    db.setSetting(`mod_log_${interaction.guildId}`, channel.id);
    await interaction.reply({
        embeds: [success('Mod Log Set', `Moderation actions will now be logged to ${channel}.`)],
        ephemeral: true,
    });
}

// ── Exports ───────────────────────────────────────────────────────────────────

module.exports = {
    banData, banExecute,
    unbanData, unbanExecute,
    kickData, kickExecute,
    muteData, muteExecute,
    unmuteData, unmuteExecute,
    warnData, warnExecute,
    warningsData, warningsExecute,
    clearwarningsData, clearwarningsExecute,
    modhistoryData, modhistoryExecute,
    purgeData, purgeExecute,
    slowmodeData, slowmodeExecute,
    lockData, lockExecute,
    unlockData, unlockExecute,
    modlogData, modlogExecute,
};
