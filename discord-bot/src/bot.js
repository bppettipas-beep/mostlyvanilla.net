const {
    Client, GatewayIntentBits, Partials,
    Events, ActivityType,
    REST, Routes,
} = require('discord.js');
const db = require('./database');
const { data: ticketData, execute: ticketExecute, handleButton: ticketHandleButton, handleSelect: ticketHandleSelect, handleModal: ticketHandleModal, ticketaddData, ticketaddExecute, ticketrenameData, ticketrenameExecute } = require('./commands/ticket');
const { data: staffappData, execute: staffappExecute, handleButton: staffappHandleButton, handleSelect: staffappHandleSelect, handleModal: staffappHandleModal, handleDm: staffappHandleDm } = require('./commands/staffapp');
const { data: welcomeData, execute: welcomeExecute, sendWelcomeMessage } = require('./commands/welcome');
const { data: joinRoleData, execute: joinRoleExecute } = require('./commands/joinrole');
const { data: embedData, execute: embedExecute, handleInteraction: embedHandleInteraction, handleModalSubmit: embedHandleModalSubmit } = require('./commands/embed');
const { data: invitesData, execute: invitesExecute, startLiveBoard } = require('./commands/invites');
const { data: chatcountData, execute: chatcountExecute, startChatBoard, incrementChat } = require('./commands/chatcount');
const { data: memberembedData, execute: memberembedExecute, startMemberEmbed } = require('./commands/memberembed');
const {
    banData, banExecute,
    unbanData, unbanExecute,
    kickData, kickExecute,
    muteData, muteExecute,
    unmuteData, unmuteExecute,
    warnData, warnExecute,
    warningsData, warningsExecute,
    clearwarningsData, clearwarningsExecute,
    modhistoryData, modhistoryExecute,
    purgeData, purgeExecute,
    slowmodeData, slowmodeExecute,
    lockData, lockExecute,
    unlockData, unlockExecute,
    modlogData, modlogExecute,
} = require('./commands/mod');

const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMembers,
        GatewayIntentBits.GuildPresences,
        GatewayIntentBits.GuildMessages,
        GatewayIntentBits.DirectMessages,
    ],
    partials: [Partials.Channel],
});

client.once(Events.ClientReady, async (c) => {
    console.log(`[Bot] Ready as ${c.user.tag}`);
    c.user.setActivity('MostlyVanilla Beacon', { type: ActivityType.Watching });

    try {
        const rest = new REST().setToken(process.env.DISCORD_TOKEN);
        await rest.put(
            Routes.applicationGuildCommands(c.user.id, process.env.GUILD_ID),
            {
                body: [
                    welcomeData.toJSON(),
                    joinRoleData.toJSON(),
                    embedData.toJSON(),
                    invitesData.toJSON(),
                    chatcountData.toJSON(),
                    ticketData.toJSON(),
                    ticketaddData.toJSON(),
                    ticketrenameData.toJSON(),
                    staffappData.toJSON(),
                    banData.toJSON(),
                    unbanData.toJSON(),
                    kickData.toJSON(),
                    muteData.toJSON(),
                    unmuteData.toJSON(),
                    warnData.toJSON(),
                    warningsData.toJSON(),
                    clearwarningsData.toJSON(),
                    modhistoryData.toJSON(),
                    purgeData.toJSON(),
                    slowmodeData.toJSON(),
                    lockData.toJSON(),
                    unlockData.toJSON(),
                    modlogData.toJSON(),
                    memberembedData.toJSON(),
                ],
            }
        );
        console.log('[Bot] Slash commands registered');
    } catch (err) {
        console.error('[Bot] Failed to register commands:', err.message);
    }

    startLiveBoard(c);
    startChatBoard(c);
    startMemberEmbed(c);
});

client.on(Events.GuildMemberAdd, async (member) => {
    await grantJoinRole(member);
    await sendWelcomeMessage(member, null);
});

client.on(Events.MessageCreate, async (message) => {
    if (message.author.bot) return;

    if (message.guild) {
        incrementChat(message.guild.id, message.author.id);
        return;
    }

    // DM — handle staff application flow
    const appHandled = await staffappHandleDm(message).catch(err => { console.error('[Bot] StaffApp DM error:', err.message); return false; });
    if (appHandled) return;
});

client.on(Events.InteractionCreate, async (interaction) => {
    if (interaction.isChatInputCommand()) {
        if (interaction.commandName === 'welcome')    await welcomeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'joinrole')   await joinRoleExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'embed')      await embedExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'ticket')        await ticketExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'ticketadd')     await ticketaddExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'ticketrename')  await ticketrenameExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'staffapp')   await staffappExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'invites')       await invitesExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'chatcount')     await chatcountExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'ban')           await banExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'unban')         await unbanExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'kick')          await kickExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'mute')          await muteExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'unmute')        await unmuteExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'warn')          await warnExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'warnings')      await warningsExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'clearwarnings') await clearwarningsExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'modhistory')    await modhistoryExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'purge')         await purgeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'slowmode')      await slowmodeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'lock')          await lockExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'unlock')        await unlockExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'modlog')        await modlogExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'memberembed')   await memberembedExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        return;
    }

    if (interaction.isButton()) {
        if (interaction.customId.startsWith('embed_'))    await embedHandleInteraction(interaction).catch(err => console.error('[Bot] Embed error:', err.message));
        else if (interaction.customId.startsWith('ticket_'))   await ticketHandleButton(interaction).catch(err => console.error('[Bot] Ticket button error:', err.message));
        else if (interaction.customId.startsWith('staffapp_')) await staffappHandleButton(interaction).catch(err => console.error('[Bot] StaffApp button error:', err.message));
        return;
    }

    if (interaction.isStringSelectMenu()) {
        if (interaction.customId.startsWith('embed_'))         await embedHandleInteraction(interaction).catch(err => console.error('[Bot] Embed error:', err.message));
        else if (interaction.customId.startsWith('ticket_panel_'))  await ticketHandleSelect(interaction).catch(err => console.error('[Bot] Ticket select error:', err.message));
        else if (interaction.customId.startsWith('staffapp_'))      await staffappHandleSelect(interaction).catch(err => console.error('[Bot] StaffApp select error:', err.message));
        return;
    }

    if (interaction.isChannelSelectMenu() || interaction.isRoleSelectMenu()) {
        if (interaction.customId.startsWith('ticket_panel_'))  await ticketHandleSelect(interaction).catch(err => console.error('[Bot] Ticket select error:', err.message));
        else if (interaction.customId.startsWith('staffapp_')) await staffappHandleSelect(interaction).catch(err => console.error('[Bot] StaffApp select error:', err.message));
        return;
    }

    if (interaction.isModalSubmit()) {
        if (interaction.customId.startsWith('embed_'))         await embedHandleModalSubmit(interaction).catch(err => console.error('[Bot] Embed modal error:', err.message));
        else if (interaction.customId.startsWith('ticket_'))   await ticketHandleModal(interaction).catch(err => console.error('[Bot] Ticket modal error:', err.message));
        else if (interaction.customId.startsWith('staffapp_')) await staffappHandleModal(interaction).catch(err => console.error('[Bot] StaffApp modal error:', err.message));
    }
});

// ── Helpers ───────────────────────────────────────────────────────────────────

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

module.exports = {
    client,
    startBot: (token) => client.login(token),
};
