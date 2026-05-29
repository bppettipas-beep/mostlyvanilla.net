const {
    SlashCommandBuilder, EmbedBuilder,
    ActionRowBuilder, ButtonBuilder, ButtonStyle,
    StringSelectMenuBuilder,
} = require('discord.js');

// ── In-memory state ───────────────────────────────────────────────────────────
const games        = new Map(); // gameId  -> game state
const sessionStats = new Map(); // userId  -> lifetime session stats
let nextId = 1;

function getStats(userId) {
    if (!sessionStats.has(userId)) {
        sessionStats.set(userId, {
            ttt:    { wins: 0, losses: 0, draws: 0 },
            rps:    { wins: 0, losses: 0, draws: 0 },
            hl:     { gamesPlayed: 0, bestStreak: 0 },
            trivia: { correct: 0, total: 0 },
        });
    }
    return sessionStats.get(userId);
}

function cleanupUserGames(userId) {
    for (const [id, state] of games.entries()) {
        if (state.userId === userId) games.delete(id);
    }
}

// ── Slash command ─────────────────────────────────────────────────────────────
const data = new SlashCommandBuilder()
    .setName('games')
    .setDescription('Play fun mini-games!');

async function execute(interaction) {
    await showMenu(interaction);
}

// ── Shared button rows ────────────────────────────────────────────────────────
function closeRow(userId) {
    return new ActionRowBuilder().addComponents(
        new ButtonBuilder()
            .setCustomId(`game_close_${userId}`)
            .setLabel('Close')
            .setStyle(ButtonStyle.Danger)
            .setEmoji('✖️'),
    );
}

function endRow(gameType, userId) {
    return new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(`game_again_${gameType}_${userId}`).setLabel('Play Again').setStyle(ButtonStyle.Primary).setEmoji('🔄'),
        new ButtonBuilder().setCustomId(`game_back_${userId}`).setLabel('Back to Menu').setStyle(ButtonStyle.Secondary).setEmoji('🎮'),
        new ButtonBuilder().setCustomId(`game_close_${userId}`).setLabel('Close').setStyle(ButtonStyle.Danger).setEmoji('✖️'),
    );
}

function replyOrUpdate(interaction, opts) {
    if (interaction.isChatInputCommand()) return interaction.reply(opts);
    return interaction.update(opts);
}

// ── Close handler ─────────────────────────────────────────────────────────────
function buildCloseEmbed(userId) {
    const stats  = getStats(userId);
    const fields = [];

    const tttTotal = stats.ttt.wins + stats.ttt.losses + stats.ttt.draws;
    if (tttTotal > 0) {
        fields.push({
            name:   '❌ Tic-Tac-Toe',
            value:  `${stats.ttt.wins}W · ${stats.ttt.losses}L · ${stats.ttt.draws}D (${tttTotal} game${tttTotal !== 1 ? 's' : ''})`,
            inline: true,
        });
    }

    const rpsTotal = stats.rps.wins + stats.rps.losses + stats.rps.draws;
    if (rpsTotal > 0) {
        fields.push({
            name:   '✊ Rock Paper Scissors',
            value:  `${stats.rps.wins}W · ${stats.rps.losses}L · ${stats.rps.draws}D (${rpsTotal} match${rpsTotal !== 1 ? 'es' : ''})`,
            inline: true,
        });
    }

    if (stats.hl.gamesPlayed > 0) {
        fields.push({
            name:   '🃏 Higher or Lower',
            value:  `Best streak: **${stats.hl.bestStreak}** · ${stats.hl.gamesPlayed} game${stats.hl.gamesPlayed !== 1 ? 's' : ''}`,
            inline: true,
        });
    }

    if (stats.trivia.total > 0) {
        const pct = Math.round((stats.trivia.correct / stats.trivia.total) * 100);
        fields.push({
            name:   '❓ Trivia',
            value:  `${stats.trivia.correct} / ${stats.trivia.total} correct (${pct}%)`,
            inline: true,
        });
    }

    const embed = new EmbedBuilder()
        .setColor(0x2B2D31)
        .setTitle('🎮 Game Closed')
        .setFooter({ text: 'MostlyVanilla Beacon • Games' })
        .setTimestamp();

    if (fields.length === 0) {
        embed.setDescription('*No games completed this session.*');
    } else {
        embed.setDescription('Thanks for playing! Here\'s your session summary:').addFields(fields);
    }

    return embed;
}

async function handleClose(interaction) {
    const userId = interaction.customId.replace('game_close_', '');
    if (interaction.user.id !== userId)
        return interaction.reply({ content: 'This is not your game.', ephemeral: true });

    cleanupUserGames(userId);
    await interaction.update({ embeds: [buildCloseEmbed(userId)], components: [] });
}

// ── Game menu ─────────────────────────────────────────────────────────────────
async function showMenu(interaction) {
    const embed = new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('🎮 MostlyVanilla Games')
        .setDescription('Pick a game below to get started!')
        .addFields(
            { name: '❌  Tic-Tac-Toe',        value: 'Beat the bot on a 3×3 grid',   inline: true },
            { name: '✊  Rock Paper Scissors', value: 'Best of 3 vs the bot',          inline: true },
            { name: '🃏  Higher or Lower',     value: 'Build the longest card streak', inline: true },
            { name: '❓  Trivia',              value: 'Multiple-choice trivia',         inline: true },
        )
        .setFooter({ text: 'MostlyVanilla Beacon • Games' });

    const menu = new ActionRowBuilder().addComponents(
        new StringSelectMenuBuilder()
            .setCustomId(`game_menu_${interaction.user.id}`)
            .setPlaceholder('Select a game...')
            .addOptions([
                { label: 'Tic-Tac-Toe',        value: 'ttt',    emoji: '❌', description: 'Classic 3×3 strategy vs the bot' },
                { label: 'Rock Paper Scissors', value: 'rps',    emoji: '✊', description: 'Best of 3 rounds against the bot' },
                { label: 'Higher or Lower',     value: 'hl',     emoji: '🃏', description: 'Guess if the next card is higher or lower' },
                { label: 'Trivia',              value: 'trivia', emoji: '❓', description: 'Random multiple-choice trivia questions' },
            ])
    );

    await replyOrUpdate(interaction, { embeds: [embed], components: [menu, closeRow(interaction.user.id)] });
}

// ── Interaction router ────────────────────────────────────────────────────────
async function handleInteraction(interaction) {
    const id = interaction.customId;

    if (interaction.isStringSelectMenu() && id.startsWith('game_menu_')) {
        if (interaction.user.id !== id.replace('game_menu_', ''))
            return interaction.reply({ content: 'This menu is not for you.', ephemeral: true });
        const game = interaction.values[0];
        if (game === 'ttt')    return startTTT(interaction);
        if (game === 'rps')    return startRPS(interaction);
        if (game === 'hl')     return startHL(interaction);
        if (game === 'trivia') return startTrivia(interaction);
        return;
    }

    if (!interaction.isButton()) return;

    // close must be checked before back/again since it has a similar prefix pattern
    if (id.startsWith('game_close_')) return handleClose(interaction);

    if (id.startsWith('game_back_')) {
        if (interaction.user.id !== id.replace('game_back_', ''))
            return interaction.reply({ content: 'This is not your game.', ephemeral: true });
        return showMenu(interaction);
    }

    if (id.startsWith('game_again_')) {
        const [,, gameType, userId] = id.split('_');
        if (interaction.user.id !== userId)
            return interaction.reply({ content: 'This is not your game.', ephemeral: true });
        if (gameType === 'ttt')    return startTTT(interaction);
        if (gameType === 'rps')    return startRPS(interaction);
        if (gameType === 'hl')     return startHL(interaction);
        if (gameType === 'trivia') return startTrivia(interaction);
        return;
    }

    // game_trivianext_ must be checked before game_trivia_ (prefix overlap)
    if (id.startsWith('game_trivianext_')) return handleTriviaNext(interaction);
    if (id.startsWith('game_trivia_'))     return handleTrivia(interaction);
    if (id.startsWith('game_ttt_'))        return handleTTT(interaction);
    if (id.startsWith('game_rps_'))        return handleRPS(interaction);
    if (id.startsWith('game_hl_'))         return handleHL(interaction);
}

// ═════════════════════════════════════════════════════════════════════════════
// TIC-TAC-TOE
// ═════════════════════════════════════════════════════════════════════════════

function tttWins(board, p) {
    return [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]]
        .some(([a,b,c]) => board[a] === p && board[b] === p && board[c] === p);
}

function tttMinimax(board, depth, isMax) {
    if (tttWins(board, 'O')) return 10 - depth;
    if (tttWins(board, 'X')) return depth - 10;
    const empty = board.reduce((a, v, i) => v === null ? [...a, i] : a, []);
    if (!empty.length) return 0;
    let best = isMax ? -Infinity : Infinity;
    for (const i of empty) {
        board[i] = isMax ? 'O' : 'X';
        const s = tttMinimax(board, depth + 1, !isMax);
        board[i] = null;
        best = isMax ? Math.max(best, s) : Math.min(best, s);
    }
    return best;
}

function tttBotMove(board) {
    const empty = board.reduce((a, v, i) => v === null ? [...a, i] : a, []);
    let best = -Infinity, move = empty[0];
    for (const i of empty) {
        board[i] = 'O';
        const s = tttMinimax(board, 0, false);
        board[i] = null;
        if (s > best) { best = s; move = i; }
    }
    return move;
}

function tttGrid(board, gameId, disabled = false) {
    return [0, 1, 2].map(row =>
        new ActionRowBuilder().addComponents(
            [0, 1, 2].map(col => {
                const i = row * 3 + col;
                const v = board[i];
                return new ButtonBuilder()
                    .setCustomId(`game_ttt_${gameId}_${i}`)
                    .setLabel(v ?? '·')
                    .setStyle(v === 'X' ? ButtonStyle.Danger : v === 'O' ? ButtonStyle.Primary : ButtonStyle.Secondary)
                    .setDisabled(disabled || v !== null);
            })
        )
    );
}

async function startTTT(interaction) {
    const id = nextId++;
    games.set(id, { type: 'ttt', board: Array(9).fill(null), userId: interaction.user.id });

    const embed = new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('❌ Tic-Tac-Toe')
        .setDescription('You are **X** · Bot is **O**\n\nYour turn!')
        .setFooter({ text: 'MostlyVanilla Beacon • Games' });

    await interaction.update({ embeds: [embed], components: [...tttGrid(games.get(id).board, id), closeRow(interaction.user.id)] });
}

async function handleTTT(interaction) {
    const [,, rawId, rawIdx] = interaction.customId.split('_');
    const id    = parseInt(rawId);
    const idx   = parseInt(rawIdx);
    const state = games.get(id);

    if (!state) return interaction.reply({ content: 'This game has expired.', ephemeral: true });
    if (state.userId !== interaction.user.id) return interaction.reply({ content: 'This is not your game.', ephemeral: true });

    const { board } = state;
    const stats = getStats(interaction.user.id);

    board[idx] = 'X';
    if (tttWins(board, 'X')) {
        games.delete(id);
        stats.ttt.wins++;
        const embed = new EmbedBuilder().setColor(0x57F287).setTitle('❌ Tic-Tac-Toe').setDescription('🎉 **You win!**').setFooter({ text: 'MostlyVanilla Beacon • Games' });
        return interaction.update({ embeds: [embed], components: [...tttGrid(board, id, true), endRow('ttt', interaction.user.id)] });
    }
    if (board.every(v => v !== null)) {
        games.delete(id);
        stats.ttt.draws++;
        const embed = new EmbedBuilder().setColor(0xFEE75C).setTitle('❌ Tic-Tac-Toe').setDescription("🤝 **Draw!**").setFooter({ text: 'MostlyVanilla Beacon • Games' });
        return interaction.update({ embeds: [embed], components: [...tttGrid(board, id, true), endRow('ttt', interaction.user.id)] });
    }

    board[tttBotMove(board)] = 'O';
    if (tttWins(board, 'O')) {
        games.delete(id);
        stats.ttt.losses++;
        const embed = new EmbedBuilder().setColor(0xED4245).setTitle('❌ Tic-Tac-Toe').setDescription('🤖 **Bot wins!**').setFooter({ text: 'MostlyVanilla Beacon • Games' });
        return interaction.update({ embeds: [embed], components: [...tttGrid(board, id, true), endRow('ttt', interaction.user.id)] });
    }
    if (board.every(v => v !== null)) {
        games.delete(id);
        stats.ttt.draws++;
        const embed = new EmbedBuilder().setColor(0xFEE75C).setTitle('❌ Tic-Tac-Toe').setDescription("🤝 **Draw!**").setFooter({ text: 'MostlyVanilla Beacon • Games' });
        return interaction.update({ embeds: [embed], components: [...tttGrid(board, id, true), endRow('ttt', interaction.user.id)] });
    }

    const embed = new EmbedBuilder().setColor(0x5865F2).setTitle('❌ Tic-Tac-Toe').setDescription('Your turn!').setFooter({ text: 'MostlyVanilla Beacon • Games' });
    await interaction.update({ embeds: [embed], components: [...tttGrid(board, id), closeRow(interaction.user.id)] });
}

// ═════════════════════════════════════════════════════════════════════════════
// ROCK PAPER SCISSORS
// ═════════════════════════════════════════════════════════════════════════════

const RPS = {
    rock:     { emoji: '🪨', label: 'Rock',     beats: 'scissors' },
    paper:    { emoji: '📄', label: 'Paper',    beats: 'rock'     },
    scissors: { emoji: '✂️', label: 'Scissors', beats: 'paper'    },
};

function rpsRow(gameId) {
    return new ActionRowBuilder().addComponents(
        Object.entries(RPS).map(([k, v]) =>
            new ButtonBuilder()
                .setCustomId(`game_rps_${gameId}_${k}`)
                .setLabel(v.label)
                .setStyle(ButtonStyle.Secondary)
                .setEmoji(v.emoji)
        )
    );
}

async function startRPS(interaction) {
    const id = nextId++;
    games.set(id, { type: 'rps', userId: interaction.user.id, wins: 0, losses: 0, draws: 0, round: 1 });

    const embed = new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('✊ Rock Paper Scissors')
        .setDescription('**Best of 3** — Make your move!')
        .addFields(
            { name: 'Round', value: '1 / 3', inline: true },
            { name: 'Score', value: '0 — 0', inline: true },
        )
        .setFooter({ text: 'MostlyVanilla Beacon • Games' });

    await interaction.update({ embeds: [embed], components: [rpsRow(id), closeRow(interaction.user.id)] });
}

async function handleRPS(interaction) {
    const [,, rawId, choice] = interaction.customId.split('_');
    const id    = parseInt(rawId);
    const state = games.get(id);

    if (!state) return interaction.reply({ content: 'This game has expired.', ephemeral: true });
    if (state.userId !== interaction.user.id) return interaction.reply({ content: 'This is not your game.', ephemeral: true });

    const botChoice = Object.keys(RPS)[Math.floor(Math.random() * 3)];
    let outcome;
    if (choice === botChoice)                outcome = 'draw';
    else if (RPS[choice].beats === botChoice) outcome = 'win';
    else                                      outcome = 'loss';

    if (outcome === 'win')  state.wins++;
    if (outcome === 'loss') state.losses++;
    if (outcome === 'draw') state.draws++;
    state.round++;

    const roundLine   = `${RPS[choice].emoji} **${RPS[choice].label}** vs ${RPS[botChoice].emoji} **${RPS[botChoice].label}**`;
    const outcomeText = outcome === 'win' ? '✅ You win this round!' : outcome === 'loss' ? '❌ Bot wins this round!' : '🤝 Draw!';
    const matchOver   = state.wins >= 2 || state.losses >= 2 || state.round > 3;

    if (matchOver) {
        games.delete(id);
        const won  = state.wins > state.losses;
        const tied = state.wins === state.losses;
        const sess = getStats(interaction.user.id);
        if (won)  sess.rps.wins++;
        else if (tied) sess.rps.draws++;
        else      sess.rps.losses++;

        const embed = new EmbedBuilder()
            .setColor(tied ? 0xFEE75C : won ? 0x57F287 : 0xED4245)
            .setTitle('✊ Rock Paper Scissors')
            .setDescription(`${roundLine}\n${outcomeText}\n\n${tied ? "🤝 **It's a tie!**" : won ? '🎉 **You win the match!**' : '🤖 **Bot wins the match!**'}`)
            .addFields({ name: 'Final Score', value: `You **${state.wins}** · Bot **${state.losses}** · Draws **${state.draws}**` })
            .setFooter({ text: 'MostlyVanilla Beacon • Games' });
        return interaction.update({ embeds: [embed], components: [endRow('rps', interaction.user.id)] });
    }

    const embed = new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('✊ Rock Paper Scissors')
        .setDescription(`${roundLine}\n${outcomeText}`)
        .addFields(
            { name: 'Round', value: `${state.round} / 3`,                             inline: true },
            { name: 'Score', value: `You **${state.wins}** · Bot **${state.losses}**`, inline: true },
        )
        .setFooter({ text: 'MostlyVanilla Beacon • Games' });

    await interaction.update({ embeds: [embed], components: [rpsRow(id), closeRow(interaction.user.id)] });
}

// ═════════════════════════════════════════════════════════════════════════════
// HIGHER OR LOWER
// ═════════════════════════════════════════════════════════════════════════════

const CARD_LABELS = ['A','2','3','4','5','6','7','8','9','10','J','Q','K'];

function hlButtons(gameId) {
    return new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(`game_hl_${gameId}_higher`).setLabel('Higher').setStyle(ButtonStyle.Success).setEmoji('⬆️'),
        new ButtonBuilder().setCustomId(`game_hl_${gameId}_equal`).setLabel('Same').setStyle(ButtonStyle.Secondary).setEmoji('↔️'),
        new ButtonBuilder().setCustomId(`game_hl_${gameId}_lower`).setLabel('Lower').setStyle(ButtonStyle.Danger).setEmoji('⬇️'),
    );
}

async function startHL(interaction) {
    const id      = nextId++;
    const current = Math.ceil(Math.random() * 13);
    const next    = Math.ceil(Math.random() * 13);
    games.set(id, { type: 'hl', userId: interaction.user.id, current, next, streak: 0 });

    const embed = new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('🃏 Higher or Lower')
        .setDescription(`Current card: **${CARD_LABELS[current - 1]}**\n\nWill the next card be higher or lower?`)
        .addFields({ name: '🔥 Streak', value: '**0**' })
        .setFooter({ text: 'A=1  J=11  Q=12  K=13 · MostlyVanilla Beacon • Games' });

    await interaction.update({ embeds: [embed], components: [hlButtons(id), closeRow(interaction.user.id)] });
}

async function handleHL(interaction) {
    const parts  = interaction.customId.split('_');
    const id     = parseInt(parts[2]);
    const choice = parts[3];
    const state  = games.get(id);

    if (!state) return interaction.reply({ content: 'This game has expired.', ephemeral: true });
    if (state.userId !== interaction.user.id) return interaction.reply({ content: 'This is not your game.', ephemeral: true });

    const { current, next } = state;
    const correct =
        (choice === 'higher' && next > current) ||
        (choice === 'lower'  && next < current) ||
        (choice === 'equal'  && next === current);

    const nextLabel = CARD_LABELS[next - 1];

    if (correct) {
        state.streak++;
        state.current = next;
        state.next    = Math.ceil(Math.random() * 13);

        const embed = new EmbedBuilder()
            .setColor(0x57F287)
            .setTitle('🃏 Higher or Lower')
            .setDescription(`✅ Correct! It was **${nextLabel}**.\n\nNew card: **${CARD_LABELS[state.current - 1]}**\n\nWill the next card be higher or lower?`)
            .addFields({ name: '🔥 Streak', value: `**${state.streak}**` })
            .setFooter({ text: 'A=1  J=11  Q=12  K=13 · MostlyVanilla Beacon • Games' });

        return interaction.update({ embeds: [embed], components: [hlButtons(id), closeRow(interaction.user.id)] });
    }

    const finalStreak = state.streak;
    games.delete(id);
    const sess = getStats(interaction.user.id);
    sess.hl.gamesPlayed++;
    if (finalStreak > sess.hl.bestStreak) sess.hl.bestStreak = finalStreak;

    const embed = new EmbedBuilder()
        .setColor(0xED4245)
        .setTitle('🃏 Higher or Lower')
        .setDescription(`❌ Wrong! It was **${nextLabel}**.\n\nYour final streak: **${finalStreak}**${finalStreak >= 5 ? ' 🔥' : ''}`)
        .setFooter({ text: 'MostlyVanilla Beacon • Games' });

    await interaction.update({ embeds: [embed], components: [endRow('hl', interaction.user.id)] });
}

// ═════════════════════════════════════════════════════════════════════════════
// TRIVIA
// ═════════════════════════════════════════════════════════════════════════════

const TRIVIA_QS = [
    { q: 'What is the capital of France?',                    a: 'Paris',            w: ['London', 'Berlin', 'Madrid'] },
    { q: 'How many sides does a hexagon have?',               a: '6',                w: ['5', '7', '8'] },
    { q: 'What planet is known as the Red Planet?',           a: 'Mars',             w: ['Venus', 'Jupiter', 'Saturn'] },
    { q: 'Who painted the Mona Lisa?',                        a: 'Leonardo da Vinci', w: ['Michelangelo', 'Raphael', 'Rembrandt'] },
    { q: 'What is the chemical symbol for gold?',             a: 'Au',               w: ['Ag', 'Fe', 'Gd'] },
    { q: 'How many continents are on Earth?',                 a: '7',                w: ['5', '6', '8'] },
    { q: 'What is the fastest land animal?',                  a: 'Cheetah',          w: ['Lion', 'Falcon', 'Pronghorn'] },
    { q: 'What year did World War II end?',                   a: '1945',             w: ['1943', '1944', '1946'] },
    { q: 'What is the largest ocean on Earth?',               a: 'Pacific Ocean',    w: ['Atlantic Ocean', 'Indian Ocean', 'Arctic Ocean'] },
    { q: 'How many strings does a standard guitar have?',     a: '6',                w: ['4', '5', '7'] },
    { q: 'What gas do plants absorb in photosynthesis?',      a: 'Carbon dioxide',   w: ['Oxygen', 'Nitrogen', 'Hydrogen'] },
    { q: 'Who wrote Romeo and Juliet?',                       a: 'Shakespeare',      w: ['Dickens', 'Austen', 'Twain'] },
    { q: 'What is the smallest planet in our solar system?',  a: 'Mercury',          w: ['Mars', 'Venus', 'Pluto'] },
    { q: 'How many players are on a standard soccer team?',   a: '11',               w: ['9', '10', '12'] },
    { q: 'What element has atomic number 1?',                 a: 'Hydrogen',         w: ['Helium', 'Lithium', 'Carbon'] },
    { q: 'What is the longest river in the world?',           a: 'Nile',             w: ['Amazon', 'Yangtze', 'Mississippi'] },
    { q: 'Where was pizza invented?',                         a: 'Italy',            w: ['Greece', 'France', 'Spain'] },
    { q: 'How many bones are in the adult human body?',       a: '206',              w: ['185', '212', '230'] },
    { q: 'What is the most spoken language in the world?',    a: 'Mandarin Chinese', w: ['English', 'Spanish', 'Hindi'] },
    { q: 'What shape has exactly three sides?',               a: 'Triangle',         w: ['Square', 'Pentagon', 'Hexagon'] },
    { q: 'Which planet has the most moons?',                  a: 'Saturn',           w: ['Jupiter', 'Uranus', 'Neptune'] },
    { q: 'What year did the Titanic sink?',                   a: '1912',             w: ['1905', '1918', '1920'] },
    { q: 'What is the hardest natural substance?',            a: 'Diamond',          w: ['Quartz', 'Titanium', 'Sapphire'] },
    { q: 'Which country invented the Olympic Games?',         a: 'Greece',           w: ['Rome', 'Egypt', 'China'] },
    { q: 'How many colors are in a rainbow?',                 a: '7',                w: ['5', '6', '8'] },
    { q: 'What is H₂O commonly known as?',                   a: 'Water',            w: ['Hydrogen gas', 'Helium oxide', 'Hydroxide'] },
    { q: 'What is the capital of Japan?',                     a: 'Tokyo',            w: ['Osaka', 'Kyoto', 'Hiroshima'] },
    { q: 'Which animal is known as man\'s best friend?',      a: 'Dog',              w: ['Cat', 'Horse', 'Parrot'] },
    { q: 'What instrument has black and white keys?',         a: 'Piano',            w: ['Guitar', 'Violin', 'Drums'] },
    { q: 'How many seconds are in a minute?',                 a: '60',               w: ['50', '100', '30'] },
    { q: 'What is the square root of 144?',                   a: '12',               w: ['11', '13', '14'] },
    { q: 'What sport is played at Wimbledon?',                a: 'Tennis',           w: ['Badminton', 'Squash', 'Cricket'] },
    { q: 'What is the currency of Japan?',                    a: 'Yen',              w: ['Won', 'Ringgit', 'Baht'] },
    { q: 'How many teeth does a healthy adult human have?',   a: '32',               w: ['28', '30', '34'] },
    { q: 'What country is the Eiffel Tower in?',              a: 'France',           w: ['Belgium', 'Italy', 'Spain'] },
    { q: 'What is the largest mammal on Earth?',              a: 'Blue whale',       w: ['Elephant', 'Giraffe', 'Hippopotamus'] },
    { q: 'How many days are in a leap year?',                 a: '366',              w: ['364', '365', '367'] },
    { q: 'What is the tallest mountain in the world?',        a: 'Mount Everest',    w: ['K2', 'Kangchenjunga', 'Lhotse'] },
    { q: 'Which planet is closest to the Sun?',               a: 'Mercury',          w: ['Venus', 'Earth', 'Mars'] },
    { q: 'What is the capital of Australia?',                 a: 'Canberra',         w: ['Sydney', 'Melbourne', 'Brisbane'] },
    { q: 'How many players are on a basketball team?',        a: '5',                w: ['4', '6', '7'] },
    { q: 'What is the chemical formula for salt?',            a: 'NaCl',             w: ['KCl', 'CaCl₂', 'MgCl₂'] },
    { q: 'What is the capital of Canada?',                    a: 'Ottawa',           w: ['Toronto', 'Vancouver', 'Montreal'] },
    { q: 'How many hours are in a day?',                      a: '24',               w: ['12', '20', '48'] },
    { q: 'What is the biggest planet in our solar system?',   a: 'Jupiter',          w: ['Saturn', 'Neptune', 'Uranus'] },
];

function shuffle(arr) {
    const a = [...arr];
    for (let i = a.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [a[i], a[j]] = [a[j], a[i]];
    }
    return a;
}

function pickTrivia() {
    const q       = TRIVIA_QS[Math.floor(Math.random() * TRIVIA_QS.length)];
    const options = shuffle([q.a, ...q.w]);
    return { question: q.q, options, correctIdx: options.indexOf(q.a) };
}

function triviaEmbed(question, options, score) {
    const letters = ['A', 'B', 'C', 'D'];
    return new EmbedBuilder()
        .setColor(0x5865F2)
        .setTitle('❓ Trivia')
        .setDescription(`**${question}**\n\n` + options.map((o, i) => `**${letters[i]}.** ${o}`).join('\n'))
        .setFooter({ text: `Score: ${score} correct · MostlyVanilla Beacon • Games` });
}

function triviaButtons(gameId) {
    return new ActionRowBuilder().addComponents(
        ['A', 'B', 'C', 'D'].map((l, i) =>
            new ButtonBuilder().setCustomId(`game_trivia_${gameId}_${i}`).setLabel(l).setStyle(ButtonStyle.Primary)
        )
    );
}

async function startTrivia(interaction) {
    const id = nextId++;
    const { question, options, correctIdx } = pickTrivia();
    games.set(id, { type: 'trivia', userId: interaction.user.id, question, options, correctIdx, score: 0 });

    await interaction.update({ embeds: [triviaEmbed(question, options, 0)], components: [triviaButtons(id), closeRow(interaction.user.id)] });
}

async function handleTrivia(interaction) {
    const [,, rawId, rawIdx] = interaction.customId.split('_');
    const id     = parseInt(rawId);
    const chosen = parseInt(rawIdx);
    const state  = games.get(id);

    if (!state) return interaction.reply({ content: 'This game has expired.', ephemeral: true });
    if (state.userId !== interaction.user.id) return interaction.reply({ content: 'This is not your game.', ephemeral: true });

    const correct = chosen === state.correctIdx;
    if (correct) state.score++;

    const sess = getStats(interaction.user.id);
    sess.trivia.total++;
    if (correct) sess.trivia.correct++;

    const letters = ['A', 'B', 'C', 'D'];
    const resultDesc =
        `**${state.question}**\n\n` +
        state.options.map((o, i) => {
            const mark = i === state.correctIdx ? '✅' : i === chosen ? '❌' : '○';
            return `${mark} **${letters[i]}.** ${o}`;
        }).join('\n') +
        `\n\n${correct ? '✅ **Correct!**' : `❌ **Wrong!** The answer was **${state.options[state.correctIdx]}**`}`;

    const embed = new EmbedBuilder()
        .setColor(correct ? 0x57F287 : 0xED4245)
        .setTitle('❓ Trivia')
        .setDescription(resultDesc)
        .setFooter({ text: `Score: ${state.score} correct · MostlyVanilla Beacon • Games` });

    const next = pickTrivia();
    state.nextQuestion   = next.question;
    state.nextOptions    = next.options;
    state.nextCorrectIdx = next.correctIdx;

    const nextRow = new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId(`game_trivianext_${id}`).setLabel('Next Question').setStyle(ButtonStyle.Success).setEmoji('➡️'),
        new ButtonBuilder().setCustomId(`game_back_${interaction.user.id}`).setLabel('Back to Menu').setStyle(ButtonStyle.Secondary).setEmoji('🎮'),
        new ButtonBuilder().setCustomId(`game_close_${interaction.user.id}`).setLabel('Close').setStyle(ButtonStyle.Danger).setEmoji('✖️'),
    );

    await interaction.update({ embeds: [embed], components: [nextRow] });
}

async function handleTriviaNext(interaction) {
    const id    = parseInt(interaction.customId.replace('game_trivianext_', ''));
    const state = games.get(id);

    if (!state) return interaction.reply({ content: 'This game has expired.', ephemeral: true });
    if (state.userId !== interaction.user.id) return interaction.reply({ content: 'This is not your game.', ephemeral: true });

    state.question   = state.nextQuestion;
    state.options    = state.nextOptions;
    state.correctIdx = state.nextCorrectIdx;

    await interaction.update({
        embeds: [triviaEmbed(state.question, state.options, state.score)],
        components: [triviaButtons(id), closeRow(interaction.user.id)],
    });
}

module.exports = { data, execute, handleInteraction };
