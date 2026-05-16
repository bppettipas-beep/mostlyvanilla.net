const {
    SlashCommandBuilder, PermissionFlagsBits,
    EmbedBuilder, ActionRowBuilder,
    ButtonBuilder, ButtonStyle,
    StringSelectMenuBuilder,
    ModalBuilder, TextInputBuilder, TextInputStyle,
} = require('discord.js');

const DEFAULT_COLOR = 0x2ECC71;

const COLORS = {
    green:  0x2ECC71,
    teal:   0x1ABC9C,
    blue:   0x3498DB,
    purple: 0x9B59B6,
    gold:   0xF1C40F,
    orange: 0xE67E22,
    red:    0xE74C3C,
    pink:   0xFF69B4,
    white:  0xFFFFFF,
    dark:   0x2C3E50,
    gray:   0x95A5A6,
    black:  0x000001,
};

const COLOR_OPTIONS = [
    { label: 'Green 🟢',  value: 'green',  description: '#2ECC71 — Server theme' },
    { label: 'Teal 🩵',   value: 'teal',   description: '#1ABC9C' },
    { label: 'Blue 🔵',   value: 'blue',   description: '#3498DB' },
    { label: 'Purple 🟣', value: 'purple', description: '#9B59B6' },
    { label: 'Gold 🟡',   value: 'gold',   description: '#F1C40F' },
    { label: 'Orange 🔶', value: 'orange', description: '#E67E22' },
    { label: 'Red 🔴',    value: 'red',    description: '#E74C3C' },
    { label: 'Pink 🩷',   value: 'pink',   description: '#FF69B4' },
    { label: 'White ⬜',  value: 'white',  description: '#FFFFFF' },
    { label: 'Dark 🌑',   value: 'dark',   description: '#2C3E50' },
    { label: 'Gray 🩶',   value: 'gray',   description: '#95A5A6' },
    { label: 'Custom ✏️', value: 'custom', description: 'Enter a custom hex code' },
];

// In-memory sessions: key = `${userId}_${interactionId}`
const sessions = new Map();

function blankEmbed(authorId) {
    return {
        title: '', titleUrl: '', description: '', color: DEFAULT_COLOR,
        authorName: '', authorIcon: '', footerText: '', footerIcon: '',
        thumbnailUrl: '', imageUrl: '', fields: [], timestamp: false,
        authorId,
    };
}

function buildEmbed(s, placeholder = false) {
    const embed = new EmbedBuilder().setColor(s.color);
    if (s.title)       embed.setTitle(s.title);
    if (s.title && s.titleUrl) embed.setURL(s.titleUrl);
    if (s.description) embed.setDescription(s.description);
    else if (placeholder) embed.setDescription('*(no description set)*');
    if (s.authorName)  embed.setAuthor({ name: s.authorName, iconURL: s.authorIcon || undefined });
    if (s.footerText)  embed.setFooter({ text: s.footerText, iconURL: s.footerIcon || undefined });
    if (s.thumbnailUrl) embed.setThumbnail(s.thumbnailUrl);
    if (s.imageUrl)    embed.setImage(s.imageUrl);
    for (const f of s.fields) embed.addFields({ name: f.name, value: f.value, inline: f.inline });
    if (s.timestamp)   embed.setTimestamp();
    return embed;
}

function buildPanelEmbed(s) {
    const hex = '#' + s.color.toString(16).toUpperCase().padStart(6, '0');
    const fieldList = s.fields.length
        ? s.fields.map((f, i) => `\`${i + 1}.\` ${f.name}${f.inline ? ' *(inline)*' : ''}`).join('\n')
        : '*(none)*';

    return new EmbedBuilder()
        .setColor(s.color)
        .setTitle('Embed Builder')
        .addFields(
            { name: 'Color',       value: `\`${hex}\``,                                                         inline: true },
            { name: 'Timestamp',   value: s.timestamp ? '✅ On' : '❌ Off',                                    inline: true },
            { name: 'Title',       value: s.title       ? `\`${s.title.slice(0, 60)}\`` : '*(none)*',           inline: false },
            { name: 'Description', value: s.description ? s.description.slice(0, 80) + (s.description.length > 80 ? '…' : '') : '*(none)*', inline: false },
            { name: 'Author',      value: s.authorName  || '*(none)*',                                          inline: true },
            { name: 'Footer',      value: s.footerText  || '*(none)*',                                          inline: true },
            { name: 'Thumbnail',   value: s.thumbnailUrl ? '✅' : '❌',                                        inline: true },
            { name: 'Image',       value: s.imageUrl     ? '✅' : '❌',                                        inline: true },
            { name: `Fields (${s.fields.length}/25)`, value: fieldList,                                         inline: false },
        );
}

function buildPanelComponents(sid, s) {
    const row1 = new ActionRowBuilder().addComponents(
        new StringSelectMenuBuilder()
            .setCustomId(`embed_color:${sid}`)
            .setPlaceholder('🎨 Pick a color…')
            .addOptions(COLOR_OPTIONS)
    );

    const row2 = new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(`embed_title:${sid}`).setLabel('Title').setStyle(ButtonStyle.Secondary),
        new ButtonBuilder().setCustomId(`embed_desc:${sid}`).setLabel('Description').setStyle(ButtonStyle.Secondary),
        new ButtonBuilder().setCustomId(`embed_author:${sid}`).setLabel('Author').setStyle(ButtonStyle.Secondary),
        new ButtonBuilder().setCustomId(`embed_footer:${sid}`).setLabel('Footer').setStyle(ButtonStyle.Secondary),
        new ButtonBuilder().setCustomId(`embed_images:${sid}`).setLabel('Images').setStyle(ButtonStyle.Secondary),
    );

    const row3 = new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setCustomId(`embed_timestamp:${sid}`)
            .setLabel(`Timestamp: ${s.timestamp ? 'On' : 'Off'}`)
            .setStyle(s.timestamp ? ButtonStyle.Success : ButtonStyle.Secondary),
        new ButtonBuilder().setCustomId(`embed_addfield:${sid}`).setLabel('Add Field').setStyle(ButtonStyle.Secondary),
        new ButtonBuilder().setCustomId(`embed_removefield:${sid}`).setLabel('Remove Field').setStyle(ButtonStyle.Secondary),
    );

    const row4 = new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(`embed_preview:${sid}`).setLabel('Preview').setStyle(ButtonStyle.Primary),
        new ButtonBuilder().setCustomId(`embed_clear:${sid}`).setLabel('Clear All').setStyle(ButtonStyle.Danger),
        new ButtonBuilder().setCustomId(`embed_post:${sid}`).setLabel('Post Embed').setStyle(ButtonStyle.Success),
    );

    return [row1, row2, row3, row4];
}

// ---- Slash command entry ----

const data = new SlashCommandBuilder()
    .setName('embed')
    .setDescription('Build and post a fully custom embed')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageMessages);

async function execute(interaction) {
    const sid = `${interaction.user.id}_${interaction.id}`;
    sessions.set(sid, blankEmbed(interaction.user.id));
    const s = sessions.get(sid);

    await interaction.reply({
        embeds: [buildPanelEmbed(s)],
        components: buildPanelComponents(sid, s),
        ephemeral: true,
    });

    setTimeout(() => sessions.delete(sid), 10 * 60_000);
}

// ---- Component interactions (buttons + select) ----

async function handleInteraction(interaction) {
    const customId = interaction.customId;

    // Color select menu
    if (interaction.isStringSelectMenu() && customId.startsWith('embed_color:')) {
        const sid = customId.slice('embed_color:'.length);
        const s = sessions.get(sid);
        if (!s) return interaction.reply({ content: 'Session expired. Run `/embed` again.', ephemeral: true });
        if (interaction.user.id !== s.authorId) return interaction.reply({ content: 'This panel belongs to someone else.', ephemeral: true });

        const value = interaction.values[0];
        if (value === 'custom') {
            return interaction.showModal(
                new ModalBuilder()
                    .setCustomId(`embed_customcolor:${sid}`)
                    .setTitle('Custom Color')
                    .addComponents(new ActionRowBuilder().addComponents(
                        new TextInputBuilder()
                            .setCustomId('hex')
                            .setLabel('Hex color code (e.g. 2ECC71)')
                            .setStyle(TextInputStyle.Short)
                            .setMaxLength(7)
                            .setRequired(true)
                    ))
            );
        }

        s.color = COLORS[value];
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (!interaction.isButton()) return;

    const colonIdx = customId.indexOf(':');
    const action = customId.slice(0, colonIdx);
    const sid    = customId.slice(colonIdx + 1);
    const s = sessions.get(sid);
    if (!s) return interaction.reply({ content: 'Session expired. Run `/embed` again.', ephemeral: true });
    if (interaction.user.id !== s.authorId) return interaction.reply({ content: 'This panel belongs to someone else.', ephemeral: true });

    if (action === 'embed_title') {
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_title:${sid}`)
                .setTitle('Set Title')
                .addComponents(
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('title').setLabel('Title (max 256 chars)').setStyle(TextInputStyle.Short).setMaxLength(256).setRequired(false).setValue(s.title)
                    ),
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('url').setLabel('Title URL (optional)').setStyle(TextInputStyle.Short).setMaxLength(500).setRequired(false).setValue(s.titleUrl)
                    ),
                )
        );
    }

    if (action === 'embed_desc') {
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_desc:${sid}`)
                .setTitle('Set Description')
                .addComponents(new ActionRowBuilder().addComponents(
                    new TextInputBuilder().setCustomId('desc').setLabel('Description (supports markdown)').setStyle(TextInputStyle.Paragraph).setMaxLength(4000).setRequired(false).setValue(s.description)
                ))
        );
    }

    if (action === 'embed_author') {
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_author:${sid}`)
                .setTitle('Set Author')
                .addComponents(
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('name').setLabel('Author Name').setStyle(TextInputStyle.Short).setMaxLength(256).setRequired(false).setValue(s.authorName)
                    ),
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('icon').setLabel('Author Icon URL (optional)').setStyle(TextInputStyle.Short).setMaxLength(500).setRequired(false).setValue(s.authorIcon)
                    ),
                )
        );
    }

    if (action === 'embed_footer') {
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_footer:${sid}`)
                .setTitle('Set Footer')
                .addComponents(
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('text').setLabel('Footer Text').setStyle(TextInputStyle.Short).setMaxLength(2048).setRequired(false).setValue(s.footerText)
                    ),
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('icon').setLabel('Footer Icon URL (optional)').setStyle(TextInputStyle.Short).setMaxLength(500).setRequired(false).setValue(s.footerIcon)
                    ),
                )
        );
    }

    if (action === 'embed_images') {
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_images:${sid}`)
                .setTitle('Set Images')
                .addComponents(
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('thumbnail').setLabel('Thumbnail URL (small, top-right)').setStyle(TextInputStyle.Short).setMaxLength(500).setRequired(false).setValue(s.thumbnailUrl)
                    ),
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('image').setLabel('Image URL (large, bottom)').setStyle(TextInputStyle.Short).setMaxLength(500).setRequired(false).setValue(s.imageUrl)
                    ),
                )
        );
    }

    if (action === 'embed_timestamp') {
        s.timestamp = !s.timestamp;
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_addfield') {
        if (s.fields.length >= 25) return interaction.reply({ content: 'Max 25 fields reached.', ephemeral: true });
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_addfield:${sid}`)
                .setTitle('Add Field')
                .addComponents(
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('name').setLabel('Field Name').setStyle(TextInputStyle.Short).setMaxLength(256).setRequired(true)
                    ),
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('value').setLabel('Field Value (supports markdown)').setStyle(TextInputStyle.Paragraph).setMaxLength(1024).setRequired(true)
                    ),
                    new ActionRowBuilder().addComponents(
                        new TextInputBuilder().setCustomId('inline').setLabel('Inline? (yes / no)').setStyle(TextInputStyle.Short).setMaxLength(3).setRequired(false).setValue('no')
                    ),
                )
        );
    }

    if (action === 'embed_removefield') {
        if (s.fields.length === 0) return interaction.reply({ content: 'No fields to remove.', ephemeral: true });
        return interaction.showModal(
            new ModalBuilder()
                .setCustomId(`embed_modal_removefield:${sid}`)
                .setTitle('Remove Field')
                .addComponents(new ActionRowBuilder().addComponents(
                    new TextInputBuilder()
                        .setCustomId('number')
                        .setLabel(`Field number to remove (1–${s.fields.length})`)
                        .setStyle(TextInputStyle.Short)
                        .setMaxLength(2)
                        .setRequired(true)
                ))
        );
    }

    if (action === 'embed_preview') {
        return interaction.reply({ embeds: [buildEmbed(s, true)], ephemeral: true });
    }

    if (action === 'embed_clear') {
        const fresh = blankEmbed(s.authorId);
        sessions.set(sid, fresh);
        return interaction.update({ embeds: [buildPanelEmbed(fresh)], components: buildPanelComponents(sid, fresh) });
    }

    if (action === 'embed_post') {
        if (!s.title && !s.description && s.fields.length === 0 && !s.authorName) {
            return interaction.reply({ content: 'Add at least a title or description before posting.', ephemeral: true });
        }
        try {
            await interaction.channel.send({ embeds: [buildEmbed(s)] });
            sessions.delete(sid);
            return interaction.update({ content: '✅ Embed posted!', embeds: [], components: [] });
        } catch (err) {
            console.error('[Embed] Post error:', err.message);
            return interaction.reply({ content: 'Failed to post embed. Check my permissions in this channel.', ephemeral: true });
        }
    }
}

// ---- Modal submits ----

async function handleModalSubmit(interaction) {
    const customId = interaction.customId;
    const colonIdx = customId.indexOf(':');
    const action   = customId.slice(0, colonIdx);
    const sid      = customId.slice(colonIdx + 1);
    const s        = sessions.get(sid);
    if (!s) return interaction.reply({ content: 'Session expired. Run `/embed` again.', ephemeral: true });

    if (action === 'embed_customcolor') {
        const raw    = interaction.fields.getTextInputValue('hex').replace('#', '');
        const parsed = parseInt(raw, 16);
        if (isNaN(parsed) || raw.length < 3 || raw.length > 6) {
            return interaction.reply({ content: 'Invalid hex — example: `2ECC71`', ephemeral: true });
        }
        s.color = parsed;
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_title') {
        s.title    = interaction.fields.getTextInputValue('title').trim();
        s.titleUrl = interaction.fields.getTextInputValue('url').trim();
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_desc') {
        s.description = interaction.fields.getTextInputValue('desc').trim();
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_author') {
        s.authorName = interaction.fields.getTextInputValue('name').trim();
        s.authorIcon = interaction.fields.getTextInputValue('icon').trim();
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_footer') {
        s.footerText = interaction.fields.getTextInputValue('text').trim();
        s.footerIcon = interaction.fields.getTextInputValue('icon').trim();
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_images') {
        s.thumbnailUrl = interaction.fields.getTextInputValue('thumbnail').trim();
        s.imageUrl     = interaction.fields.getTextInputValue('image').trim();
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_addfield') {
        const name   = interaction.fields.getTextInputValue('name').trim();
        const value  = interaction.fields.getTextInputValue('value').trim();
        const inline = ['yes', 'y', 'true', '1'].includes(
            interaction.fields.getTextInputValue('inline').trim().toLowerCase()
        );
        s.fields.push({ name, value, inline });
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }

    if (action === 'embed_modal_removefield') {
        const num = parseInt(interaction.fields.getTextInputValue('number'));
        if (isNaN(num) || num < 1 || num > s.fields.length) {
            return interaction.reply({ content: `Invalid field number. Enter a number between 1 and ${s.fields.length}.`, ephemeral: true });
        }
        s.fields.splice(num - 1, 1);
        return interaction.update({ embeds: [buildPanelEmbed(s)], components: buildPanelComponents(sid, s) });
    }
}

module.exports = { data, execute, handleInteraction, handleModalSubmit };
