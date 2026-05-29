const {
    SlashCommandBuilder, EmbedBuilder,
    PermissionFlagsBits, ChannelType, Role,
} = require('discord.js');
const db = require('../database');
const { success, error, warning, info } = require('../ticketUtils');

const UPDATE_MS = 15 * 1000;
let liveInterval = null;
let wipeTimeout  = null;

// ── Interval helpers ──────────────────────────────────────────────────────────

function parseInterval(str) {
    // mo must come before m so "1mo" doesn't match as "1m + o"
    const match = str.trim().match(/^(\d+(?:\.\d+)?)(mo|y|w|d|h|m|s)$/i);
    if (!match) return null;
    const n    = parseFloat(match[1]);
    const unit = match[2].toLowerCase();
    const map  = { s: 1000, m: 60000, h: 3600000, d: 86400000, w: 604800000, mo: 2592000000, y: 31536000000 };
    return Math.round(n * map[unit]);
}

function formatMs(ms) {
    const units = [
        { label: 'year',   div: 31536000000 },
        { label: 'month',  div: 2592000000  },
        { label: 'week',   div: 604800000   },
        { label: 'day',    div: 86400000    },
        { label: 'hour',   div: 3600000     },
        { label: 'minute', div: 60000       },
        { label: 'second', div: 1000        },
    ];
    for (const u of units) {
        if (ms >= u.div) {
            const n = Math.round(ms / u.div);
            return `${n} ${u.label}${n !== 1 ? 's' : ''}`;
        }
    }
    return `${ms}ms`;
}

// ── Leaderboard embed ─────────────────────────────────────────────────────────

function buildStrikeBoard(guild) {
    const rows = db.strikes.getLeaderboard.all(guild.id);

    const updatedAt = new Date().toLocaleString('en-US', {
        month: 'short', day: 'numeric',
        hour: '2-digit', minute: '2-digit',
        second: '2-digit',
    });

    if (!rows.length) {
        return new EmbedBuilder()
            .setColor(0xE74C3C)
            .setTitle('⚡ Staff Strike Leaderboard')
            .setDescription('*No strikes have been issued.*')
            .setFooter({ text: `MostlyVanilla Beacon • Last updated ${updatedAt}` })
            .setTimestamp();
    }

    const medals = ['🥇', '🥈', '🥉'];
    const lines  = rows.map((r, i) => {
        const rank = medals[i] ?? `\`#${i + 1}\``;
        const date = `<t:${r.last_at}:d>`;
        return `${rank} <@${r.user_id}> — **${r.count}** strike${r.count !== 1 ? 's' : ''}\n└ Last: *${r.last_reason}* · ${date}`;
    }).join('\n\n');

    const total = rows.reduce((s, r) => s + r.count, 0);

    return new EmbedBuilder()
        .setColor(0xE74C3C)
        .setTitle('⚡ Staff Strike Leaderboard')
        .setDescription(lines)
        .addFields({ name: 'Total Strikes Issued', value: `**${total}** total` })
        .setFooter({ text: `MostlyVanilla Beacon • Last updated ${updatedAt}` })
        .setTimestamp();
}

// ── Live board updater ────────────────────────────────────────────────────────

async function updateStrikeBoard(client) {
    const channelId = db.getSetting('strike_board_channel_id');
    const messageId = db.getSetting('strike_board_message_id');
    if (!channelId || !messageId) return;

    try {
        const channel = await client.channels.fetch(channelId).catch(() => null);
        if (!channel) return;

        const message = await channel.messages.fetch(messageId).catch(() => null);
        if (!message) {
            db.deleteSetting('strike_board_channel_id');
            db.deleteSetting('strike_board_message_id');
            console.log('[Strike] Live board message deleted — cleared saved IDs.');
            return;
        }

        const embed = buildStrikeBoard(channel.guild);
        await message.edit({ embeds: [embed] });
    } catch (err) {
        console.error('[Strike] Live board update failed:', err.message);
    }
}

function startStrikeBoard(client) {
    if (liveInterval) clearInterval(liveInterval);

    const channelId = db.getSetting('strike_board_channel_id');
    if (!channelId) return;

    console.log('[Strike] Restoring live board — updating immediately then every 60s.');
    updateStrikeBoard(client);
    liveInterval = setInterval(() => updateStrikeBoard(client), UPDATE_MS);
}

// ── Strike wipe scheduler ─────────────────────────────────────────────────────

async function runStrikeWipe(client, intervalMs) {
    try {
        const guildId = client.guilds.cache.first()?.id;
        if (guildId) db.strikes.clearAll(guildId);
        await updateStrikeBoard(client);
        console.log('[Strike] Auto-wipe completed — all strikes cleared.');
    } catch (err) {
        console.error('[Strike] Auto-wipe failed:', err.message);
    }
    scheduleNextWipe(client, intervalMs);
}

function scheduleNextWipe(client, intervalMs) {
    if (wipeTimeout) clearTimeout(wipeTimeout);
    const nextAt = Date.now() + intervalMs;
    db.setSetting('strike_wipe_next_at', String(nextAt));
    wipeTimeout = setTimeout(() => runStrikeWipe(client, intervalMs), intervalMs);
}

function startStrikeWipe(client) {
    const intervalMs = parseInt(db.getSetting('strike_wipe_interval_ms') ?? '0');
    if (!intervalMs) return;
    const nextAt = parseInt(db.getSetting('strike_wipe_next_at') ?? '0');
    const delay  = Math.max(nextAt - Date.now(), 0);
    if (wipeTimeout) clearTimeout(wipeTimeout);
    wipeTimeout = setTimeout(() => runStrikeWipe(client, intervalMs), delay);
    console.log(`[Strike] Auto-wipe resuming — next wipe in ${Math.round(delay / 1000)}s.`);
}

// ── Slash command ─────────────────────────────────────────────────────────────

const data = new SlashCommandBuilder()
    .setName('strike')
    .setDescription('Staff strike management.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addSubcommand(sc => sc
        .setName('add')
        .setDescription('Issue a strike to a staff member.')
        .addUserOption(o => o.setName('user').setDescription('Staff member to strike.').setRequired(true))
        .addStringOption(o => o.setName('reason').setDescription('Reason for the strike.').setRequired(true))
    )
    .addSubcommand(sc => sc
        .setName('remove')
        .setDescription("Remove a staff member's most recent strike.")
        .addUserOption(o => o.setName('user').setDescription('Staff member to remove a strike from.').setRequired(true))
    )
    .addSubcommand(sc => sc
        .setName('clear')
        .setDescription("Clear all strikes for a staff member.")
        .addUserOption(o => o.setName('user').setDescription('Staff member to clear.').setRequired(true))
    )
    .addSubcommand(sc => sc
        .setName('history')
        .setDescription("View all strikes for a staff member.")
        .addUserOption(o => o.setName('user').setDescription('Staff member to look up.').setRequired(true))
    )
    .addSubcommand(sc => sc
        .setName('board')
        .setDescription('Post a live strike leaderboard in a channel.')
        .addChannelOption(o => o.setName('channel').setDescription('Channel to post the leaderboard in.').addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement).setRequired(true))
    );

async function execute(interaction) {
    const sub = interaction.options.getSubcommand();
    if (sub === 'add')     return handleAdd(interaction);
    if (sub === 'remove')  return handleRemove(interaction);
    if (sub === 'clear')   return handleClear(interaction);
    if (sub === 'history') return handleHistory(interaction);
    if (sub === 'board')   return handleBoard(interaction);
}

// ── /strike add ───────────────────────────────────────────────────────────────

async function handleAdd(interaction) {
    const target = interaction.options.getMember('user') ?? interaction.options.getUser('user');
    const reason = interaction.options.getString('reason');
    const user   = target.user ?? target;

    if (user.id === interaction.user.id)
        return interaction.reply({ embeds: [error('Invalid', 'You cannot strike yourself.')], ephemeral: true });

    db.strikes.create.run({
        guild_id: interaction.guildId,
        user_id:  user.id,
        mod_id:   interaction.user.id,
        reason,
    });

    const count = db.strikes.countUser.get(interaction.guildId, user.id)?.count ?? 1;

    // Public announcement embed
    const announceEmbed = new EmbedBuilder()
        .setColor(0xE74C3C)
        .setTitle('⚡ Staff Strike Issued')
        .setDescription(`${user} has been struck for:\n\n**${reason}**`)
        .setThumbnail(user.displayAvatarURL())
        .addFields(
            { name: 'Strike #',   value: `**${count}**`,              inline: true },
            { name: 'Issued by',  value: `${interaction.user}`,       inline: true },
            { name: 'Date',       value: `<t:${Math.floor(Date.now() / 1000)}:F>`, inline: true },
        )
        .setFooter({ text: 'MostlyVanilla Beacon • Staff Discipline' })
        .setTimestamp();

    await interaction.reply({ embeds: [announceEmbed] });

    // DM the struck user
    await user.send({
        embeds: [
            new EmbedBuilder()
                .setColor(0xE74C3C)
                .setTitle(`⚡ You have received a strike in ${interaction.guild.name}`)
                .setDescription(`You have received **Strike #${count}**.`)
                .addFields(
                    { name: 'Reason',    value: reason },
                    { name: 'Issued by', value: interaction.user.tag },
                )
                .setFooter({ text: 'Please review the staff guidelines.' })
                .setTimestamp(),
        ],
    }).catch(() => {});

    // Check strikemax threshold and notify
    const maxCount = parseInt(db.getSetting('strike_max_count') ?? '0');
    if (maxCount > 0 && count >= maxCount) {
        const notifyId   = db.getSetting('strike_max_notify_id');
        const notifyType = db.getSetting('strike_max_notify_type');
        if (notifyId) {
            const mention = notifyType === 'role' ? `<@&${notifyId}>` : `<@${notifyId}>`;
            await interaction.channel.send({
                content: `${mention} ⚠️ **${user.tag}** has reached **${count}** strike${count !== 1 ? 's' : ''} — the max threshold of **${maxCount}** has been hit.`,
            }).catch(() => {});
        }
    }

    // Refresh live board immediately
    await updateStrikeBoard(interaction.client);
}

// ── /strike remove ────────────────────────────────────────────────────────────

async function handleRemove(interaction) {
    const user  = interaction.options.getUser('user');
    const count = db.strikes.countUser.get(interaction.guildId, user.id)?.count ?? 0;

    if (!count)
        return interaction.reply({ embeds: [info('No strikes', `**${user.tag}** has no strikes to remove.`)], ephemeral: true });

    db.strikes.removeLast(interaction.guildId, user.id);
    await updateStrikeBoard(interaction.client);

    await interaction.reply({
        embeds: [success('Strike Removed', `Removed the most recent strike from **${user.tag}**. They now have **${count - 1}** strike${count - 1 !== 1 ? 's' : ''}.`)],
        ephemeral: true,
    });
}

// ── /strike clear ─────────────────────────────────────────────────────────────

async function handleClear(interaction) {
    const user  = interaction.options.getUser('user');
    const count = db.strikes.countUser.get(interaction.guildId, user.id)?.count ?? 0;

    if (!count)
        return interaction.reply({ embeds: [info('No strikes', `**${user.tag}** has no strikes to clear.`)], ephemeral: true });

    db.strikes.clearUser(interaction.guildId, user.id);
    await updateStrikeBoard(interaction.client);

    await interaction.reply({
        embeds: [success('Strikes Cleared', `Cleared all **${count}** strike${count !== 1 ? 's' : ''} from **${user.tag}**.`)],
        ephemeral: true,
    });
}

// ── /strike history ───────────────────────────────────────────────────────────

async function handleHistory(interaction) {
    const user    = interaction.options.getUser('user');
    const strikes = db.strikes.getUser.all(interaction.guildId, user.id);

    if (!strikes.length)
        return interaction.reply({ embeds: [info('No strikes', `**${user.tag}** has no strikes on record.`)], ephemeral: true });

    const embed = new EmbedBuilder()
        .setColor(0xE74C3C)
        .setTitle(`⚡ Strike History — ${user.tag}`)
        .setThumbnail(user.displayAvatarURL())
        .setDescription(
            strikes.map((s, i) =>
                `**Strike ${i + 1}** · <t:${s.created_at}:d>\n└ ${s.reason} · by <@${s.mod_id}>`
            ).join('\n\n')
        )
        .setFooter({ text: `${strikes.length} total strike${strikes.length !== 1 ? 's' : ''}` });

    await interaction.reply({ embeds: [embed], ephemeral: true });
}

// ── /strike board ─────────────────────────────────────────────────────────────

async function handleBoard(interaction) {
    await interaction.deferReply({ ephemeral: true });

    const channel = interaction.options.getChannel('channel');
    const embed   = buildStrikeBoard(interaction.guild);

    const placeholder = new EmbedBuilder()
        .setColor(0xE74C3C)
        .setTitle('⚡ Staff Strike Leaderboard')
        .setDescription('*No strikes have been issued yet.*')
        .setFooter({ text: 'MostlyVanilla Beacon • Staff Discipline' })
        .setTimestamp();

    const msg = await channel.send({ embeds: [embed ?? placeholder] });

    db.setSetting('strike_board_channel_id', channel.id);
    db.setSetting('strike_board_message_id', msg.id);
    console.log(`[Strike] Live board posted — channel ${channel.id}, message ${msg.id}`);

    if (liveInterval) clearInterval(liveInterval);
    liveInterval = setInterval(() => updateStrikeBoard(interaction.client), UPDATE_MS);

    await interaction.editReply({
        embeds: [success('Strike Board Posted', `Live strike leaderboard posted in ${channel}.`)],
    });
}

// ── /strikewipe ───────────────────────────────────────────────────────────────

const strikewipeData = new SlashCommandBuilder()
    .setName('strikewipe')
    .setDescription('Configure automatic strike wipes for all staff.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addSubcommand(sc => sc
        .setName('cooldown')
        .setDescription('Set how often all strikes are automatically wiped (e.g. 1d, 12h, 2w, 1mo, 1y).')
        .addStringOption(o => o.setName('set').setDescription('Interval — s/m/h/d/w/mo/y  (m=minutes, mo=months)').setRequired(true))
    )
    .addSubcommand(sc => sc
        .setName('status')
        .setDescription('Show the current auto-wipe schedule.')
    )
    .addSubcommand(sc => sc
        .setName('clear')
        .setDescription('Cancel the automatic strike wipe schedule.')
    );

async function strikewipeExecute(interaction) {
    const sub = interaction.options.getSubcommand();
    if (sub === 'cooldown') return handleWipeCooldown(interaction);
    if (sub === 'status')   return handleWipeStatus(interaction);
    if (sub === 'clear')    return handleWipeClear(interaction);
}

async function handleWipeCooldown(interaction) {
    const raw = interaction.options.getString('set');
    const ms  = parseInterval(raw);
    if (!ms || ms < 1000) {
        return interaction.reply({
            embeds: [error('Invalid Interval', 'Use a format like `1d`, `12h`, `2w`, `1mo`, `1y`.\n`m` = minutes · `mo` = months')],
            ephemeral: true,
        });
    }

    db.setSetting('strike_wipe_interval_ms', String(ms));
    scheduleNextWipe(interaction.client, ms);
    const nextAt = Date.now() + ms;

    await interaction.reply({
        embeds: [
            new EmbedBuilder()
                .setColor(0xE74C3C)
                .setTitle('⚡ Auto-Wipe Scheduled')
                .setDescription(`All strikes will be wiped every **${formatMs(ms)}**.\nFirst wipe: <t:${Math.floor(nextAt / 1000)}:R>`)
                .setFooter({ text: 'MostlyVanilla Beacon • Staff Discipline' })
                .setTimestamp(),
        ],
        ephemeral: true,
    });
}

async function handleWipeStatus(interaction) {
    const intervalMs = parseInt(db.getSetting('strike_wipe_interval_ms') ?? '0');
    if (!intervalMs) {
        return interaction.reply({
            embeds: [info('No Auto-Wipe', 'No automatic strike wipe is configured.')],
            ephemeral: true,
        });
    }
    const nextAt = parseInt(db.getSetting('strike_wipe_next_at') ?? '0');
    await interaction.reply({
        embeds: [
            new EmbedBuilder()
                .setColor(0xE74C3C)
                .setTitle('⚡ Auto-Wipe Status')
                .addFields(
                    { name: 'Interval',  value: formatMs(intervalMs), inline: true },
                    { name: 'Next Wipe', value: nextAt ? `<t:${Math.floor(nextAt / 1000)}:R>` : '*Unknown*', inline: true },
                )
                .setFooter({ text: 'MostlyVanilla Beacon • Staff Discipline' })
                .setTimestamp(),
        ],
        ephemeral: true,
    });
}

async function handleWipeClear(interaction) {
    if (wipeTimeout) clearTimeout(wipeTimeout);
    wipeTimeout = null;
    db.deleteSetting('strike_wipe_interval_ms');
    db.deleteSetting('strike_wipe_next_at');
    await interaction.reply({
        embeds: [success('Auto-Wipe Cancelled', 'Automatic strike wipes have been disabled.')],
        ephemeral: true,
    });
}

// ── /strikemax ────────────────────────────────────────────────────────────────

const strikemaxData = new SlashCommandBuilder()
    .setName('strikemax')
    .setDescription('Set max strike threshold and who to notify when it is hit.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addIntegerOption(o => o.setName('set').setDescription('Max strikes before a notification is sent.').setMinValue(1))
    .addMentionableOption(o => o.setName('notification').setDescription('User or role to notify when the max is reached.'));

async function strikemaxExecute(interaction) {
    const maxCount    = interaction.options.getInteger('set');
    const mentionable = interaction.options.getMentionable('notification');

    if (maxCount === null && !mentionable) {
        const current    = parseInt(db.getSetting('strike_max_count') ?? '0');
        const notifyId   = db.getSetting('strike_max_notify_id');
        const notifyType = db.getSetting('strike_max_notify_type');
        const mention    = notifyId ? (notifyType === 'role' ? `<@&${notifyId}>` : `<@${notifyId}>`) : '*Not set*';

        return interaction.reply({
            embeds: [
                new EmbedBuilder()
                    .setColor(0xE74C3C)
                    .setTitle('⚡ Strike Max Settings')
                    .addFields(
                        { name: 'Max Strikes',   value: current ? `**${current}**` : '*Not set*', inline: true },
                        { name: 'Notify Target', value: mention,                                  inline: true },
                    )
                    .setFooter({ text: 'MostlyVanilla Beacon • Staff Discipline' })
                    .setTimestamp(),
            ],
            ephemeral: true,
        });
    }

    if (maxCount !== null) db.setSetting('strike_max_count', String(maxCount));

    if (mentionable) {
        const isRole = mentionable instanceof Role;
        db.setSetting('strike_max_notify_id',   mentionable.id);
        db.setSetting('strike_max_notify_type', isRole ? 'role' : 'user');
    }

    const finalMax   = parseInt(db.getSetting('strike_max_count') ?? '0');
    const notifyId   = db.getSetting('strike_max_notify_id');
    const notifyType = db.getSetting('strike_max_notify_type');
    const mention    = notifyId ? (notifyType === 'role' ? `<@&${notifyId}>` : `<@${notifyId}>`) : '*Not set*';

    await interaction.reply({
        embeds: [
            new EmbedBuilder()
                .setColor(0xE74C3C)
                .setTitle('⚡ Strike Max Updated')
                .addFields(
                    { name: 'Max Strikes',   value: finalMax ? `**${finalMax}**` : '*Not set*', inline: true },
                    { name: 'Notify Target', value: mention,                                    inline: true },
                )
                .setFooter({ text: 'MostlyVanilla Beacon • Staff Discipline' })
                .setTimestamp(),
        ],
        ephemeral: true,
    });
}

module.exports = { data, execute, startStrikeBoard, startStrikeWipe, strikewipeData, strikewipeExecute, strikemaxData, strikemaxExecute };
