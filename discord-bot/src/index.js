require('dotenv').config();
const { startBot } = require('./bot');
const { startApi } = require('./api');
startBot(process.env.DISCORD_TOKEN);
startApi(parseInt(process.env.API_PORT) || 3000);
