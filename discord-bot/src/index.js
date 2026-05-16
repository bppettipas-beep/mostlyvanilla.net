require('dotenv').config();

const { startApi } = require('./api');
const { startBot } = require('./bot');

// Railway injects PORT; fall back to API_PORT for local dev
const port = parseInt(process.env.PORT || process.env.API_PORT) || 3000;

startApi(port);
startBot(process.env.DISCORD_TOKEN);
