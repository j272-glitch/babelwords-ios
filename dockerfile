FROM node:18-slim
WORKDIR /app
COPY . .
RUN npm install @anthropic-ai/sdk
CMD ["node", "index.js"]