const {
    SlashCommandBuilder, EmbedBuilder,
    ActionRowBuilder, ButtonBuilder, ButtonStyle,
    ModalBuilder, TextInputBuilder, TextInputStyle,
    PermissionFlagsBits, ChannelType,
} = require('discord.js');
const db = require('../database');
const { success, error, info } = require('../ticketUtils');

// ── Interval parser ───────────────────────────────────────────────────────────

function parseInterval(str) {
    const match = str.trim().match(/^(\d+(?:\.\d+)?)(mo|y|w|d|h|m|s)$/i);
    if (!match) return null;
    const n    = parseFloat(match[1]);
    const unit = match[2].toLowerCase();
    const map  = { s: 1000, m: 60000, h: 3600000, d: 86400000, w: 604800000, mo: 2592000000, y: 31536000000 };
    return Math.round(n * map[unit]);
}

// ── Embed builder ─────────────────────────────────────────────────────────────

function mcHeadUrl(mcName) {
    return `https://minotar.net/avatar/${encodeURIComponent(mcName)}/128`;
}

function buildRankEmbed(grant, discordUser) {
    const now      = Math.floor(Date.now() / 1000);
    const expired  = grant.expired || now >= grant.expires_at;
    const color    = expired ? 0x64748b : (grant.rank === 'Vanilla+' ? 0xd97706 : 0x16a34a);
    const title    = expired ? '❌ Rank Expired' : `🎖️ Active Rank — ${grant.rank}`;
    const expiryValue = expired
        ? `<t:${grant.expires_at}:F>`
        : `<t:${grant.expires_at}:F>  ·  <t:${grant.expires_at}:R>`;

    return new EmbedBuilder()
        .setColor(color)
        .setAuthor({
            name:    discordUser ? discordUser.username : `User ${grant.user_id}`,
            iconURL: discordUser?.displayAvatarURL?.() ?? undefined,
        })
        .setThumbnail(mcHeadUrl(grant.mc_name))
        .setTitle(title)
        .addFields(
            { name: 'Holder',      value: `<@${grant.user_id}>`,        inline: true },
            { name: 'Minecraft',   value: `\`${grant.mc_name}\``,       inline: true },
            { name: 'Rank',        value: `**${grant.rank}**`,          inline: true },
            { name: 'Granted by',  value: `<@${grant.granted_by}>`,     inline: true },
            { name: 'Granted',     value: `<t:${grant.granted_at}:F>`,  inline: true },
            { name: expired ? 'Expired' : 'Expires', value: expiryValue, inline: true },
        )
        .setFooter({ text: 'MostlyVanilla Beacon • Rank Tracker' })
        .setTimestamp();
}

function buildRankButtons(grantId, expired) {
    return new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setCustomId(`rank_refresh:${grantId}`)
            .setLabel('Refresh')
            .setEmoji('🔄')
            .setStyle(ButtonStyle.Secondary),
        new ButtonBuilder()
            .setCustomId(`rank_extend:${grantId}`)
            .setLabel('Extend')
            .setEmoji('📅')
            .setStyle(ButtonStyle.Primary)
            .setDisabled(!!expired),
        new ButtonBuilder()
            .setCustomId(`rank_revoke:${grantId}`)
            .setLabel('Revoke')
            .setEmoji('❌')
            .setStyle(ButtonStyle.Danger)
            .setDisabled(!!expired),
    );
}

// ── Live embed updater ────────────────────────────────────────────────────────

async function refreshEmbed(client, grant) {
    if (!grant.channel_id || !grant.message_id) return;
    try {
        const channel = await client.channels.fetch(grant.channel_id).catch(() => null);
        if (!channel) return;
        const message = await channel.messages.fetch(grant.message_id).catch(() => null);
        if (!message) return;
        const discordUser = await client.users.fetch(grant.user_id).catch(() => null);
        const expired = grant.expired || Math.floor(Date.now() / 1000) >= grant.expires_at;
        await message.edit({
            embeds:     [buildRankEmbed({ ...grant, expired: expired ? 1 : 0 }, discordUser)],
            components: [buildRankButtons(grant.id, expired)],
        });
    } catch (err) {
        console.error('[Rank] Embed refresh failed:', err.message);
    }
}

// ── Reminder checker (run every 60s) ─────────────────────────────────────────

let reminderInterval = null;

async function checkRankReminders(client) {
    const now    = Math.floor(Date.now() / 1000);
    const grants = db.rankGrants.getActiveAll();

    for (const grant of grants) {
        const timeLeft = grant.expires_at - now;

        if (!grant.reminded_1d && timeLeft <= 86400 && timeLeft > 0) {
            await sendReminder(client, grant, '**1 day**');
            db.rankGrants.setReminder(grant.id, '1d');
        }

        if (!grant.reminded_1h && timeLeft <= 3600 && timeLeft > 0) {
            await sendReminder(client, grant, '**1 hour**');
            db.rankGrants.setReminder(grant.id, '1h');
        }

        if (!grant.reminded_1m && timeLeft <= 60 && timeLeft > 0) {
            await sendReminder(client, grant, '**1 minute**');
            db.rankGrants.setReminder(grant.id, '1m');
        }

        if (timeLeft <= 0) {
            db.rankGrants.setExpired(grant.id);
            await sendExpiredNotice(client, grant);
            await refreshEmbed(client, { ...grant, expired: 1 });
        }
    }
}

async function sendReminder(client, grant, timeLabel) {
    try {
        const channel = await client.channels.fetch(grant.channel_id).catch(() => null);
        if (!channel) return;
        await channel.send({
            content: `<@${grant.granted_by}> ⏰ **Heads up:** <@${grant.user_id}>'s **${grant.rank}** rank expires in ${timeLabel}!`,
            allowedMentions: { users: [grant.granted_by] },
        });
    } catch (err) {
        console.error('[Rank] Reminder send failed:', err.message);
    }
}

async function sendExpiredNotice(client, grant) {
    try {
        const channel = await client.channels.fetch(grant.channel_id).catch(() => null);
        if (!channel) return;
        await channel.send({
            content: `<@${grant.granted_by}> 🔔 <@${grant.user_id}>'s **${grant.rank}** rank has **expired**. Please remove their in-game rank.`,
            allowedMentions: { users: [grant.granted_by] },
        });
    } catch (err) {
        console.error('[Rank] Expiry notice send failed:', err.message);
    }
}

function startRankChecker(client) {
    if (reminderInterval) clearInterval(reminderInterval);
    reminderInterval = setInterval(() => checkRankReminders(client), 60_000);
    checkRankReminders(client);
    console.log('[Rank] Reminder checker started.');
}

// ── Slash command definition ──────────────────────────────────────────────────

const data = new SlashCommandBuilder()
    .setName('rank')
    .setDescription('Manage donor rank grants.')
    .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
    .addSubcommand(sc => sc
        .setName('give')
        .setDescription('Grant a donor rank to a player.')
        .addUserOption(o => o.setName('user').setDescription('Discord user to grant the rank to.').setRequired(true))
        .addStringOption(o => o
            .setName('rank')
            .setDescription('Rank to grant.')
            .setRequired(true)
            .addChoices(
                { name: 'Vanilla ($12)',  value: 'Vanilla'  },
                { name: 'Vanilla+ ($15)', value: 'Vanilla+' },
            )
        )
        .addStringOption(o => o.setName('duration').setDescription('How long the rank lasts (e.g. 30d, 1mo, 2w).').setRequired(true))
        .addStringOption(o => o.setName('minecraft').setDescription("Player's Minecraft username.").setRequired(true))
        .addChannelOption(o => o
            .setName('channel')
            .setDescription('Channel to post the rank tracking embed in.')
            .addChannelTypes(ChannelType.GuildText, ChannelType.GuildAnnouncement)
            .setRequired(true)
        )
    )
    .addSubcommand(sc => sc
        .setName('revoke')
        .setDescription('Revoke an active rank early.')
        .addUserOption(o => o.setName('user').setDescription('User whose rank to revoke.').setRequired(true))
    )
    .addSubcommand(sc => sc
        .setName('list')
        .setDescription('List active rank grants in this server.')
    );

// ── Command execute ───────────────────────────────────────────────────────────

async function execute(interaction) {
    const sub = interaction.options.getSubcommand();
    if (sub === 'give')   return handleGive(interaction);
    if (sub === 'revoke') return handleRevoke(interaction);
    if (sub === 'list')   return handleList(interaction);
}

// ── /rank give ────────────────────────────────────────────────────────────────

async function handleGive(interaction) {
    await interaction.deferReply({ ephemeral: true });

    const targetUser  = interaction.options.getUser('user');
    const rankName    = interaction.options.getString('rank');
    const durationRaw = interaction.options.getString('duration');
    const mcName      = interaction.options.getString('minecraft');
    const channel     = interaction.options.getChannel('channel');

    const durationMs = parseInterval(durationRaw);
    if (!durationMs || durationMs < 60_000) {
        return interaction.editReply({ embeds: [error('Invalid Duration', 'Use a format like `30d`, `1mo`, `2w`, `1h`.')] });
    }

    const now       = Math.floor(Date.now() / 1000);
    const expiresAt = now + Math.floor(durationMs / 1000);

    const grantId = db.rankGrants.create({
        guild_id:   interaction.guildId,
        user_id:    targetUser.id,
        mc_name:    mcName,
        rank:       rankName,
        granted_by: interaction.user.id,
        expires_at: expiresAt,
        channel_id: channel.id,
    });

    const grant = db.rankGrants.getById(grantId);
    const embed = buildRankEmbed(grant, targetUser);
    const row   = buildRankButtons(grantId, false);

    const msg = await channel.send({ embeds: [embed], components: [row] });
    db.rankGrants.setMessageId(grantId, msg.id);

    await interaction.editReply({
        embeds: [success(
            'Rank Granted',
            `**${rankName}** granted to ${targetUser} (\`${mcName}\`) until <t:${expiresAt}:F>.\nTracking embed posted in ${channel}.`,
        )],
    });
}

// ── /rank revoke ──────────────────────────────────────────────────────────────

async function handleRevoke(interaction) {
    await interaction.deferReply({ ephemeral: true });

    const targetUser = interaction.options.getUser('user');
    const grant      = db.rankGrants.getActiveByUser(interaction.guildId, targetUser.id);

    if (!grant) {
        return interaction.editReply({
            embeds: [info('No Active Rank', `${targetUser.tag} has no active rank grant on record.`)],
        });
    }

    db.rankGrants.setExpired(grant.id);
    await refreshEmbed(interaction.client, { ...grant, expired: 1 });

    await interaction.editReply({
        embeds: [success('Rank Revoked', `**${grant.rank}** has been revoked from ${targetUser} (\`${grant.mc_name}\`).`)],
    });
}

// ── /rank list ────────────────────────────────────────────────────────────────

async function handleList(interaction) {
    const grants = db.rankGrants.getActive(interaction.guildId);

    if (!grants.length) {
        return interaction.reply({ embeds: [info('No Active Ranks', 'No active rank grants right now.')], ephemeral: true });
    }

    const lines = grants.map(g =>
        `• <@${g.user_id}> — **${g.rank}** · \`${g.mc_name}\` · expires <t:${g.expires_at}:R>`
    );

    await interaction.reply({
        embeds: [
            new EmbedBuilder()
                .setColor(0x16a34a)
                .setTitle('🎖️ Active Rank Grants')
                .setDescription(lines.join('\n'))
                .setFooter({ text: 'MostlyVanilla Beacon • Rank Tracker' })
                .setTimestamp(),
        ],
        ephemeral: true,
    });
}

// ── Button handler ────────────────────────────────────────────────────────────

async function handleButton(interaction) {
    const colonIdx = interaction.customId.indexOf(':');
    const action   = interaction.customId.slice(0, colonIdx);
    const grantId  = parseInt(interaction.customId.slice(colonIdx + 1));
    const grant    = db.rankGrants.getById(grantId);

    if (!grant) {
        return interaction.reply({ embeds: [error('Not Found', 'This rank grant no longer exists.')], ephemeral: true });
    }

    if (action === 'rank_refresh') return handleRefresh(interaction, grant);
    if (action === 'rank_extend')  return handleExtend(interaction, grant);
    if (action === 'rank_revoke')  return handleRevokeButton(interaction, grant);
}

async function handleRefresh(interaction, grant) {
    const discordUser = await interaction.client.users.fetch(grant.user_id).catch(() => null);
    const expired     = grant.expired || Math.floor(Date.now() / 1000) >= grant.expires_at;
    await interaction.update({
        embeds:     [buildRankEmbed({ ...grant, expired: expired ? 1 : 0 }, discordUser)],
        components: [buildRankButtons(grant.id, expired)],
    });
}

async function handleExtend(interaction, grant) {
    if (!interaction.member.permissions.has(PermissionFlagsBits.Administrator)) {
        return interaction.reply({ embeds: [error('No Permission', 'Only admins can extend ranks.')], ephemeral: true });
    }

    const modal = new ModalBuilder()
        .setCustomId(`rank_extend_modal:${grant.id}`)
        .setTitle('Extend Rank Duration')
        .addComponents(
            new ActionRowBuilder().addComponents(
                new TextInputBuilder()
                    .setCustomId('duration')
                    .setLabel('Extra time to add (e.g. 7d, 1mo, 2w)')
                    .setStyle(TextInputStyle.Short)
                    .setRequired(true)
                    .setPlaceholder('30d'),
            ),
        );

    await interaction.showModal(modal);
}

async function handleRevokeButton(interaction, grant) {
    if (!interaction.member.permissions.has(PermissionFlagsBits.Administrator)) {
        return interaction.reply({ embeds: [error('No Permission', 'Only admins can revoke ranks.')], ephemeral: true });
    }

    db.rankGrants.setExpired(grant.id);
    const discordUser = await interaction.client.users.fetch(grant.user_id).catch(() => null);

    await interaction.update({
        embeds:     [buildRankEmbed({ ...grant, expired: 1 }, discordUser)],
        components: [buildRankButtons(grant.id, true)],
    });

    await interaction.channel.send({
        content: `<@${grant.granted_by}> 🔔 <@${grant.user_id}>'s **${grant.rank}** rank was manually revoked by <@${interaction.user.id}>.`,
        allowedMentions: { users: [grant.granted_by] },
    }).catch(() => {});
}

// ── Modal handler ─────────────────────────────────────────────────────────────

async function handleModal(interaction) {
    const colonIdx = interaction.customId.indexOf(':');
    const grantId  = parseInt(interaction.customId.slice(colonIdx + 1));
    const grant    = db.rankGrants.getById(grantId);

    if (!grant) {
        return interaction.reply({ embeds: [error('Not Found', 'This rank grant no longer exists.')], ephemeral: true });
    }

    const durationRaw = interaction.fields.getTextInputValue('duration');
    const durationMs  = parseInterval(durationRaw);

    if (!durationMs || durationMs < 60_000) {
        return interaction.reply({ embeds: [error('Invalid Duration', 'Use a format like `7d`, `1mo`, `2w`.')], ephemeral: true });
    }

    const newExpiry = grant.expires_at + Math.floor(durationMs / 1000);
    db.rankGrants.extend(grantId, newExpiry);

    const updated     = db.rankGrants.getById(grantId);
    const discordUser = await interaction.client.users.fetch(grant.user_id).catch(() => null);

    await refreshEmbed(interaction.client, updated);

    await interaction.reply({
        embeds: [success(
            'Rank Extended',
            `**${grant.rank}** for <@${grant.user_id}> extended to <t:${newExpiry}:F>.`,
        )],
        ephemeral: true,
    });
}

module.exports = { data, execute, handleButton, handleModal, startRankChecker };
