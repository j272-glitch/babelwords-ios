const Anthropic = require('@anthropic-ai/sdk');
const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

async function callClaude() {
  try {
    const response = await client.messages.create({
      model: 'claude-4-1-20250805',
      max_tokens: 1024,
      messages: [{ role: 'user', content: 'Write a Node.js function to manage a Replit project, including file creation and git commits.' }]
    });
    console.log(response.content[0].text);
  } catch (error) {
    console.error('Error:', error);
  }
}

callClaude();