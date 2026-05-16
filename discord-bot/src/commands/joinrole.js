const {
    SlashCommandBuilder, PermissionFlagsBits,
    EmbedBuilder,
} = require('discord.js');
const db = require('../database');

const GREEN      = 0x2ECC71;
const DARK_GREEN = 0x27AE60;
const RED        = 0xE74C3C;
const FOOTER     = 'Mostly Vanilla • Join Role';

const data = new SlashCommandBuilder()
    .setName('joinrole')
    .setDescription('Configure the role automatically assigned when members join')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageRoles)
    .addSubcommand(s => s
        .setName('set')
        .setDescription('Set the role to assign when a member joins')
        .addRoleOption(o => o
            .setName('role')
            .setDescription('Role to assign on join')
            .setRequired(true)))
    .addSubcommand(s => s
        .setName('view')
        .setDescription('View the current join role'))
    .addSubcommand(s => s
        .setName('disable')
        .setDescription('Disable automatic join role assignment'));

async function execute(interaction) {
    const sub = interaction.options.getSubcommand();

    if (sub === 'set') {
        const role = interaction.options.getRole('role');
        db.setSetting('join_role_id', role.id);
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(GREEN)
                .setTitle('Join Role Set')
                .setDescription(`Members will be assigned ${role} when they join.`)
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'view') {
        const roleId = db.getSetting('join_role_id');
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(DARK_GREEN)
                .setTitle('Join Role')
                .addFields({ name: 'Current Role', value: roleId ? `<@&${roleId}>` : 'Not set' })
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'disable') {
        db.deleteSetting('join_role_id');
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(RED)
                .setTitle('Join Role Disabled')
                .setDescription('No role will be assigned on join. Use `/joinrole set` to re-enable.')
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }
}

module.exports = { data, execute };
