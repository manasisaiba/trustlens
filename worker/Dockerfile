FROM mcr.microsoft.com/playwright:v1.56.1-noble
WORKDIR /app
ENV NODE_ENV=production PORT=4000 SCREENSHOT_DIR=/data/screenshots
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force
COPY src ./src
RUN mkdir -p /data/screenshots && chown -R pwuser:pwuser /data /app
EXPOSE 4000
HEALTHCHECK --interval=20s --timeout=5s --start-period=20s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:4000/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["sh", "-c", "mkdir -p /data/screenshots && chown -R pwuser:pwuser /data/screenshots && exec env HOME=/home/pwuser setpriv --reuid=pwuser --regid=pwuser --init-groups node src/server.js"]
