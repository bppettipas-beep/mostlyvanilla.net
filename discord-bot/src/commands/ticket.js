const {
  SlashCommandBuilder, PermissionFlagsBits, ChannelType,
  ActionRowBuilder, ButtonBuilder, ButtonStyle,
  EmbedBuilder, ModalBuilder, TextInputBuilder, TextInputStyle,
} = require('discord.js');
const { ticketConfig, getSupportRoleIds, tickets, ticketQuestions } = require('../database');
const { success, error, warning, info, closeTicket } = require('../ticketUtils');
const panel = require('../panel');

const PANEL_STYLE_MAP = { 1: 'blue', 2: 'grey', 3: 'green', 4: 'red' };

const data = new SlashCommandBuilder()
  .setName('ticket').setDescription('Ticket management commands.')
  .addSubcommand(sc => sc.setName('edit').setDescription('Edit an existing bot message or ticket panel.')
    .addStringOption(o => o.setName('message_id').setDescription('The ID of the message to edit.').setRequired(true)))
  .addSubcommand(sc => sc.setName('panel').setDescription('Build and post a customizable ticket panel.')
    .addChannelOption(o => o.setName('channel').setDescription('Channel to post the panel in.').addChannelTypes(ChannelType.GuildText).setRequired(true)))
  .addSubcommand(sc => sc.setName('close').setDescription('Close the current ticket.')
    .addStringOption(o => o.setName('reason').setDescription('Optional reason for closing.').setRequired(false)))
  .addSubcommand(sc => sc.setName('rename').setDescription('Rename this ticket channel.')
    .addStringOption(o => o.setName('name').setDescription('New name for the channel.').setRequired(true)));

async function execute(interaction) {
  const sub = interaction.options.getSubcommand();
  if (sub === 'edit')   return handleTicketEdit(interaction);
  if (sub === 'panel')  return handleTicketPanel(interaction);
  if (sub === 'close')  return handleTicketClose(interaction);
  if (sub === 'rename') return handleTicketRename(interaction);
}

async function handleTicketEdit(interaction) {
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator))
    return interaction.reply({ embeds: [error('Permission denied', 'Only administrators can edit messages.')], ephemeral: true });

  const messageId = interaction.options.getString('message_id').trim();
  const msg = await interaction.channel.messages.fetch(messageId).catch(() => null);
  if (!msg) return interaction.reply({ embeds: [error('Not found', 'Could not find that message in this channel.')], ephemeral: true });
  if (msg.author.id !== interaction.client.user.id) return interaction.reply({ embeds: [error('Not my message', 'I can only edit messages sent by me.')], ephemeral: true });

  const isPanel = msg.components.some(row => row.components.some(c => c.customId?.startsWith('ticket_create:')));
  if (isPanel) {
    const embed   = msg.embeds[0];
    const cfg     = ticketConfig.get.get(interaction.guildId);
    const buttons = [];
    for (const row of msg.components) {
      for (const comp of row.components) {
        if (!comp.customId?.startsWith('ticket_create:')) continue;
        const prefix   = comp.customId.split(':')[1] || 'ticket';
        const qRow     = ticketQuestions.get.get(interaction.guildId, prefix);
        const emojiStr = comp.emoji ? (comp.emoji.id ? `<${comp.emoji.animated ? 'a' : ''}:${comp.emoji.name}:${comp.emoji.id}>` : comp.emoji.name ?? '') : '';
        buttons.push({ label: comp.label ?? '', emoji: emojiStr, style: PANEL_STYLE_MAP[comp.style] ?? 'blue', prefix, questions: qRow ? JSON.parse(qRow.questions) : [] });
      }
    }
    return panel.openForEdit(interaction, { channelId: msg.channelId, editMessageId: msg.id, title: embed?.title ?? '', description: embed?.description ?? '', color: embed?.color ?? 0xF4A460, categoryId: cfg?.category_id ?? null, logChannelId: cfg?.log_channel_id ?? null, supportRoleIds: getSupportRoleIds(cfg), buttons });
  }

  const embed        = msg.embeds[0];
  const currentTitle = embed?.title ?? '';
  const currentDesc  = embed?.description ?? msg.content ?? '';
  const modal = new ModalBuilder().setCustomId(`ticket_edit_modal:${messageId}`).setTitle('Edit Message');
  modal.addComponents(
    new ActionRowBuilder().addComponents(new TextInputBuilder().setCustomId('title').setLabel('Embed Title').setStyle(TextInputStyle.Short).setValue(currentTitle).setMaxLength(256).setRequired(false)),
    new ActionRowBuilder().addComponents(new TextInputBuilder().setCustomId('description').setLabel('Embed Description / Message Content').setStyle(TextInputStyle.Paragraph).setValue(currentDesc.substring(0, 4000)).setMaxLength(4000).setRequired(false)),
  );
  await interaction.showModal(modal);
}

async function handleTicketPanel(interaction) {
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator))
    return interaction.reply({ embeds: [error('Permission denied', 'Only administrators can create panels.')], ephemeral: true });
  const channelId = interaction.options.getChannel('channel').id;
  const modal = new ModalBuilder().setCustomId(`ticket_panel_modal:${channelId}`).setTitle('Ticket Panel Builder');
  modal.addComponents(
    new ActionRowBuilder().addComponents(new TextInputBuilder().setCustomId('title').setLabel('Panel Title').setStyle(TextInputStyle.Short).setPlaceholder('e.g. Support Tickets').setMaxLength(100).setRequired(true)),
    new ActionRowBuilder().addComponents(new TextInputBuilder().setCustomId('description').setLabel('Panel Description').setStyle(TextInputStyle.Paragraph).setPlaceholder('e.g. Click a button below to open a ticket.').setMaxLength(2000).setRequired(true)),
    new ActionRowBuilder().addComponents(new TextInputBuilder().setCustomId('color').setLabel('Embed Color (hex, e.g. F4A460)').setStyle(TextInputStyle.Short).setPlaceholder('F4A460').setMaxLength(7).setRequired(false)),
  );
  await interaction.showModal(modal);
}

async function handleTicketClose(interaction) {
  const ticket = tickets.getByChannel.get(interaction.channelId);
  const cfg    = ticketConfig.get.get(interaction.guildId);
  if (!ticket || ticket.status !== 'open')
    return interaction.reply({ embeds: [error('Not a ticket', 'This command can only be used inside an open ticket channel.')], ephemeral: true });
  const closeRoles = getSupportRoleIds(cfg);
  const canClose   = interaction.user.id === ticket.owner_id || closeRoles.some(id => interaction.member?.roles.cache.has(id)) || interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels);
  if (!canClose) return interaction.reply({ embeds: [error('Permission denied', 'Only the ticket owner or staff can close this ticket.')], ephemeral: true });
  await interaction.deferReply();
  await closeTicket(interaction, ticket, cfg);
}

async function handleTicketRename(interaction) {
  const ticket = tickets.getByChannel.get(interaction.channelId);
  if (!ticket) return interaction.reply({ embeds: [error('Not a ticket', 'This command can only be used inside a ticket channel.')], ephemeral: true });
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.Administrator)) return interaction.reply({ embeds: [error('Permission denied', 'Only administrators can rename tickets.')], ephemeral: true });
  const newName = interaction.options.getString('name').toLowerCase().replace(/[^a-z0-9_-]/g, '-').replace(/-{2,}/g, '-').replace(/^-|-$/g, '').substring(0, 100);
  if (!newName) return interaction.reply({ embeds: [error('Invalid name', 'Channel name must contain at least one valid character.')], ephemeral: true });
  await interaction.deferReply({ ephemeral: true });
  await interaction.channel.setName(newName);
  await interaction.editReply({ embeds: [success('Renamed', `Channel renamed to **${newName}**.`)] });
}

// ─── Interaction routers ───────────────────────────────────────────────────────

async function handleButton(interaction) {
  const [action, ...args] = interaction.customId.split(':');

  if (action === 'ticket_create') return handleTicketCreate(interaction);

  if (action === 'ticket_panel_addbtn')         return panel.onAddButtonBtn(interaction);
  if (action === 'ticket_panel_removebtn')      return panel.onRemoveLastBtn(interaction);
  if (action === 'ticket_panel_edit')           return panel.onEditButton(interaction);
  if (action === 'ticket_panel_post')           return panel.onPostButton(interaction);
  if (action === 'ticket_panel_cancel')         return panel.onCancelButton(interaction);
  if (action === 'ticket_panel_btn_done')       return panel.onBtnDone(interaction);
  if (action === 'ticket_panel_btn_clearcat')   return panel.onBtnClearCat(interaction);
  if (action === 'ticket_panel_btn_clearroles') return panel.onBtnClearRoles(interaction);

  if (action === 'ticket_claim') {
    const ticketId  = parseInt(args[0], 10);
    const ticket    = tickets.getById.get(ticketId);
    const cfg       = ticketConfig.get.get(interaction.guildId);
    if (!ticket || ticket.status !== 'open')
      return interaction.reply({ embeds: [error('Ticket not found', 'This ticket could not be found or is already closed.')], ephemeral: true });
    const claimRoles = getSupportRoleIds(cfg);
    if (!claimRoles.some(id => interaction.member?.roles.cache.has(id)))
      return interaction.reply({ embeds: [error('Permission denied', 'Only support staff can claim tickets.')], ephemeral: true });
    tickets.update.run({ id: ticketId, status: 'open', claimed_by: interaction.user.id, closed_at: null });
    return interaction.reply({ embeds: [info('Ticket Claimed', `${interaction.user} has claimed this ticket.`)] });
  }

  if (action === 'ticket_close') {
    const ticketId  = parseInt(args[0], 10);
    const ticket    = tickets.getById.get(ticketId);
    const cfg       = ticketConfig.get.get(interaction.guildId);
    if (!ticket || ticket.status !== 'open')
      return interaction.reply({ embeds: [error('Ticket not found', 'This ticket could not be found or is already closed.')], ephemeral: true });
    const closeRoles = getSupportRoleIds(cfg);
    const canClose   = interaction.user.id === ticket.owner_id || closeRoles.some(id => interaction.member?.roles.cache.has(id)) || interaction.memberPermissions?.has(PermissionFlagsBits.ManageChannels);
    if (!canClose)
      return interaction.reply({ embeds: [error('Permission denied', 'Only the ticket owner or staff can close this.')], ephemeral: true });
    await interaction.deferReply();
    return closeTicket(interaction, ticket, cfg);
  }
}

async function handleSelect(interaction) {
  if (interaction.customId === 'ticket_panel_category')             return panel.onCategorySelect(interaction);
  if (interaction.customId === 'ticket_panel_logchannel')           return panel.onLogChannelSelect(interaction);
  if (interaction.customId === 'ticket_panel_supportrole')          return panel.onSupportRoleSelect(interaction);
  if (interaction.customId === 'ticket_panel_editbtn_select')       return panel.onEditBtnSelect(interaction);
  if (interaction.customId.startsWith('ticket_panel_btn_category')) return panel.onBtnCategorySelect(interaction);
  if (interaction.customId.startsWith('ticket_panel_btn_rolesel'))  return panel.onBtnRoleSelect(interaction);
}

async function handleModal(interaction) {
  if (interaction.customId.startsWith('ticket_panel_modal:'))          return panel.onModalSubmit(interaction);
  if (interaction.customId === 'ticket_panel_addbtn_modal')            return panel.onAddButtonModalSubmit(interaction);
  if (interaction.customId.startsWith('ticket_qs_form:'))              return handleTicketQsForm(interaction);
  if (interaction.customId.startsWith('ticket_qs_setup:'))             return handleQsSetup(interaction);
  if (interaction.customId.startsWith('ticket_edit_modal:'))           return handleEditModal(interaction);
  if (interaction.customId.startsWith('ticket_panel_editbtn_modal:'))  return panel.onEditBtnModalSubmit(interaction);
}

// ─── Ticket creation ───────────────────────────────────────────────────────────

async function handleTicketCreate(interaction) {
  const prefix = interaction.customId.split(':')[1] || 'ticket';
  const cfg    = ticketConfig.get.get(interaction.guildId);
  const btnCfg = ticketQuestions.get.get(interaction.guildId, prefix);

  if (!btnCfg?.category_id && !cfg?.category_id)
    return interaction.reply({ embeds: [error('Not configured', 'No ticket panel has been set up yet.')], ephemeral: true });

  const existing = tickets.listOpen.all(interaction.guildId).find(t => t.owner_id === interaction.user.id);
  if (existing)
    return interaction.reply({ embeds: [warning('Already open', `You already have a ticket open: <#${existing.channel_id}>`)], ephemeral: true });

  const qList = btnCfg?.questions ? JSON.parse(btnCfg.questions) : [];

  if (qList.length > 0) {
    const typeName = prefix.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    const modal = new ModalBuilder().setCustomId(`ticket_qs_form:${prefix}`).setTitle(`${typeName} — Open a Ticket`);
    qList.slice(0, 5).forEach((q, i) => {
      const isParagraph = /explain|describe|proof|detail|reason|issue|problem/i.test(q.label);
      modal.addComponents(
        new ActionRowBuilder().addComponents(
          new TextInputBuilder().setCustomId(`q${i}`).setLabel(q.label.substring(0, 45))
            .setStyle(isParagraph ? TextInputStyle.Paragraph : TextInputStyle.Short)
            .setRequired(q.required).setMaxLength(500)
        )
      );
    });
    return interaction.showModal(modal);
  }

  return createTicketChannel(interaction, prefix, []);
}

async function handleTicketQsForm(interaction) {
  const prefix = interaction.customId.split(':')[1] || 'ticket';
  const qRow   = ticketQuestions.get.get(interaction.guildId, prefix);
  const qList  = qRow ? JSON.parse(qRow.questions) : [];
  const answers = qList.slice(0, 5).map((q, i) => ({
    question: q.label,
    answer:   interaction.fields.getTextInputValue(`q${i}`),
  }));
  return createTicketChannel(interaction, prefix, answers);
}

async function handleQsSetup(interaction) {
  const prefix = interaction.customId.split(':')[1];
  const raw    = interaction.fields.getTextInputValue('questions').trim();
  const questions = raw
    ? raw.split('\n').map(l => l.trim()).filter(Boolean).slice(0, 5).map(l => ({
        label:    l.startsWith('*') ? l.slice(1).trim() : l,
        required: !l.startsWith('*'),
      }))
    : [];
  const existing = ticketQuestions.get.get(interaction.guildId, prefix);
  ticketQuestions.upsert.run({ guild_id: interaction.guildId, prefix, questions: JSON.stringify(questions), category_id: existing?.category_id ?? null, support_role_ids: existing?.support_role_ids ?? null });
  await interaction.reply({ embeds: [success('Questions Saved', `Set **${questions.length}** question(s) for **${prefix}** tickets.`)], ephemeral: true });
}

async function handleEditModal(interaction) {
  const messageId = interaction.customId.split(':')[1];
  const msg = await interaction.channel.messages.fetch(messageId).catch(() => null);
  if (!msg) return interaction.reply({ embeds: [error('Not found', 'Message could not be found.')], ephemeral: true });

  const newTitle = interaction.fields.getTextInputValue('title').trim();
  const newDesc  = interaction.fields.getTextInputValue('description').trim();

  if (msg.embeds.length > 0) {
    const updated = EmbedBuilder.from(msg.embeds[0]);
    if (newTitle) updated.setTitle(newTitle); else updated.setTitle(null);
    if (newDesc)  updated.setDescription(newDesc); else updated.setDescription(null);
    await msg.edit({ embeds: [updated] });
  } else {
    await msg.edit({ content: newDesc || null });
  }
  await interaction.reply({ embeds: [success('Message Updated', 'The message has been edited.')], ephemeral: true });
}

async function createTicketChannel(interaction, prefix, answers) {
  await interaction.deferReply({ ephemeral: true });

  const cfg    = ticketConfig.get.get(interaction.guildId);
  const btnCfg = ticketQuestions.get.get(interaction.guildId, prefix);

  const categoryId = btnCfg?.category_id || cfg?.category_id;
  if (!categoryId) return interaction.editReply({ embeds: [error('Not configured', 'No ticket panel has been set up yet.')] });

  let supportRoleIds;
  if (btnCfg?.support_role_ids) {
    try { supportRoleIds = JSON.parse(btnCfg.support_role_ids); } catch { supportRoleIds = []; }
  } else {
    supportRoleIds = getSupportRoleIds(cfg);
  }

  const existing = tickets.listOpen.all(interaction.guildId).find(t => t.owner_id === interaction.user.id);
  if (existing) return interaction.editReply({ embeds: [warning('Already open', `You already have a ticket open: <#${existing.channel_id}>`)] });

  const typeName = prefix.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  const num      = cfg.next_ticket_num;
  const guild    = interaction.guild;

  const sanitizedName = interaction.user.username
    .toLowerCase().replace(/[^a-z0-9_-]/g, '-').replace(/-{2,}/g, '-').replace(/^-|-$/g, '').substring(0, 25) || 'user';

  const permissionOverwrites = [
    { id: guild.roles.everyone.id, deny:  [PermissionFlagsBits.ViewChannel] },
    { id: interaction.user.id,     allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] },
    ...supportRoleIds.map(id => ({ id, allow: [PermissionFlagsBits.ViewChannel, PermissionFlagsBits.SendMessages, PermissionFlagsBits.ReadMessageHistory] })),
  ];

  const channel = await guild.channels.create({
    name: `${prefix}-${sanitizedName}`,
    type: ChannelType.GuildText,
    parent: categoryId,
    permissionOverwrites,
  });

  const result = tickets.create.run({ guild_id: interaction.guildId, channel_id: channel.id, ticket_num: num, owner_id: interaction.user.id, reason: answers.length ? answers.map(a => `${a.question}: ${a.answer}`).join('\n') : null });
  ticketConfig.bumpTicketNum.run(interaction.guildId);

  const ticketId    = result.lastInsertRowid;
  const displayName = interaction.member?.displayName ?? interaction.user.username;

  const greeting = new EmbedBuilder()
    .setColor(0xF4A460).setTitle(`🎫 ${typeName} — ${displayName}`)
    .setDescription(`Hello ${interaction.user}! Support staff will be with you shortly.`)
    .setFooter({ text: 'Use /ticket close to close this ticket.' }).setTimestamp();

  if (answers.length > 0) greeting.addFields(answers.map(a => ({ name: a.question, value: a.answer || '—', inline: false })));

  const row = new ActionRowBuilder().addComponents(
    new ButtonBuilder().setCustomId(`ticket_claim:${ticketId}`).setLabel('Claim').setEmoji('🙋').setStyle(ButtonStyle.Secondary),
    new ButtonBuilder().setCustomId(`ticket_close:${ticketId}`).setLabel('Close').setEmoji('🔒').setStyle(ButtonStyle.Danger),
  );

  const pingContent = supportRoleIds.length ? `${interaction.user} ${supportRoleIds.map(id => `<@&${id}>`).join(' ')}` : `${interaction.user}`;
  await channel.send({ content: pingContent, embeds: [greeting], components: [row] });

  if (cfg.log_channel_id) {
    const logCh = await interaction.client.channels.fetch(cfg.log_channel_id).catch(() => null);
    if (logCh) {
      const logEmbed = new EmbedBuilder().setColor(0x57F287).setTitle(`${typeName} Ticket Opened`)
        .addFields(
          { name: 'User',    value: `${interaction.user} (${interaction.user.id})`, inline: true },
          { name: 'Channel', value: `<#${channel.id}>`, inline: true },
          { name: 'Type',    value: typeName, inline: true },
        ).setTimestamp();
      if (answers.length > 0) logEmbed.addFields(answers.map(a => ({ name: a.question, value: a.answer || '—', inline: false })));
      await logCh.send({ embeds: [logEmbed] }).catch(() => {});
    }
  }

  await interaction.editReply({ embeds: [success('Ticket Created', `Your ticket is open in ${channel}.`)] });
}

module.exports = { data, execute, handleButton, handleSelect, handleModal };
