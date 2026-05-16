const { EmbedBuilder } = require('discord.js');
const { tickets } = require('./database');

function success(title, description) { return new EmbedBuilder().setColor(0x57F287).setTitle(`✅ ${title}`).setDescription(description ?? null); }
function error(title, description)   { return new EmbedBuilder().setColor(0xED4245).setTitle(`❌ ${title}`).setDescription(description ?? null); }
function warning(title, description) { return new EmbedBuilder().setColor(0xFEE75C).setTitle(`⚠️ ${title}`).setDescription(description ?? null); }
function info(title, description)    { return new EmbedBuilder().setColor(0x5865F2).setTitle(`ℹ️ ${title}`).setDescription(description ?? null); }

async function generateTranscript(channel, ticket, cfg, client) {
  if (!cfg?.log_channel_id) return;
  const logCh = await client.channels.fetch(cfg.log_channel_id).catch(() => null);
  if (!logCh) return;
  const messages = await channel.messages.fetch({ limit: 100 }).catch(() => null);
  if (!messages || messages.size === 0) return;
  const sorted = [...messages.values()].sort((a, b) => a.createdTimestamp - b.createdTimestamp);
  const lines = sorted.map(m => {
    const time   = new Date(m.createdTimestamp).toISOString().replace('T', ' ').slice(0, 19) + ' UTC';
    const author = m.author.bot ? `[BOT] ${m.author.username}` : `${m.author.username} (${m.author.id})`;
    const parts  = [];
    if (m.content) parts.push(m.content);
    for (const e of m.embeds) {
      const ep = [];
      if (e.title)       ep.push(`[Embed: ${e.title}]`);
      if (e.description) ep.push(e.description.substring(0, 300));
      parts.push(ep.join(' — ') || '[Embed]');
    }
    if (m.attachments.size) parts.push(`[${m.attachments.size} attachment(s)]`);
    return `[${time}] ${author}: ${parts.join(' | ') || '[no content]'}`;
  });
  const header = [
    `Ticket #${ticket.ticket_num} — Transcript`, '='.repeat(60),
    `Opened by : ${ticket.owner_id}`, `Channel   : ${channel.name}`,
    `Generated : ${new Date().toISOString()}`, '='.repeat(60), '',
  ].join('\n');
  await logCh.send({
    embeds: [
      new EmbedBuilder().setColor(0xED4245).setTitle('🎫 Ticket Closed')
        .addFields(
          { name: 'Ticket #',   value: String(ticket.ticket_num), inline: true },
          { name: 'Opened by', value: `<@${ticket.owner_id}>`,    inline: true },
          { name: 'Messages',  value: String(sorted.length),       inline: true },
        )
        .setFooter({ text: 'Transcript attached below' }).setTimestamp(),
    ],
    files: [{ attachment: Buffer.from(header + lines.join('\n'), 'utf-8'), name: `ticket-${ticket.ticket_num}-transcript.txt` }],
  }).catch(err => console.error('[Transcript]', err));
}

async function closeTicket(interaction, ticket, cfg) {
  tickets.update.run({ id: ticket.id, status: 'closed', claimed_by: ticket.claimed_by ?? null, closed_at: Math.floor(Date.now() / 1000) });
  await generateTranscript(interaction.channel, ticket, cfg, interaction.client);
  const send = (interaction.deferred || interaction.replied) ? p => interaction.editReply(p) : p => interaction.reply(p);
  await send({ embeds: [info('Ticket Closed', 'This channel will be deleted in 5 seconds.')] });
  setTimeout(() => interaction.channel.delete().catch(err => console.error('[Ticket Delete]', err)), 5000);
}

module.exports = { success, error, warning, info, closeTicket };
