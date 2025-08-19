const Anthropic = require('@anthropic-ai/sdk');
const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

async function callClaude() {
  const response = await client.messages.create({
    model: 'claude-4-1-20250805',
    max_tokens: 1024,
    messages: [{ role: 'user', content: 'Write a JavaScript function to sort an array.' }]
  });
  console.log(response.content[0].text);
}

callClaude();