const {
    SlashCommandBuilder, PermissionFlagsBits,
    EmbedBuilder, ChannelType,
} = require('discord.js');
const db = require('../database');

const GREEN      = 0x2ECC71;
const DARK_GREEN = 0x27AE60;
const RED        = 0xE74C3C;
const FOOTER     = 'Mostly Vanilla • Welcome';

const DEFAULT_MESSAGE = 'Welcome to **{server}**, {user}! Your Minecraft account **{mcname}** is now linked. Enjoy your stay!';

async function sendWelcomeMessage(member, mcName) {
    const channelId = db.getSetting('welcome_channel_id');
    if (!channelId) return;

    const channel = member.guild.channels.cache.get(channelId);
    if (!channel) return;

    const template = db.getSetting('welcome_message') || DEFAULT_MESSAGE;
    const text = template
        .replace(/{user}/g,     member.toString())
        .replace(/{username}/g, member.user.username)
        .replace(/{mcname}/g,   mcName)
        .replace(/{server}/g,   member.guild.name);

    const embed = new EmbedBuilder()
        .setColor(GREEN)
        .setTitle(`Welcome to ${member.guild.name}!`)
        .setDescription(text)
        .setThumbnail(member.user.displayAvatarURL({ dynamic: true }))
        .addFields(
            { name: 'Minecraft IGN', value: mcName,               inline: true },
            { name: 'Member',        value: member.user.username,  inline: true },
        )
        .setFooter({ text: FOOTER })
        .setTimestamp();

    try {
        await channel.send({ content: member.toString(), embeds: [embed] });
    } catch (err) {
        console.error('[Welcome] Failed to send welcome message:', err.message);
    }
}

const data = new SlashCommandBuilder()
    .setName('welcome')
    .setDescription('Configure the server welcome message')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageGuild)
    .addSubcommand(s => s
        .setName('setchannel')
        .setDescription('Set which channel welcome messages are sent in')
        .addChannelOption(o => o
            .setName('channel')
            .setDescription('Channel to send welcome messages in')
            .addChannelTypes(ChannelType.GuildText)
            .setRequired(true)))
    .addSubcommand(s => s
        .setName('setmessage')
        .setDescription('Set the welcome message text')
        .addStringOption(o => o
            .setName('message')
            .setDescription('Placeholders: {user} {username} {mcname} {server}')
            .setRequired(true)))
    .addSubcommand(s => s
        .setName('test')
        .setDescription('Send a test welcome message to the configured channel'))
    .addSubcommand(s => s
        .setName('view')
        .setDescription('View current welcome settings'))
    .addSubcommand(s => s
        .setName('disable')
        .setDescription('Disable welcome messages'));

async function execute(interaction) {
    const sub = interaction.options.getSubcommand();

    if (sub === 'setchannel') {
        const channel = interaction.options.getChannel('channel');
        db.setSetting('welcome_channel_id', channel.id);
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(GREEN)
                .setTitle('Welcome Channel Set')
                .setDescription(`Welcome messages will be sent in ${channel}.`)
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'setmessage') {
        const message = interaction.options.getString('message');
        db.setSetting('welcome_message', message);
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(GREEN)
                .setTitle('Welcome Message Updated')
                .setDescription(`**New message:**\n${message}`)
                .addFields({ name: 'Available Placeholders', value: '`{user}` `{username}` `{mcname}` `{server}`' })
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'test') {
        const channelId = db.getSetting('welcome_channel_id');
        if (!channelId) {
            return interaction.reply({ embeds: [
                new EmbedBuilder()
                    .setColor(RED)
                    .setTitle('No Channel Set')
                    .setDescription('Set a welcome channel first with `/welcome setchannel`.')
                    .setFooter({ text: FOOTER }),
            ], ephemeral: true });
        }
        await sendWelcomeMessage(interaction.member, 'Steve');
        return interaction.reply({ content: 'Test welcome sent!', ephemeral: true });
    }

    if (sub === 'view') {
        const channelId = db.getSetting('welcome_channel_id');
        const message   = db.getSetting('welcome_message') || `*(default)*\n${DEFAULT_MESSAGE}`;
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(DARK_GREEN)
                .setTitle('Welcome Settings')
                .addFields(
                    { name: 'Status',   value: channelId ? '✅ Enabled' : '❌ Disabled', inline: true },
                    { name: 'Channel',  value: channelId ? `<#${channelId}>` : 'Not set', inline: true },
                    { name: 'Message',  value: message },
                )
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'disable') {
        db.deleteSetting('welcome_channel_id');
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(RED)
                .setTitle('Welcome Messages Disabled')
                .setDescription('Run `/welcome setchannel` to re-enable.')
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }
}

module.exports = { data, execute, sendWelcomeMessage };
