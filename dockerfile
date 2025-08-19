FROM node:18-slim
WORKDIR /app
COPY . .
RUN npm install @anthropic-ai/sdk @anthropic-ai/claude-code
CMD ["node", "index.js"]