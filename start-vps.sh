#!/bin/bash
# Starter serveren på Linux/VPS (Oracle Cloud Ubuntu).
# Krever: Java 25 installert («setup-oracle.sh» gjør det).
# NB: start.sh (Mac) og start-vps.sh (Linux) ligger ved siden av hverandre –
#     kjør kun den som passer OS-et.
cd "$(dirname "$0")"

if [ "$(uname)" = "Darwin" ]; then
    echo "FEIL: Dette er start-vps.sh (Linux). På Mac bruker du ./start.sh"
    exit 1
fi

JAVA_BIN="java"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    echo "Java er ikke installert. Kjør: sudo ./setup-oracle.sh"
    exit 1
fi

echo "Starter Tangen VGS Minecraft 26.2 Server..."
echo "📦 Teksturpakke: PureBDcraft 256x (https://files.catbox.moe/l69xqz.zip)"
"$JAVA_BIN" -Xms4G -Xmx8G \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -XX:G1NewSizePercent=30 \
    -XX:G1MaxNewSizePercent=40 \
    -XX:G1ReservePercent=20 \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -Dusing.aikars.flags=https://mcflags.emc.gs \
    -Daikars.new.flags=true \
    -jar paper.jar --nogui