# Bot-Armee

Minecraft-Mod (Fabric, Minecraft 26.3) für Civilization-Videos: Armeen aus Fake-Spielern spawnen, Ausrüstung am Armor-Stand einstellen, Ablauf planen und die Schlacht laufen lassen.

## Installieren (Lunar Client)

1. Lade die Datei `botarmee-0.2.0.jar` herunter.
2. Öffne den Lunar Client, wähle **Minecraft 26.3** und als Loader **Fabric**.
3. Klicke auf das Ordner-Symbol bei Mods und lege die `.jar` in den Mods-Ordner.
4. Die Mod braucht **Fabric API**. Im Lunar Client ist das normalerweise schon dabei. Falls Minecraft meckert, dass `fabric-api` fehlt: auf modrinth.com „Fabric API“ für 26.3 herunterladen und auch in den Mods-Ordner legen.
5. Starte das Spiel und öffne eine **Einzelspieler-Welt** (am besten Kreativmodus, Superflach).

## So machst du eine Schlacht

1. Drück **B**. Das Menü öffnet sich.
2. **Teams:** Name, Farbe, Anzahl Bots, Formation, Bogenschützen-Anteil und Vorteil einstellen. Mit „+ Team“ gehen bis zu 4 Teams.
3. **Ausrüstung:** Klick am Armor-Stand auf Kopf, Brust, Beine, Füße oder die Hände. Rechts erscheinen dann die passenden Teile. Darunter kannst du Trim-Muster, Trim-Material und Glitzern auswählen. Unten links gibt es schnelle Vorlagen (Leder, Eisen, Diamant …).
4. **Ablauf:** Unter „Rigging“ stellst du ein, wer gewinnen soll und wie knapp es wird. Darunter baust du die Sequenz pro Team (z. B. 3 s Halten → 6 s Marschieren → Angriff).
5. **Arena:** Schlachtfeld aussuchen, Größe wählen, „Arena hier bauen“ drücken. Es wird um dich herum gebaut.
6. Unten auf **Aufstellen** drücken, dann auf **▶ LOS!**. Es kommt ein Countdown 3-2-1, danach kämpfen sie.
7. **Aufräumen** entfernt alle Bots. **Pause** friert die Schlacht ein.

## Tipps für Videos

- **F1** blendet das Interface aus (auch den Zähler).
- `/tick rate 10` = Zeitlupe, `/tick rate 20` = normal.
- Zuschauer-Modus (**F3 + N**) für Kamerafahrten durch die Armeen.
- Eigene Skins: Im Tab **Extras** auf „Skin-Ordner öffnen“ klicken und 64x64-PNG-Skins hineinlegen. Steht „slim“ im Dateinamen, bekommt der Skin dünne Arme. Dann „Eigene Skins (Ordner)“ auswählen.
- `/botarmee clear` entfernt alle Bots, falls das Menü mal nicht geht.

## Gut zu wissen

- Bis zu 500 Bots pro Team und 1000 insgesamt.
- **Ab 300 Bots** erscheint ein orangenes ⚠: Die KI wird einfacher (kein Ausweichen, keine Crit-Sprünge), damit dein PC nicht laggt.
- **Ab 600 Bots** wird das ⚠ rot (Sparmodus): Die Bots laufen geradeaus, suchen seltener neue Gegner und flankieren nicht. Rigging funktioniert trotzdem.
- Tipp bei vielen Bots: Im Tab Extras „Namen über den Köpfen“ ausschalten. Das spart viel Leistung.
- Das Schlachtfeld bleibt geladen, auch wenn du für Kamerafahrten weit wegfliegst.
- Bots werden nicht mit der Welt gespeichert. Nach dem Verlassen sind sie weg.
- Die Mod muss auch auf dem Server sein. Im Einzelspieler ist das automatisch so.
