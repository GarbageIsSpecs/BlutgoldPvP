# Blutgold PvP – Fabric-Mod für Minecraft 26.3

Client-Mod mit PvP-Komfort. Alles sind reine Anzeige-Hilfen, keine Cheats (kein Reach, kein Aimbot, nichts durch Wände).

## Features

**Menü: Taste M** – Tabs **PvP** (HUD, Info-Anzeigen, Hitboxen, Crosshair, Block-Rahmen, Komfort), **Cosmetics** (Drachenflügel, Heiligenschein, Partikel – nur für dich sichtbar), **Design** (6 Themes) und **Info**.

| Feature | Taste | Beschreibung |
|---|---|---|
| Rote Hitboxen | **H** | Roter Rahmen + leichte Füllung um Spieler, dunkle Linie auf Augenhöhe. Blitzt gold, wenn das Ziel getroffen wird. Nicht durch Wände, unsichtbare Spieler bleiben unsichtbar. |
| Ziel-Crosshair | – | Crosshair wird rot (Spieler) bzw. orange (Mobs), sobald ein Gegner in Schlagreichweite im Visier ist. Mit Abklingzeit-Balken. |
| PvP-HUD | **J** | FPS und CPS (links \| rechts) oben links, aktive Tränke mit Stufe und Restzeit darunter, Rüstung + Waffe mit Haltbarkeit unten rechts. |
| Toggle-Sprint | **K** | Du sprintest automatisch, solange du vorwärts läufst. |
| Regenbogen-Rahmen | **O** | Animierter Regenbogen-Rahmen um den Block, auf den du schaust. Ohne Resource Pack, ohne Core Shaders. |
| Kein Kamera-Wackeln | **N** | Schaltet das Wackeln bei Treffern ab (nutzt die Vanilla-Option „Schadensneigung“). |

Tasten kannst du unter *Optionen → Steuerung → Tastenbelegung → Blutgold PvP* ändern.
Alle Einstellungen stehen außerdem in `.minecraft/config/blutgoldpvp.properties`
(`true` = an, `false` = aus), z. B. `hitboxesPlayersOnly=false`, wenn auch Mobs Hitboxen bekommen sollen.

## Die .jar bauen lassen (ohne Programmieren)

1. Konto auf **github.com** erstellen (kostenlos).
2. Oben rechts **+ → New repository**, Name z. B. `BlutgoldPvP`, auf **Create repository** klicken.
3. Auf **uploading an existing file** klicken und **alle Dateien und Ordner** aus diesem Projekt hineinziehen
   (wichtig: auch den Ordner `.github`). Dann **Commit changes**.
4. Oben auf **Actions** klicken. Der Build startet von selbst und dauert ca. 3–5 Minuten.
5. Bei grünem Haken ✅ den Lauf anklicken, unten bei **Artifacts** `BlutgoldPvP-Mod` herunterladen.
   In der ZIP liegt `blutgold-pvp-1.0.0.jar` (die Datei **ohne** „sources“ im Namen ist die Mod).

Rotes ❌? Den Lauf anklicken → **build** → den roten Schritt aufklappen und den Fehlertext an Claude schicken.

## Installieren

1. **Fabric Loader** für 26.3 installieren: https://fabricmc.net/use/installer/
2. **Fabric API** für 26.3 herunterladen (Modrinth oder CurseForge).
3. Beide `.jar`-Dateien (Fabric API + Blutgold PvP) in `.minecraft/mods` legen.
4. Im Launcher das Profil **fabric-loader-26.3** starten.

Hinweis: Manche Server erlauben nur bestimmte Mods. Diese Funktionen gelten fast überall als erlaubt,
schau aber im Zweifel in die Serverregeln.
