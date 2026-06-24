const {
    Client, GatewayIntentBits, Partials,
    Events, ActivityType,
    REST, Routes,
} = require('discord.js');
const db = require('./database');
const { data: ticketData, execute: ticketExecute, handleButton: ticketHandleButton, handleSelect: ticketHandleSelect, handleModal: ticketHandleModal, ticketaddData, ticketaddExecute } = require('./commands/ticket');
const { data: staffappData, execute: staffappExecute, handleButton: staffappHandleButton, handleSelect: staffappHandleSelect, handleModal: staffappHandleModal, handleDm: staffappHandleDm } = require('./commands/staffapp');
const { data: welcomeData, execute: welcomeExecute, sendWelcomeMessage } = require('./commands/welcome');
const { data: joinRoleData, execute: joinRoleExecute } = require('./commands/joinrole');
const { data: embedData, execute: embedExecute, handleInteraction: embedHandleInteraction, handleModalSubmit: embedHandleModalSubmit } = require('./commands/embed');
const { data: invitesData, execute: invitesExecute, startLiveBoard, invitewipeData, invitewipeExecute, inviterestoreData, inviterestoreExecute } = require('./commands/invites');
const { data: chatcountData, execute: chatcountExecute, startChatBoard, incrementChat } = require('./commands/chatcount');
const { data: memberembedData, execute: memberembedExecute, startMemberEmbed } = require('./commands/memberembed');
const { data: strikeData, execute: strikeExecute, startStrikeBoard, startStrikeWipe, strikewipeData, strikewipeExecute, strikemaxData, strikemaxExecute } = require('./commands/strike');
const { data: gamesData, execute: gamesExecute, handleInteraction: gamesHandleInteraction } = require('./commands/games');
const { data: chatlogData, execute: chatlogExecute } = require('./commands/chatlog');
const { data: verifiedRoleData, execute: verifiedRoleExecute } = require('./commands/verifiedrole');
const { data: rankData, execute: rankExecute, handleButton: rankHandleButton, handleModal: rankHandleModal, startRankChecker } = require('./commands/rank');
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
    partials: [Partials.Channel, Partials.Message],
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
                    invitewipeData.toJSON(),
                    inviterestoreData.toJSON(),
                    chatcountData.toJSON(),
                    ticketData.toJSON(),
                    ticketaddData.toJSON(),
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
                    strikeData.toJSON(),
                    strikewipeData.toJSON(),
                    strikemaxData.toJSON(),
                    gamesData.toJSON(),
                    chatlogData.toJSON(),
                    verifiedRoleData.toJSON(),
                    rankData.toJSON(),
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
    startStrikeBoard(c);
    startStrikeWipe(c);
    startRankChecker(c);
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

    // DM — handle staff application flow first
    const appHandled = await staffappHandleDm(message).catch(err => { console.error('[Bot] StaffApp DM error:', err.message); return false; });
    if (appHandled) return;

    // DM — check if it's a verification code (6 chars from our code alphabet)
    const content = message.content.trim().toUpperCase();
    console.log(`[Bot] DM from ${message.author.tag}: "${content}" (length ${content.length})`);
    if (/^[A-HJ-NP-Z2-9]{6}$/.test(content)) {
        try {
            db.codes.cleanupExpired();
            const row = db.codes.get(content);
            console.log(`[Bot] Code lookup for "${content}": ${row ? 'found' : 'not found'}`);
            if (!row) {
                await message.reply('That code is invalid or has already been used. Run `/link` in-game to get a new one.');
                return;
            }
            if (row.expires_at < Math.floor(Date.now() / 1000)) {
                db.codes.delete(content);
                await message.reply('That code has expired. Run `/link` in-game to get a new one.');
                return;
            }
            const alreadyLinked = db.verified.getByDiscordId(message.author.id);
            if (alreadyLinked) {
                await message.reply(`Your Discord is already linked to **${alreadyLinked.mc_name}**. Contact an admin if you need to change this.`);
                return;
            }
            db.verified.link(row.mc_uuid, message.author.id, row.mc_name);
            db.codes.delete(content);
            console.log(`[Bot] Linked ${row.mc_name} (${row.mc_uuid}) to Discord ${message.author.tag}`);

            // Grant verified role + sync any existing Discord roles to game roles
            try {
                const guild = client.guilds.cache.get(process.env.GUILD_ID);
                const member = await guild?.members.fetch(message.author.id).catch(() => null);
                if (member) {
                    const verifiedRoleId = db.getSetting('verified_role_id');
                    if (verifiedRoleId) {
                        const role = guild.roles.cache.get(verifiedRoleId);
                        if (role) await member.roles.add(role);
                    }

                    // Sync any role links the member already has in Discord
                    const allLinks = db.roleLinks.getAll();
                    for (const { game_role, discord_role_id } of allLinks) {
                        if (member.roles.cache.has(discord_role_id)) {
                            db.pendingGameRoles.enqueue(row.mc_uuid, game_role, true);
                            console.log(`[Bot] Link-time sync: queued assign ${game_role} for ${row.mc_name}`);
                        }
                    }
                }
            } catch (err) {
                console.error('[Bot] Failed to grant verified role:', err.message);
            }

            await message.reply(`✅ Linked! Your Minecraft account **${row.mc_name}** is now connected to your Discord.`);
        } catch (err) {
            console.error('[Bot] DM verification error:', err.message, err.stack);
            await message.reply('Something went wrong. Please try again or contact an admin.').catch(() => {});
        }
        return;
    }
    console.log(`[Bot] DM did not match code regex — ignoring`);
});

client.on(Events.InteractionCreate, async (interaction) => {
    if (interaction.isChatInputCommand()) {
        if (interaction.commandName === 'welcome')    await welcomeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'joinrole')   await joinRoleExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'embed')      await embedExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'ticket')        await ticketExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'ticketadd')     await ticketaddExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'staffapp')   await staffappExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'invites')        await invitesExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'invitewipe')     await invitewipeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'inviterestore')  await inviterestoreExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
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
        if (interaction.commandName === 'strike')        await strikeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'strikewipe')    await strikewipeExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'strikemax')     await strikemaxExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'games')         await gamesExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'chatlog')        await chatlogExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'verifiedrole')  await verifiedRoleExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        if (interaction.commandName === 'rank')           await rankExecute(interaction).catch(err => console.error('[Bot] Command error:', err.message));
        return;
    }

    if (interaction.isButton()) {
        if (interaction.customId.startsWith('embed_'))         await embedHandleInteraction(interaction).catch(err => console.error('[Bot] Embed error:', err.message));
        else if (interaction.customId.startsWith('ticket_'))   await ticketHandleButton(interaction).catch(err => console.error('[Bot] Ticket button error:', err.message));
        else if (interaction.customId.startsWith('staffapp_')) await staffappHandleButton(interaction).catch(err => console.error('[Bot] StaffApp button error:', err.message));
        else if (interaction.customId.startsWith('game_'))     await gamesHandleInteraction(interaction).catch(err => console.error('[Bot] Games error:', err.message));
        else if (interaction.customId.startsWith('rank_'))     await rankHandleButton(interaction).catch(err => console.error('[Bot] Rank button error:', err.message));
        return;
    }

    if (interaction.isStringSelectMenu()) {
        if (interaction.customId.startsWith('embed_'))              await embedHandleInteraction(interaction).catch(err => console.error('[Bot] Embed error:', err.message));
        else if (interaction.customId.startsWith('ticket_panel_'))  await ticketHandleSelect(interaction).catch(err => console.error('[Bot] Ticket select error:', err.message));
        else if (interaction.customId.startsWith('staffapp_'))      await staffappHandleSelect(interaction).catch(err => console.error('[Bot] StaffApp select error:', err.message));
        else if (interaction.customId.startsWith('game_'))          await gamesHandleInteraction(interaction).catch(err => console.error('[Bot] Games error:', err.message));
        return;
    }

    if (interaction.isChannelSelectMenu() || interaction.isRoleSelectMenu()) {
        if (interaction.customId.startsWith('ticket_panel_'))  await ticketHandleSelect(interaction).catch(err => console.error('[Bot] Ticket select error:', err.message));
        else if (interaction.customId.startsWith('staffapp_')) await staffappHandleSelect(interaction).catch(err => console.error('[Bot] StaffApp select error:', err.message));
        return;
    }

    if (interaction.isModalSubmit()) {
        if (interaction.customId.startsWith('embed_'))              await embedHandleModalSubmit(interaction).catch(err => console.error('[Bot] Embed modal error:', err.message));
        else if (interaction.customId.startsWith('ticket_'))        await ticketHandleModal(interaction).catch(err => console.error('[Bot] Ticket modal error:', err.message));
        else if (interaction.customId.startsWith('staffapp_'))      await staffappHandleModal(interaction).catch(err => console.error('[Bot] StaffApp modal error:', err.message));
        else if (interaction.customId.startsWith('rank_extend_modal:')) await rankHandleModal(interaction).catch(err => console.error('[Bot] Rank modal error:', err.message));
    }
});

// ── Discord→MC role sync ──────────────────────────────────────────────────────
client.on(Events.GuildMemberUpdate, async (oldMember, newMember) => {
    const allLinks = db.roleLinks.getAll();
    if (allLinks.length === 0) return;

    const verifiedRow = db.verified.getByDiscordId(newMember.id);
    if (!verifiedRow) return;

    for (const { game_role, discord_role_id } of allLinks) {
        const hadRole = oldMember.roles.cache.has(discord_role_id);
        const hasRole = newMember.roles.cache.has(discord_role_id);
        if (hadRole === hasRole) continue;
        db.pendingGameRoles.enqueue(verifiedRow.mc_uuid, game_role, hasRole);
        console.log(`[Bot] Discord→MC queued: ${hasRole ? 'assign' : 'remove'} ${game_role} for ${verifiedRow.mc_name}`);
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
