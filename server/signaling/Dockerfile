FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
EXPOSE 8443
ENV SIGNALING_PORT=8443
CMD ["npm", "start"]
