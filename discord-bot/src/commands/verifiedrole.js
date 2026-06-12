const {
    SlashCommandBuilder, PermissionFlagsBits,
    EmbedBuilder,
} = require('discord.js');
const db = require('../database');

const GREEN  = 0x2ECC71;
const RED    = 0xE74C3C;
const FOOTER = 'MostlyVanilla Beacon • Verified Role';

const data = new SlashCommandBuilder()
    .setName('verifiedrole')
    .setDescription('Configure the role granted when a player links their Minecraft account')
    .setDefaultMemberPermissions(PermissionFlagsBits.ManageRoles)
    .addSubcommand(s => s
        .setName('set')
        .setDescription('Set the role to grant on Discord verification')
        .addRoleOption(o => o
            .setName('role')
            .setDescription('Role to grant when a player links via /link')
            .setRequired(true)))
    .addSubcommand(s => s
        .setName('view')
        .setDescription('View the current verified role'))
    .addSubcommand(s => s
        .setName('disable')
        .setDescription('Stop granting a role on verification'));

async function execute(interaction) {
    const sub = interaction.options.getSubcommand();

    if (sub === 'set') {
        const role = interaction.options.getRole('role');
        db.setSetting('verified_role_id', role.id);
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(GREEN)
                .setTitle('Verified Role Set')
                .setDescription(`Players who link via \`/link\` will receive ${role}.`)
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'view') {
        const roleId = db.getSetting('verified_role_id');
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(GREEN)
                .setTitle('Verified Role')
                .addFields({ name: 'Current Role', value: roleId ? `<@&${roleId}>` : 'Not set' })
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }

    if (sub === 'disable') {
        db.deleteSetting('verified_role_id');
        return interaction.reply({ embeds: [
            new EmbedBuilder()
                .setColor(RED)
                .setTitle('Verified Role Disabled')
                .setDescription('No role will be granted on verification. Use `/verifiedrole set` to re-enable.')
                .setFooter({ text: FOOTER }),
        ], ephemeral: true });
    }
}

module.exports = { data, execute };
