# Bot-Armee

Minecraft-Mod (Fabric, Minecraft 26.3) für Civilization-Videos: Armeen aus Fake-Spielern spawnen, Ausrüstung am Armor-Stand einstellen, Ablauf planen und die Schlacht laufen lassen.

## Installieren (Lunar Client)

1. Lade die Datei `botarmee-0.3.3.jar` herunter.
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

## Neu in 0.3

- **Menschlicher kämpfen** (Tab Ablauf): Bots laufen in Bögen auf den Gegner zu, zögern mal, weichen nach einem Schlag zurück, heben das Schild, wenn ein Gegner gleich zuschlägt, und hauen auch mal daneben. Sie greifen bevorzugt zu zweit einen Gegner an, statt alle auf einen Haufen.
- **Held** (Tab Teams → „Held (bessere KI)“): steht vor seiner Armee, hat 1,5× Leben, blockt fast immer rechtzeitig, kontert und kreist um seine Gegner. Schau ihn an und **duck dich 2×** – er duckt sich mit. Schau ihn an und **dreh dich einmal im Kreis** – er dreht sich mit.
- **Selbst mitkämpfen** (Tab Teams → „Ich kämpfe in diesem Team“): Du wirst zu deinem Team teleportiert, bekommst die Team-Ausrüstung (alte Rüstung kommt ins Inventar) und die Gegner greifen dich an. Wichtig: `/gamemode survival` und die Welt nicht auf Friedlich.
- **Mitkämpfen-Extras (0.3.1)**: Du bekommst 2 Goldäpfel und 8 Steak. Ist in deinem Team auch ein Held, bekommt jedes Gegner-Team zum Ausgleich 2 Bots extra.
- **Weniger Klumpen (0.3.1)**: Jeder Bot bleibt auf seinen Gegner fokussiert, höchstens zwei greifen denselben an, die anderen warten mit Abstand oder suchen sich einen freien Gegner. Teamkollegen halten Abstand zueinander.
- **0.3.2**: Kein doppeltes Inventar mehr bei mehrmaligem Aufstellen. Gebaute Arenen sind geschützt: keine Explosionslöcher (Creeper, TNT), kein Abbauen im Überlebensmodus, Monster verschwinden aus der Arena. `/botarmee schutzaus` hebt den Schutz auf. Im Kolosseum gibt es vier Podeste mit Treppe, auf denen die Bogenschützen stehen.
- **0.3.3**: Nach der Schlacht erscheint im Chat **[▶ Nochmal spielen]** (oder im Menü der Knopf „Nochmal“): gleiche Einstellungen, neu aufstellen, Countdown. Zu Beginn jeder Runde bekommst du volle Leben, vollen Hunger, Effekte und Feuer werden entfernt. Im Kreativmodus zählst du nicht mehr als Kämpfer (sonst endete die Schlacht nie).
- **Schönere Arenen**: Feldlager mit Zelten, Fahnen in Teamfarbe auf den Burgen, Oasen mit Palmen, Iglus und Schneemänner, Netherfestungs-Ruinen, und das Kolosseum hat Sitzreihen, eine Kaiserloge und Eingänge mit Gittern.

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
