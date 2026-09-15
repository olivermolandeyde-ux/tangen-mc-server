#!/bin/bash
cd "$(dirname "$0")"

# Teksturpakken ligger på en offentlig HTTPS-lenke (plugins/BetterPack/config.yml),
# så den må IKKE oppdateres med lokal IP. LOCAL_IP brukes kun til å vise
# direkte-tilkoblingsadressen for spillere på skolenettet.
LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "localhost")

JAVA_BIN="./jdk-25/Contents/Home/bin/java"
if [ ! -f "$JAVA_BIN" ]; then
    JAVA_BIN="./jdk-21/Contents/Home/bin/java"
fi
if [ ! -f "$JAVA_BIN" ]; then
    JAVA_BIN="java"
fi

echo "Starter Tangen VGS Minecraft 26.2 Server..."
echo "🌐 Direkte tilkobling (skolenettet): $LOCAL_IP"
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
