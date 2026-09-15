#!/bin/bash
# Kjør én gang på Oracle Cloud Ubuntu VPS-en FØR første serverstart.
# Installerer Java 21 (Paper 26.2 trenger 21+), åpner port 25565 i ufw
# og gir eieren tilgang til servermappa.
set -e

if [ "$(uname)" = "Darwin" ]; then
    echo "FEIL: Dette skal kjøres på Linux-VPS-en, ikke på Mac."
    exit 1
fi

echo "=== 1/3 Installerer OpenJDK 21 headless ==="
sudo apt-get update
sudo apt-get install -y openjdk-21-jre-headless ufw

echo "=== 2/3 Åpner port 25565 i ufw ==="
sudo ufw allow 25565/tcp
sudo ufw --force enable

echo "=== 3/3 Sjekker Java ==="
java -version

echo
echo "VIKTIG: Åpne i tillegg port 25565 i Oracle Cloud-konsollen:"
echo "  Networking -> Virtual Cloud Networks -> (ditt VCN) -> Security Lists"
echo "  -> Ingress Rules -> Add Ingress Rule:"
echo "       Source: 0.0.0.0/0, Protocol: TCP, Destination Port: 25565"
echo "Da har spillere fra hele internett tilgang til IP-en din (se: curl ipinfo.io/ip)."
echo "Start serveren med: nohup ./start-vps.sh > console.log 2>&1 &"