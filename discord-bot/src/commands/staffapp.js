const {
  SlashCommandBuilder, PermissionFlagsBits, ChannelType,
  ActionRowBuilder, ButtonBuilder, ButtonStyle,
  EmbedBuilder, ModalBuilder, TextInputBuilder, TextInputStyle,
  ChannelSelectMenuBuilder, RoleSelectMenuBuilder,
} = require('discord.js');
const { staffappConfig, staffappApplications, ticketConfig, getSupportRoleIds, tickets } = require('../database');
const { success, error, warning, info } = require('../ticketUtils');

const SESSION_TIMEOUT_MS = 24 * 60 * 60 * 1000;

const sessions  = new Map();
const builders  = new Map();

// ─── Command definition ───────────────────────────────────────────────────────

const data = new SlashCommandBuilder()
  .setName('staffapp')
  .setDescription('Staff application panel management.')
  .setDefaultMemberPermissions(PermissionFlagsBits.Administrator)
  .addSubcommand(sc => sc.setName('panel')
    .setDescription('Build and post a staff application panel.')
    .addChannelOption(o => o.setName('channel').setDescription('Channel to post the panel in.').addChannelTypes(ChannelType.GuildText).setRequired(true)));

async function execute(interaction) {
  if (interaction.options.getSubcommand() === 'panel') return handlePanelCommand(interaction);
}

async function handlePanelCommand(interaction) {
  const channelId = interaction.options.getChannel('channel').id;
  const existing  = staffappConfig.get.get(interaction.guildId);

  const modal = new ModalBuilder().setCustomId(`staffapp_panel_modal:${channelId}`).setTitle('Staff Application Setup');
  modal.addComponents(
    new ActionRowBuilder().addComponents(
      new TextInputBuilder().setCustomId('title').setLabel('Panel Title').setStyle(TextInputStyle.Short)
        .setValue(existing?.title ?? 'Staff Applications').setMaxLength(100).setRequired(true)
    ),
    new ActionRowBuilder().addComponents(
      new TextInputBuilder().setCustomId('description').setLabel('Panel Description').setStyle(TextInputStyle.Paragraph)
        .setPlaceholder('e.g. Click a button below to apply for a staff position.')
        .setValue(existing?.description ?? '').setMaxLength(2000).setRequired(true)
    ),
    new ActionRowBuilder().addComponents(
      new TextInputBuilder().setCustomId('questions').setLabel('Questions (one per line, up to 10)').setStyle(TextInputStyle.Paragraph)
        .setPlaceholder('How old are you?\nWhy do you want to be staff?\nHow many hours per week can you dedicate?')
        .setValue(existing?.questions ? JSON.parse(existing.questions).join('\n') : '').setMaxLength(2000).setRequired(true)
    ),
  );
  await interaction.showModal(modal);
}

// ─── Panel builder UI ─────────────────────────────────────────────────────────

function builderPayload(config) {
  const rolesValue = config.roleIds.length ? config.roleIds.map(id => `<@&${id}>`).join(', ') : '❌ Not set';
  const qLines     = config.questions.length ? config.questions.map((q, i) => `**${i + 1}.** ${q}`).join('\n') : '*(none)*';

  const previewEmbed = new EmbedBuilder()
    .setColor(0x5865F2).setTitle(config.title).setDescription(config.description)
    .setFooter({ text: 'Panel Preview' });

  const configEmbed = new EmbedBuilder()
    .setColor(0x5865F2).setTitle('⚙️ Application Panel Configuration')
    .addFields(
      { name: '📋 Log Channel',                          value: config.logChannelId ? `<#${config.logChannelId}>` : '❌ Not set', inline: true },
      { name: `🛡️ Apply Roles (${config.roleIds.length}/5)`, value: rolesValue, inline: true },
      { name: `❓ Questions (${config.questions.length})`,   value: qLines },
    )
    .setFooter({ text: 'Set a log channel and at least one role to enable posting.' });

  const logRow = new ActionRowBuilder().addComponents(
    new ChannelSelectMenuBuilder().setCustomId('staffapp_builder_logchannel')
      .setPlaceholder(config.logChannelId ? '✅ Log channel set — click to change' : '📋 Select log channel...')
      .setChannelTypes(ChannelType.GuildText)
  );
  const roleRow = new ActionRowBuilder().addComponents(
    new RoleSelectMenuBuilder().setCustomId('staffapp_builder_roles')
      .setPlaceholder(config.roleIds.length ? '✅ Roles set — select to replace' : '🛡️ Select roles users can apply for...')
      .setMinValues(1).setMaxValues(5)
  );

  const ready = !!(config.logChannelId && config.roleIds.length);
  const actionRow = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId('staffapp_builder_post').setLabel('Post Panel').setEmoji('✅').setStyle(ButtonStyle.Success).setDisabled(!ready),
    new ButtonBuilder().setCustomId('staffapp_builder_cancel').setLabel('Cancel').setEmoji('🗑️').setStyle(ButtonStyle.Danger),
  );

  return { embeds: [previewEmbed, configEmbed], components: [logRow, roleRow, actionRow] };
}

// ─── Interaction routers ───────────────────────────────────────────────────────

async function handleButton(interaction) {
  const [action, ...args] = interaction.customId.split(':');
  if (action === 'staffapp_apply')          return handleApply(interaction, args[0]);
  if (action === 'staffapp_builder_post')   return handleBuilderPost(interaction);
  if (action === 'staffapp_builder_cancel') return handleBuilderCancel(interaction);
  if (action === 'staffapp_accept')         return handleAccept(interaction, parseInt(args[0], 10));
  if (action === 'staffapp_deny')           return handleDeny(interaction, parseInt(args[0], 10));
  if (action === 'staffapp_accept_reason')  return showReasonModal(interaction, parseInt(args[0], 10), 'accept');
  if (action === 'staffapp_deny_reason')    return showReasonModal(interaction, parseInt(args[0], 10), 'deny');
  if (action === 'staffapp_ticket')         return handleOpenTicket(interaction, parseInt(args[0], 10));
}

async function handleSelect(interaction) {
  if (interaction.customId === 'staffapp_builder_logchannel') return handleBuilderLogChannel(interaction);
  if (interaction.customId === 'staffapp_builder_roles')      return handleBuilderRoles(interaction);
}

async function handleModal(interaction) {
  if (interaction.customId.startsWith('staffapp_panel_modal:'))  return handlePanelModal(interaction);
  if (interaction.customId.startsWith('staffapp_reason_modal:')) return handleReasonModal(interaction);
}

// ─── Panel builder handlers ────────────────────────────────────────────────────

async function handlePanelModal(interaction) {
  const channelId    = interaction.customId.split(':')[1];
  const title        = interaction.fields.getTextInputValue('title').trim();
  const description  = interaction.fields.getTextInputValue('description').trim();
  const questionsRaw = interaction.fields.getTextInputValue('questions').trim();
  const questions    = questionsRaw.split('\n').map(l => l.trim()).filter(Boolean).slice(0, 10);

  const existing = builders.get(interaction.user.id) ?? {};
  const config = { channelId, title, description, questions, logChannelId: existing.logChannelId ?? null, roleIds: existing.roleIds ?? [] };
  builders.set(interaction.user.id, config);
  await interaction.reply({ ...builderPayload(config), ephemeral: true });
}

async function handleBuilderLogChannel(interaction) {
  const config = builders.get(interaction.user.id);
  if (!config) return interaction.reply({ embeds: [error('Session expired', 'Run `/staffapp panel` again.')], ephemeral: true });
  config.logChannelId = interaction.values[0];
  await interaction.update(builderPayload(config));
}

async function handleBuilderRoles(interaction) {
  const config = builders.get(interaction.user.id);
  if (!config) return interaction.reply({ embeds: [error('Session expired', 'Run `/staffapp panel` again.')], ephemeral: true });
  config.roleIds = interaction.values;
  await interaction.update(builderPayload(config));
}

async function handleBuilderPost(interaction) {
  const config = builders.get(interaction.user.id);
  if (!config) return interaction.reply({ embeds: [error('Session expired', 'Run `/staffapp panel` again.')], ephemeral: true });
  if (!config.logChannelId || !config.roleIds.length)
    return interaction.reply({ embeds: [error('Incomplete', 'Set a log channel and at least one role first.')], ephemeral: true });

  await interaction.deferUpdate();
  const channel = await interaction.client.channels.fetch(config.channelId).catch(() => null);
  if (!channel) return interaction.editReply({ embeds: [error('Channel not found', 'Target channel could not be found.')], components: [] });

  const buttonComponents = config.roleIds.map(roleId => {
    const role = interaction.guild.roles.cache.get(roleId);
    return new ButtonBuilder()
      .setCustomId(`staffapp_apply:${roleId}`)
      .setLabel(`Apply for ${role?.name ?? roleId}`)
      .setEmoji('📋')
      .setStyle(ButtonStyle.Primary);
  });

  const panelEmbed = new EmbedBuilder()
    .setColor(0x5865F2).setTitle(config.title).setDescription(config.description)
    .setFooter({ text: 'MostlyVanilla Beacon • Staff Applications' });

  const panelMsg = await channel.send({
    embeds: [panelEmbed],
    components: [new ActionRowBuilder().addComponents(buttonComponents)],
  });

  staffappConfig.upsert.run({
    guild_id:         interaction.guildId,
    log_channel_id:   config.logChannelId,
    questions:        JSON.stringify(config.questions),
    role_ids:         JSON.stringify(config.roleIds),
    title:            config.title,
    description:      config.description,
    panel_channel_id: config.channelId,
    panel_message_id: panelMsg.id,
  });

  builders.delete(interaction.user.id);
  await interaction.editReply({ embeds: [success('Panel Posted!', `Staff application panel posted in <#${config.channelId}>.`)], components: [] });
}

async function handleBuilderCancel(interaction) {
  builders.delete(interaction.user.id);
  await interaction.update({ embeds: [info('Cancelled', 'Panel builder closed.')], components: [] });
}

// ─── Apply button ──────────────────────────────────────────────────────────────

async function handleApply(interaction, roleId) {
  const cfg = staffappConfig.get.get(interaction.guildId);
  if (!cfg) return interaction.reply({ embeds: [error('Not configured', 'The application system has not been set up.')], ephemeral: true });

  if (sessions.has(interaction.user.id)) {
    const s = sessions.get(interaction.user.id);
    if (Date.now() - s.startedAt < SESSION_TIMEOUT_MS)
      return interaction.reply({ embeds: [warning('Application in progress', 'You already have an active application. Check your DMs!')], ephemeral: true });
    sessions.delete(interaction.user.id);
  }

  const pending = staffappApplications.getPending.get(interaction.guildId, interaction.user.id);
  if (pending)
    return interaction.reply({ embeds: [warning('Already applied', 'You already have a pending application under review. Please wait for a decision.')], ephemeral: true });

  const questions = JSON.parse(cfg.questions ?? '[]');
  if (!questions.length)
    return interaction.reply({ embeds: [error('Not configured', 'No application questions have been set up.')], ephemeral: true });

  const role     = interaction.guild.roles.cache.get(roleId);
  const roleName = role?.name ?? 'Unknown Role';

  try {
    const dm = await interaction.user.createDM();
    await dm.send({
      embeds: [
        new EmbedBuilder().setColor(0x5865F2)
          .setTitle(`📋 Staff Application — ${roleName}`)
          .setDescription(
            `Welcome! You're applying for **${roleName}** in **${interaction.guild.name}**.\n\n` +
            `I'll ask you **${questions.length}** question${questions.length !== 1 ? 's' : ''}. ` +
            `Just reply to each one in this DM.\n\n` +
            `**Question 1/${questions.length}:**\n${questions[0]}`
          )
          .setFooter({ text: 'Type your answer and send it as a message.' })
          .setTimestamp(),
      ],
    });
  } catch {
    return interaction.reply({ embeds: [error('DMs disabled', "I couldn't DM you. Please enable DMs from server members and try again.")], ephemeral: true });
  }

  sessions.set(interaction.user.id, {
    guildId:       interaction.guildId,
    guildName:     interaction.guild.name,
    roleId,
    roleName,
    questions,
    questionIndex: 0,
    answers:       [],
    startedAt:     Date.now(),
  });

  await interaction.reply({ embeds: [success('Application started!', 'Check your DMs to answer the questions.')], ephemeral: true });
}

// ─── DM conversation handler ───────────────────────────────────────────────────

async function handleDm(message) {
  const session = sessions.get(message.author.id);
  if (!session) return false;

  if (Date.now() - session.startedAt > SESSION_TIMEOUT_MS) {
    sessions.delete(message.author.id);
    await message.channel.send({ embeds: [error('Session expired', 'Your application has expired. Click Apply on the panel to start over.')] });
    return true;
  }

  session.answers.push({ question: session.questions[session.questionIndex], answer: message.content.trim() });
  session.questionIndex++;

  if (session.questionIndex < session.questions.length) {
    await message.channel.send({
      embeds: [
        new EmbedBuilder().setColor(0x5865F2)
          .setDescription(`**Question ${session.questionIndex + 1}/${session.questions.length}:**\n${session.questions[session.questionIndex]}`),
      ],
    });
  } else {
    sessions.delete(message.author.id);
    await submitApplication(message, session);
  }

  return true;
}

async function submitApplication(message, session) {
  await message.channel.send({
    embeds: [
      new EmbedBuilder().setColor(0x57F287)
        .setTitle('✅ Application Submitted!')
        .setDescription(`Your application for **${session.roleName}** in **${session.guildName}** has been submitted. You'll hear back from staff soon!`)
        .setTimestamp(),
    ],
  });

  const result = staffappApplications.create.run({
    guild_id:        session.guildId,
    user_id:         message.author.id,
    applied_role_id: session.roleId,
    answers:         JSON.stringify(session.answers),
  });
  const appId = result.lastInsertRowid;

  const cfg   = staffappConfig.get.get(session.guildId);
  const logCh = cfg?.log_channel_id
    ? await message.client.channels.fetch(cfg.log_channel_id).catch(() => null)
    : null;
  if (!logCh) return;

  const user     = message.author;
  const logEmbed = new EmbedBuilder()
    .setColor(0x5865F2).setTitle('📋 New Staff Application')
    .setThumbnail(user.displayAvatarURL({ dynamic: true }))
    .addFields(
      { name: 'Applicant', value: `${user} (${user.id})`, inline: true },
      { name: 'Role',      value: `<@&${session.roleId}>`, inline: true },
    )
    .setTimestamp();

  for (const { question, answer } of session.answers) {
    logEmbed.addFields({ name: question, value: answer || '*(no answer)*', inline: false });
  }

  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(`staffapp_accept:${appId}`).setLabel('Accept').setEmoji('✅').setStyle(ButtonStyle.Success),
    new ButtonBuilder().setCustomId(`staffapp_accept_reason:${appId}`).setLabel('Accept w/ Reason').setEmoji('📝').setStyle(ButtonStyle.Primary),
    new ButtonBuilder().setCustomId(`staffapp_deny:${appId}`).setLabel('Deny').setEmoji('❌').setStyle(ButtonStyle.Danger),
    new ButtonBuilder().setCustomId(`staffapp_deny_reason:${appId}`).setLabel('Deny w/ Reason').setEmoji('📝').setStyle(ButtonStyle.Secondary),
    new ButtonBuilder().setCustomId(`staffapp_ticket:${appId}`).setLabel('Open Ticket').setEmoji('🎫').setStyle(ButtonStyle.Secondary),
  );

  const logMsg = await logCh.send({ embeds: [logEmbed], components: [row] }).catch(err => { console.error('[StaffApp] Log send failed:', err); return null; });
  if (logMsg) staffappApplications.setLogMessage.run({ id: appId, log_message_id: logMsg.id });
}

// ─── Accept / Deny ────────────────────────────────────────────────────────────

async function handleAccept(interaction, appId, reason = null) {
  const app = staffappApplications.getById.get(appId);
  if (!app) return interaction.reply({ embeds: [error('Not found', 'Application not found.')], ephemeral: true });
  if (app.status !== 'pending') return interaction.reply({ embeds: [warning('Already processed', 'This application has already been processed.')], ephemeral: true });

  staffappApplications.updateStatus.run({ id: appId, status: 'accepted', reviewed_by: interaction.user.id, reason });

  const member = await interaction.guild.members.fetch(app.user_id).catch(() => null);
  if (member) {
    await member.roles.add(app.applied_role_id).catch(err => console.error('[StaffApp] Failed to add applied role:', err.message));
  }

  const user = await interaction.client.users.fetch(app.user_id).catch(() => null);
  if (user) {
    const dmEmbed = new EmbedBuilder().setColor(0x57F287)
      .setTitle('🎉 Application Accepted!')
      .setDescription(
        `Congratulations! Your staff application for <@&${app.applied_role_id}> ` +
        `in **${interaction.guild.name}** has been **accepted**!\n\nWelcome to the team! 🎉`
      )
      .setTimestamp();
    if (reason) dmEmbed.addFields({ name: 'Note from staff', value: reason });
    await user.send({ embeds: [dmEmbed] }).catch(() => {});
  }

  const footerText = reason
    ? `✅ Accepted by ${interaction.user.tag} — ${reason}`
    : `✅ Accepted by ${interaction.user.tag}`;

  const updated = EmbedBuilder.from(interaction.message.embeds[0])
    .setColor(0x57F287)
    .setFooter({ text: footerText.substring(0, 2048) });
  await interaction.update({ embeds: [updated], components: [] });
}

async function handleDeny(interaction, appId, reason = null) {
  const app = staffappApplications.getById.get(appId);
  if (!app) return interaction.reply({ embeds: [error('Not found', 'Application not found.')], ephemeral: true });
  if (app.status !== 'pending') return interaction.reply({ embeds: [warning('Already processed', 'This application has already been processed.')], ephemeral: true });

  staffappApplications.updateStatus.run({ id: appId, status: 'denied', reviewed_by: interaction.user.id, reason });

  const user = await interaction.client.users.fetch(app.user_id).catch(() => null);
  if (user) {
    const dmEmbed = new EmbedBuilder().setColor(0xED4245)
      .setTitle('Application Update')
      .setDescription(
        `Thank you for applying for a staff position in **${interaction.guild.name}**.\n\n` +
        `Unfortunately, your application was **not accepted** at this time. ` +
        `We appreciate your interest and encourage you to apply again in the future!`
      )
      .setTimestamp();
    if (reason) dmEmbed.addFields({ name: 'Reason', value: reason });
    await user.send({ embeds: [dmEmbed] }).catch(() => {});
  }

  const footerText = reason
    ? `❌ Denied by ${interaction.user.tag} — ${reason}`
    : `❌ Denied by ${interaction.user.tag}`;

  const updated = EmbedBuilder.from(interaction.message.embeds[0])
    .setColor(0xED4245)
    .setFooter({ text: footerText.substring(0, 2048) });
  await interaction.update({ embeds: [updated], components: [] });
}

// ─── Accept/Deny with Reason ──────────────────────────────────────────────────

async function showReasonModal(interaction, appId, verdict) {
  const app = staffappApplications.getById.get(appId);
  if (!app) return interaction.reply({ embeds: [error('Not found', 'Application not found.')], ephemeral: true });
  if (app.status !== 'pending') return interaction.reply({ embeds: [warning('Already processed', 'This application has already been processed.')], ephemeral: true });

  const modal = new ModalBuilder()
    .setCustomId(`staffapp_reason_modal:${verdict}:${appId}`)
    .setTitle(verdict === 'accept' ? 'Accept with Reason' : 'Deny with Reason');
  modal.addComponents(
    new ActionRowBuilder().addComponents(
      new TextInputBuilder()
        .setCustomId('reason')
        .setLabel('Reason')
        .setStyle(TextInputStyle.Paragraph)
        .setPlaceholder(
          verdict === 'accept'
            ? 'e.g. Great application, welcome to the team!'
            : 'e.g. Not enough experience at this time.'
        )
        .setMaxLength(1000)
        .setRequired(true)
    )
  );
  await interaction.showModal(modal);
}

async function handleReasonModal(interaction) {
  const parts   = interaction.customId.split(':');
  const verdict = parts[1];
  const appId   = parseInt(parts[2], 10);
  const reason  = interaction.fields.getTextInputValue('reason').trim();

  if (verdict === 'accept') return handleAccept(interaction, appId, reason);
  if (verdict === 'deny')   return handleDeny(interaction, appId, reason);
}

// ─── Open Ticket with User ────────────────────────────────────────────────────

async function handleOpenTicket(interaction, appId) {
  const app = staffappApplications.getById.get(appId);
  if (!app) return interaction.reply({ embeds: [error('Not found', 'Application not found.')], ephemeral: true });

  const cfg = ticketConfig.get.get(interaction.guildId);
  if (!cfg?.category_id)
    return interaction.reply({ embeds: [error('Not configured', 'No ticket system has been set up. Run `/ticket panel` first.')], ephemeral: true });

  await interaction.deferReply({ ephemeral: true });

  const member = await interaction.guild.members.fetch(app.user_id).catch(() => null);
  if (!member) return interaction.editReply({ embeds: [error('User not found', 'The applicant is no longer in the server.')] });

  const supportRoleIds = getSupportRoleIds(cfg);
  const guild          = interaction.guild;

  const sanitizedName = member.user.username
    .toLowerCase().replace(/[^a-z0-9_-]/g, '-').replace(/-{2,}/g, '-').replace(/^-|-$/g, '').substring(0, 25) || 'user';

  const permissionOverwrites = [
    { id: guild.roles.everyone.id, deny:  [PermissionFlagsBits.ViewChannel] },
    { id: app.user_id,             allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] },
    { id: interaction.user.id,     allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] },
    ...supportRoleIds.map(id => ({ id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] })),
  ];

  const channel = await guild.channels.create({
    name: `staffapp-${sanitizedName}`,
    type: ChannelType.GuildText,
    parent: cfg.category_id,
    permissionOverwrites,
  });

  const result   = tickets.create.run({ guild_id: interaction.guildId, channel_id: channel.id, ticket_num: cfg.next_ticket_num, owner_id: app.user_id, reason: `Staff application follow-up for <@&${app.applied_role_id}>` });
  const ticketId = result.lastInsertRowid;
  ticketConfig.bumpTicketNum.run(interaction.guildId);

  const embed = new EmbedBuilder()
    .setColor(0x5865F2)
    .setTitle('Staff Application — Follow Up')
    .setDescription(`${member} — a staff member would like to discuss your application for <@&${app.applied_role_id}>.`)
    .setFooter({ text: 'MostlyVanilla Beacon' })
    .setTimestamp();

  const pingParts = [member.toString(), interaction.user.toString(), ...supportRoleIds.map(id => `<@&${id}>`)];
  const closeRow  = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(`ticket_close:${ticketId}`).setLabel('Close Ticket').setEmoji('🔒').setStyle(ButtonStyle.Danger),
  );

  await channel.send({ content: pingParts.join(' '), embeds: [embed], components: [closeRow] });
  await interaction.editReply({ embeds: [success('Ticket Opened', `Ticket created for ${member} in ${channel}.`)] });
}

module.exports = { data, execute, handleButton, handleSelect, handleModal, handleDm };
