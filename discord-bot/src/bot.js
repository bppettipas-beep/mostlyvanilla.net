const {
    Client, GatewayIntentBits, Partials,
    EmbedBuilder, Events, ActivityType,
} = require('discord.js');
const db = require('./database');

const COLOR_GREEN      = 0x2ECC71;
const COLOR_DARK_GREEN = 0x27AE60;
const COLOR_RED        = 0xE74C3C;
const FOOTER           = 'Mostly Vanilla • Verification';

// Tracks pending kick timers for unverified members: userId → timeoutId
const pendingKicks = new Map();

const client = new Client({
    intents: [
        GatewayIntentBits.Guilds,
        GatewayIntentBits.GuildMembers,
        GatewayIntentBits.DirectMessages,
    ],
    partials: [Partials.Channel],
});

client.once(Events.ClientReady, (c) => {
    console.log(`[Bot] Ready as ${c.user.tag}`);
    c.user.setActivity('Mostly Vanilla | /discord', { type: ActivityType.Watching });
});

client.on(Events.GuildMemberAdd, async (member) => {
    // Already verified — grant role and skip the gate
    const existing = db.getByDiscord(member.id);
    if (existing) {
        await grantVerifiedRole(member, existing.mc_name);
        return;
    }

    const kickMinutes = parseInt(process.env.KICK_TIMEOUT_MINUTES) || 15;

    const instructions =
        `**To get access you must link your Minecraft account:**\n` +
        `> 1. Join the **Mostly Vanilla** Minecraft server\n` +
        `> 2. Run \`/discord\` in-game to receive a 6-character code\n` +
        `> 3. DM that code to me\n\n` +
        `You have **${kickMinutes} minutes** before you will be removed from the server.`;

    // Post in the #verify channel so they see it even if DMs are closed
    await postInVerifyChannel(member, instructions, kickMinutes);

    // Also DM them in case they miss the channel message
    try {
        await member.send({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_GREEN)
                .setTitle('Welcome to Mostly Vanilla!')
                .setThumbnail(member.guild.iconURL({ dynamic: true }))
                .setDescription(instructions)
                .setFooter({ text: FOOTER })
                .setTimestamp(),
        ]});
    } catch {
        // DMs disabled — the #verify channel message is the fallback
    }

    // Schedule a kick if they don't verify in time
    scheduleKick(member, kickMinutes);
});

// Handle DMs — the only input we expect is the 6-char verification code
client.on(Events.MessageCreate, async (message) => {
    if (message.author.bot || message.guild) return;

    const code = message.content.trim().toUpperCase().replace(/\s+/g, '');

    // Already linked
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

    // Basic format check
    if (!/^[A-Z0-9]{6}$/.test(code)) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_RED)
                .setTitle('Invalid Code')
                .setDescription(
                    'Codes are exactly **6 characters** (letters and numbers).\n\n' +
                    'Run `/discord` on the Mostly Vanilla Minecraft server to get your code.'
                )
                .setFooter({ text: FOOTER }),
        ]});
    }

    // Look up code
    const pending = db.getCode(code);
    if (!pending) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_RED)
                .setTitle('Code Not Found or Expired')
                .setDescription(
                    'That code is invalid, already used, or has expired.\n\n' +
                    'Run `/discord` in Minecraft again to get a fresh code.'
                )
                .setFooter({ text: FOOTER }),
        ]});
    }

    // Minecraft account already linked to a different Discord
    if (db.getByMinecraft(pending.mc_uuid)) {
        return message.reply({ embeds: [
            new EmbedBuilder()
                .setColor(COLOR_RED)
                .setTitle('Minecraft Account Already Linked')
                .setDescription(`**${pending.mc_name}** is already linked to a different Discord account.`)
                .setFooter({ text: FOOTER }),
        ]});
    }

    // Save verification and remove used code
    db.insertVerified(message.author.id, pending.mc_uuid, pending.mc_name);
    db.deleteCode(code);
    console.log(`[Bot] Verified ${message.author.tag} as ${pending.mc_name}`);

    // Cancel the pending kick now that they've verified
    cancelKick(message.author.id);

    // Grant role if they're in the guild
    const guild = client.guilds.cache.get(process.env.GUILD_ID);
    if (guild) {
        try {
            const member = await guild.members.fetch(message.author.id).catch(() => null);
            if (member) await grantVerifiedRole(member, pending.mc_name);
        } catch (err) {
            console.error('[Bot] Failed to grant role:', err.message);
        }
    }

    message.reply({ embeds: [
        new EmbedBuilder()
            .setColor(COLOR_GREEN)
            .setTitle('Verification Complete!')
            .setDescription(
                `You've been verified as **${pending.mc_name}**.\n` +
                `You now have full access to the **Mostly Vanilla** Discord!`
            )
            .addFields(
                { name: 'Minecraft IGN', value: pending.mc_name, inline: true },
                { name: 'Discord',       value: message.author.username, inline: true },
            )
            .setFooter({ text: FOOTER })
            .setTimestamp(),
    ]});
});

// --- Helpers ---

async function postInVerifyChannel(member, instructions, kickMinutes) {
    const channelId = process.env.VERIFY_CHANNEL_ID;
    if (!channelId) return;

    const channel = member.guild.channels.cache.get(channelId);
    if (!channel) {
        console.warn('[Bot] VERIFY_CHANNEL_ID not found in guild');
        return;
    }

    try {
        await channel.send({
            content: `<@${member.id}>`,
            embeds: [
                new EmbedBuilder()
                    .setColor(COLOR_GREEN)
                    .setTitle('Verification Required')
                    .setDescription(instructions)
                    .setFooter({ text: FOOTER })
                    .setTimestamp(),
            ],
        });
    } catch (err) {
        console.error('[Bot] Failed to post in verify channel:', err.message);
    }
}

function scheduleKick(member, kickMinutes) {
    // Cancel any pre-existing timer for this user (e.g. rejoined)
    cancelKick(member.id);

    const timeout = setTimeout(async () => {
        pendingKicks.delete(member.id);

        // Skip if they verified in time
        if (db.getByDiscord(member.id)) return;

        try {
            const freshMember = await member.guild.members.fetch(member.id).catch(() => null);
            if (!freshMember) return; // Already left on their own

            await freshMember.kick('Did not verify Minecraft account within the time limit');
            console.log(`[Bot] Kicked ${member.user.tag} — verification timeout`);

            // Let them know why they were removed (best-effort DM)
            try {
                await member.send({ embeds: [
                    new EmbedBuilder()
                        .setColor(COLOR_RED)
                        .setTitle('Removed from Mostly Vanilla')
                        .setDescription(
                            `You were removed for not verifying your Minecraft account within **${kickMinutes} minutes**.\n\n` +
                            `You're welcome to rejoin — just make sure to run \`/discord\` in-game first to get your code ready.`
                        )
                        .setFooter({ text: FOOTER }),
                ]});
            } catch {
                // DMs still closed — nothing to do
            }
        } catch (err) {
            console.error('[Bot] Failed to kick unverified member:', err.message);
        }
    }, kickMinutes * 60_000);

    pendingKicks.set(member.id, timeout);
}

function cancelKick(userId) {
    if (pendingKicks.has(userId)) {
        clearTimeout(pendingKicks.get(userId));
        pendingKicks.delete(userId);
    }
}

async function grantVerifiedRole(member, mcName) {
    const role = member.guild.roles.cache.get(process.env.VERIFIED_ROLE_ID);
    if (!role) {
        console.warn('[Bot] VERIFIED_ROLE_ID not found in guild');
        return;
    }
    if (member.roles.cache.has(role.id)) return;
    await member.roles.add(role);
    console.log(`[Bot] Granted Verified role to ${member.user.tag} (${mcName})`);
}

module.exports = {
    client,
    startBot: (token) => client.login(token),
};
