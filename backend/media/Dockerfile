FROM oven/bun:1.4.4-alpine@sha256:4cf82f5686b196504fe119db3fcd9185b191f82ece82c40680e597228ec6070a
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile --production
COPY src ./src
COPY video-index.json ./video-index.json
USER bun
EXPOSE 3000
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=3 CMD bun -e "const r=await fetch('http://localhost:3000/readyz');process.exit(r.ok?0:1)"
CMD ["bun", "src/index.ts"]
