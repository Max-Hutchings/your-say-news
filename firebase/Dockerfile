FROM oven/bun:1.3.14-debian

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /firebase

COPY firebase/package.json firebase/bun.lock ./
RUN bun install --frozen-lockfile --production

COPY firebase/firebase.json firebase/seed-users.mjs firebase/test-accounts.json ./

EXPOSE 4000 9099

CMD ["bun", "run", "firebase", "emulators:start", "--only", "auth", "--project", "demo-your-say-news"]
