#!/bin/bash
# Kjør én gang på Oracle Cloud Ubuntu VPS-en FØR første serverstart.
# Installerer Java 25 (Temurin/Adoptium – Paper 26.2 krever 25+),
# åpner port 25565 i ufw og viser ingress-regelen som må settes i Oracle.
set -e

if [ "$(uname)" = "Darwin" ]; then
    echo "FEIL: Dette skal kjøres på Linux-VPS-en, ikke på Mac."
    exit 1
fi

CODENAME=$(. /etc/os-release && echo "$VERSION_CODENAME")

echo "=== 1/4 Installerer forutsetninger og Temurin Java 25 ==="
sudo apt-get update
sudo apt-get install -y wget gpg ufw
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg
echo "deb https://packages.adoptium.net/artifactory/deb $CODENAME main" \
    | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update
sudo apt-get install -y temurin-25-jre

echo "=== 2/4 Velger Java 25 som standard ==="
sudo update-alternatives --set java /usr/lib/jvm/temurin-25-jre-amd64/bin/java 2>/dev/null || true

echo "=== 3/4 Åpner port 25565 i ufw ==="
sudo ufw allow 25565/tcp
sudo ufw --force enable

echo "=== 4/4 Sjekker Java ==="
java -version

echo
echo "VIKTIG: Åpne i tillegg port 25565 i Oracle Cloud-konsollen:"
echo "  Networking -> Virtual Cloud Networks -> (ditt VCN) -> Security Lists"
echo "  -> Ingress Rules -> Add Ingress Rule:"
echo "       Source: 0.0.0.0/0, Protocol: TCP, Destination Port: 25565"
echo "Da har spillere fra hele internett tilgang til IP-en din (se: curl ipinfo.io/ip)."
echo "Start serveren med: nohup ./start-vps.sh > console.log 2>&1 &"