const {
    Client, GatewayIntentBits, Partials,
    EmbedBuilder, Events, ActivityType,
    REST, Routes,
} = require('discord.js');
const { data: ticketData, execute: ticketExecute, handleButton: ticketHandleButton, handleSelect: ticketHandleSelect, handleModal: ticketHandleModal } = require('./commands/ticket');
const db = require('./database');
const { data: welcomeData, execute: welcomeExecute, sendWelcomeMessage } = require('./commands/welcome');
const { data: joinRoleData, execute: joinRoleExecute } = require('./commands/joinrole');
const { data: embedData, execute: embedExecute, handleInteraction: embedHandleInteraction, handleModalSubmit: embedHandleModalSubmit } = require('./commands/embed');

const COLOR_GREEN      = 0x2ECC71;
const COLOR_DARK_GREEN = 0x27AE60;
const COLOR_RED        = 0xE74C3C;
const FOOTER           = 'Mostly Vanilla • Verification';

const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMembers,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.DirectMessages,
    ],
    partials: [Partials.Channel],
});

client.once(Events.ClientReady, async (c) => {
    console.log(`[Bot] Ready as ${c.user.tag}`);
    c.user.setActivity('Mostly Vanilla | /link', { type: ActivityType.Watching });

    try {
        const rest = new REST().setToken(process.env.DISCORD_TOKEN);
        await rest.put(
            Routes.applicationGuildCommands(c.user.id, process.env.GUILD_ID),
            { body: [welcomeData.toJSON(), joinRoleData.toJSON(), embedData.toJSON(), ticketData.toJSON()] }
        );
        console.log('[Bot] Slash commands registered');
    } catch (err) {
        console.error('[Bot] Failed to register commands:', err.message);
    }
});

client.on(Events.InteractionCreate, async (interaction) => {
    if (interaction.isChatInputCommand()) {
        if (interaction.commandName === 'welcome') {
            await welcomeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        }
        if (interaction.commandName === 'joinrole') {
            await joinRoleExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        }
        if (interaction.commandName === 'embed') {
            await embedExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        }
        if (interaction.commandName === 'ticket') {
            await ticketExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        }
        return;
    }

    if (interaction.isButton()) {
        if (interaction.customId.startsWith('embed_')) {
            await embedHandleInteraction(interaction).catch(err => console.error('[Bot] Embed interaction error:', err.message));
        } else if (interaction.customId.startsWith('ticket_')) {
            await ticketHandleButton(interaction).catch(err => console.error('[Bot] Ticket button error:', err.message));
        }
        return;
    }

    if (interaction.isStringSelectMenu()) {
        if (interaction.customId.startsWith('embed_')) {
            await embedHandleInteraction(interaction).catch(err => console.error('[Bot] Embed interaction error:', err.message));
        } else if (interaction.customId.startsWith('ticket_panel_')) {
            await ticketHandleSelect(interaction).catch(err => console.error('[Bot] Ticket select error:', err.message));
        }
        return;
    }

    if (interaction.isChannelSelectMenu() || interaction.isRoleSelectMenu()) {
        if (interaction.customId.startsWith('ticket_panel_')) {
            await ticketHandleSelect(interaction).catch(err => console.error('[Bot] Ticket select error:', err.message));
        }
        return;
    }

    if (interaction.isModalSubmit()) {
        if (interaction.customId.startsWith('embed_')) {
            await embedHandleModalSubmit(interaction).catch(err => console.error('[Bot] Embed modal error:', err.message));
        } else if (interaction.customId.startsWith('ticket_')) {
            await ticketHandleModal(interaction).catch(err => console.error('[Bot] Ticket modal error:', err.message));
        }
    }
});

client.on(Events.GuildMemberAdd, async (member) => {
    // Assign join role immediately
    await grantJoinRole(member);

    // Send welcome message right away
    const existing = db.getByDiscord(member.id);
    await sendWelcomeMessage(member, existing?.mc_name ?? null);

    // If already verified, also grant the verified role
    if (existing) {
        await grantVerifiedRole(member, existing.mc_name);
        return;
    }

    // DM optional MC linking instructions (no kick threat)
    try {
        await member.send({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_GREEN)
                .setTitle('Welcome to Mostly Vanilla!')
                .setThumbnail(member.guild.iconURL({ dynamic: true }))
                .setDescription(
                    `**Optionally link your Minecraft account:**\n` +
                    `> 1. Join the **Mostly Vanilla** Minecraft server\n` +
                    `> 2. Run \`/link\` in-game to get a 6-character code\n` +
                    `> 3. DM that code to me\n\n` +
                    `Linking is optional but gives you the verified role!`
                )
                .setFooter({ text: FOOTER })
                .setTimestamp(),
        ]});
    } catch {
        // DMs disabled
    }
});

client.on(Events.MessageCreate, async (message) => {
    if (message.author.bot || message.guild) return;

    const code = message.content.trim().toUpperCase().replace(/\s+/g, '');

    const alreadyVerified = db.getByDiscord(message.author.id);
    if (alreadyVerified) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_DARK_GREEN)
                .setTitle('Already Verified')
                .setDescription(`Your Discord is already linked to **${alreadyVerified.mc_name}** on Minecraft.`)
                .setFooter({ text: FOOTER }),
        ]});
    }

    if (!/^[A-Z0-9]{6}$/.test(code)) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_RED)
                .setTitle('Invalid Code')
                .setDescription(
                    'Codes are exactly **6 characters** (letters and numbers).\n\n' +
                    'Run `/link` on the Mostly Vanilla Minecraft server to get your code.'
                )
                .setFooter({ text: FOOTER }),
        ]});
    }

    const pending = db.getCode(code);
    if (!pending) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_RED)
                .setTitle('Code Not Found or Expired')
                .setDescription(
                    'That code is invalid, already used, or has expired.\n\n' +
                    'Run `/link` in Minecraft again to get a fresh code.'
                )
                .setFooter({ text: FOOTER }),
        ]});
    }

    if (db.getByMinecraft(pending.mc_uuid)) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_RED)
                .setTitle('Minecraft Account Already Linked')
                .setDescription(`**${pending.mc_name}** is already linked to a different Discord account.`)
                .setFooter({ text: FOOTER }),
        ]});
    }

    db.insertVerified(message.author.id, pending.mc_uuid, pending.mc_name);
    db.deleteCode(code);
    console.log(`[Bot] Verified ${message.author.tag} as ${pending.mc_name}`);

    const guild = client.guilds.cache.get(process.env.GUILD_ID);
    if (guild) {
        try {
            const member = await guild.members.fetch(message.author.id).catch(() => null);
            if (member) await grantVerifiedRole(member, pending.mc_name);
        } catch (err) {
            console.error('[Bot] Post-verify error:', err.message);
        }
    }

    message.reply({ embeds: [
        new EmbedBuilder()
            .setColor(COLOR_GREEN)
            .setTitle('Verification Complete!')
            .setDescription(
                `You've been verified as **${pending.mc_name}**.\n` +
                `You now have the verified role on **Mostly Vanilla** Discord!`
            )
            .addFields(
                { name: 'Minecraft IGN', value: pending.mc_name,        inline: true },
                { name: 'Discord',       value: message.author.username, inline: true },
            )
            .setFooter({ text: FOOTER })
            .setTimestamp(),
    ]});
});

// --- Helpers ---

async function grantJoinRole(member) {
    const roleId = db.getSetting('join_role_id');
    if (!roleId) return;
    const role = member.guild.roles.cache.get(roleId);
    if (!role) { console.warn('[Bot] join_role_id not found in guild'); return; }
    if (member.roles.cache.has(role.id)) return;
    try {
        await member.roles.add(role);
        console.log(`[Bot] Granted join role to ${member.user.tag}`);
    } catch (err) {
        console.error('[Bot] Failed to grant join role:', err.message);
    }
}

async function grantVerifiedRole(member, mcName) {
    const role = member.guild.roles.cache.get(process.env.VERIFIED_ROLE_ID);
    if (!role) { console.warn('[Bot] VERIFIED_ROLE_ID not found'); return; }
    if (member.roles.cache.has(role.id)) return;
    await member.roles.add(role);
    console.log(`[Bot] Granted Verified role to ${member.user.tag} (${mcName})`);
}

module.exports = {
    client,
    startBot: (token) => client.login(token),
};
