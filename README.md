# Tangen VGS - IM Minecraft Server (Paper 26.2)

Velkommen til den offisielle Minecraft-serveren for IM på Tangen videregående skole!

Serveren kjører **PaperMC 26.2** med **Java 21+**, Aikar's ytelsesflagg, samt en komplett pakke med plugins. Verden, config og plugins ligger i dette git-repoet – klon det over til en Ubuntu-VPS (f.eks. Oracle Cloud) for stabil hosting med egen IP.

---

## 🚀 Slik starter du serveren

1. Åpne Terminal på Mac-en.
2. Gå inn i servermappen:
   ```bash
   cd ~/Desktop/tangen-mc-server
   ```
3. Start serveren:
   ```bash
   ./start.sh
   ```
4. For å stoppe serveren trygt, skriv i server-konsollen:
   ```text
   stop
   ```

---

## 🌍 Hosting på Ubuntu VPS (Oracle Cloud) – anbefalt

Slik flytter du serveren til en ekte maskin med egen IP (ingen tunneling nødvendig):

1. **Klon repoet** på VPS-en (Debian/Ubuntu):
   ```bash
   git clone <repo-url> ~/tangen-mc-server && cd ~/tangen-mc-server
   ```
2. **Kjør oppsettet** (installerer Java 25 + åpner port 25565 i ufw):
   ```bash
   ./setup-oracle.sh
   ```
3. **Åpne port 25565 i Oracle-konsollen:** *Networking → VCN → Security Lists → Ingress Rules* → legg til regel: Source `0.0.0.0/0`, Protocol TCP, Destination Port `25565`.
4. **Start serveren:**
   ```bash
   nohup ./start-vps.sh > console.log 2>&1 &
   ```
5. **IP-en spillerne bruker:** `curl ipinfo.io/ip` (f.eks. `123.123.123.123` – legges i Minecraft som serveradresse). Bekreft at den er nåbar utenfra: `curl -s https://ifconfig.co/port/25565` → `"reachable": true`.

> 💡 **Oppdatering:** Når du endrer verden/config lokalt på Mac, `git add -A && git commit -m "..." && git push`, og `git pull` på VPS-en for å hente endringene.

---

## 🌐 Koble til serveren (Direkte IP)

Spillere på samme nettverk/Wi-Fi kan koble til direkte med Mac-ens IP-adresse. IP-en kan endre seg, så **sjekk IP-en hver gang serveren startes** – den skrives ut i konsollen ved oppstart, eller kjør dette i Terminal på Mac-en:

```bash
ipconfig getifaddr en0
```

Deretter godtar elever teksturpakken når de logger inn (kun én nedlasting per elev, Minecraft cacher den).

> 💡 **Anbefaling:** Be IT-avdelingen om fast IP/DHCP-reservasjon til Mac-en din. Da slipper elever å jage etter IP-adressen hver eneste gang.

---

## 🔌 Installerte Plugins & Hva de gjør

| Plugin | Versjon | Beskrivelse og Bruksområde |
| :--- | :--- | :--- |
| **LuckPerms** | 5.5.71 | Tillatelseshåndtering og roller for elever og lærere (`/lp editor`). |
| **EssentialsX** | 2.22.0 | Grunnleggende serverkommandoer: `/spawn`, `/sethome`, `/home`, `/tpa`, `/warp`, kits og økonomi. |
| **Vault** | 2.20.2 | Bro for chat-prefikser, rettigheter og økonomi. |
| **WorldEdit** | 7.4.5 | Bygge- og terraformingsverktøy med treøks (`//wand`). Støtter Paperweight 26.2. |
| **WorldGuard** | 7.0.18 | Områdebeskyttelse for spawn, klasserom og fellesprosjekter mot ødeleggelse. |
| **CoreProtect** | 24.0 | Blokklogging og rollback (`/co inspect`, `/co rollback`). |
| **Chunky** | 1.5.3 | Forhåndsgenerering av kartet for lagg-fri utforskning i 26.2. |
| **TangenStats** | 1.0 | Sidepanel med drap/dødsfall, toppliste (`/topp`) og roller (owner/admin/mod/elev). |

---

## 📊 Stats, Toppliste & Roller (TangenStats)

- **Sidepanel:** Hver spiller ser på siden av skjermen sine egne stats – drap, dødsfall, død/drap-forhold og rolle.
- **Toppliste:** `/topp` (eller `/topp <antall>`) viser de med flest drap, med rolle ved siden av navnet. Dine egne stats: `/stats`.
- **Roller** (via LuckPerms-grupper): `owner`, `admin`, `mod` og `elev`.
  - Alle som joiner får automatisk rollen **elev**.
  - Eier/admin tildeler roller med: `/role give <spiller> <owner|admin|mod|elev>`. Eier og admin får automatisk tillatelse til dette (permission `tangen.stats.role`).
  - Eier og admin får også prefiks i chatten (`[Owner]`, `[Admin]`), mod får `[Mod]`.

---

## 🛡️ Administrator-kommandoer & Sikkerhet
- **Gi deg selv OP:** `op <ditt_brukernavn>`
- **Åpne rettighetsvelger:** `lp editor`
- **Sjekke blokk-endringer:** `/co i`
- **Anti-Xray:** Innebygd Paper Engine Mode 2 (obfuskering av diamanter, gull, jern og ancient debris).
- **Default Texture Pack:** PureBDcraft 256x for Minecraft 26.2. Tilbys av **BetterPack-plugin** *etter* at du er inne på serveren (blokkerer aldri innlogging). Spørsmålet dukker opp i chatten et par sekunder etter join – trykk «Ja» for å laste ned. Pakken ligger på en offentlig HTTPS-lenke (`https://files.catbox.moe/l69xqz.zip`), så den fungerer både på skolenettet og hjemmefra.

---

## 🧰 Ting å vite / gjøre rivisata

- **Store teksturpakker = treig bruk ved mange samtidige innlogginger.** PureBDcraft 256x er 86 MB, og alle nye spillere må laste den ned. Pakken ligger nå på en offentlig HTTPS-vert (catbox), så nedlastingen belaster ikke lenger serverens egen tilkobling. Hvis det fortsatt er tregt, prøv en mindre pakke (128x).
- **Tilkobling utenfra:** Serveren hostes nå på en VPS med egen IP (se avsnittet "Hosting på Ubuntu VPS" over). Ingen tunnel nødvendig.
- **Serveren kjører på WiFi.** For best ytelse bør Mac-en ligge på kablet nett der skolen tillater det, eller få fast IP.
