const {
    SlashCommandBuilder, PermissionFlagsBits,
    EmbedBuilder, ChannelType,
} = require('discord.js');

const GREEN  = 0x2ECC71;
const FOOTER = 'Mostly Vanilla';

const data = new SlashCommandBuilder()
    .setName('embed')
    .setDescription('Post a green-themed embed to a channel')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageMessages)
    .addStringOption(o => o
        .setName('description')
        .setDescription('Main body text of the embed')
        .setRequired(true)
        .setMaxLength(4096))
    .addStringOption(o => o
        .setName('title')
        .setDescription('Title of the embed')
        .setRequired(false)
        .setMaxLength(256))
    .addChannelOption(o => o
        .setName('channel')
        .setDescription('Channel to post in (defaults to current channel)')
        .addChannelTypes(ChannelType.GuildText)
        .setRequired(false))
    .addStringOption(o => o
        .setName('footer')
        .setDescription('Footer text')
        .setRequired(false)
        .setMaxLength(2048))
    .addStringOption(o => o
        .setName('image')
        .setDescription('Image URL to display in the embed')
        .setRequired(false));

async function execute(interaction) {
    const description = interaction.options.getString('description');
    const title       = interaction.options.getString('title');
    const channel     = interaction.options.getChannel('channel') ?? interaction.channel;
    const footer      = interaction.options.getString('footer');
    const image       = interaction.options.getString('image');

    const embed = new EmbedBuilder()
        .setColor(GREEN)
        .setDescription(description)
        .setTimestamp();

    if (title)  embed.setTitle(title);
    if (footer) embed.setFooter({ text: footer });
    else        embed.setFooter({ text: FOOTER });
    if (image)  embed.setImage(image);

    try {
        await channel.send({ embeds: [embed] });
        await interaction.reply({ content: `Embed posted in ${channel}.`, ephemeral: true });
    } catch (err) {
        console.error('[Embed] Failed to send embed:', err.message);
        await interaction.reply({
            content: 'Failed to post the embed. Check that I have permission to send messages in that channel.',
            ephemeral: true,
        });
    }
}

module.exports = { data, execute };
