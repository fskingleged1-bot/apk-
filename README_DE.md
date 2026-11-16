# ChessProAuto Android v1 – Erster Prototyp für Nothing Phone

**Status:** Vollständiges Android-Studio-Quellprojekt, jedoch **keine vorkompilierte APK**. Das Android SDK und die Build-Tools sind in der Erstellungsumgebung nicht vorhanden. Es wurde keine Installation auf einem echten Nothing Phone durchgeführt.

## Das ist bereits implementiert

- Android-Startbildschirm mit **Training starten / stoppen**.
- **MediaProjection**: Bildschirmaufnahme mit der ausdrücklich angezeigten Android-Systemfreigabe.
- **SYSTEM_ALERT_WINDOW**: Klickdurchlässiges Overlay direkt über der Schach-App (nicht als Chrome-Erweiterung).
- **Grün = Ausgangsfeld, Rot = Zielfeld**, nur nach einem bestätigten legalen Enginezug.
- Einmal pro Brettstellung ausgegebene Empfehlung statt wechselnder Sofort-Tipps.
- Automatische 8×8-Brettsuche und normalisierte Figurenvorlagen (aus dem vorhandenen PC-Projekt v14).
- Automatische Zuordnung der Brettausrichtung und manuelle Spielerseite Weiß/Schwarz; einmalige Hilfe für unbekanntes Zugrecht.
- Schachregeln und Stellungsverfolgung inklusive Rochade, en passant und Umwandlung.
- FEN-Analyse über `https://chess-api.com/v1`; **nur FEN**, keine Bildschirmbilder werden übertragen.
- Vordergrunddienst mit dauerhaft sichtbarer Systembenachrichtigung und Stopptaste in der App.

## Noch nicht fertig / nicht zugesichert

- **Kein lokales Stockfish-19-Binary im Projekt:** Version 1 verwendet die Stockfish-Cloud API und braucht Internet. Für PC-ähnliche Turbo-Geschwindigkeit ist später ein für Android ARM64 gebauter Stockfish nötig.
- **Kein installierbares APK-Binary im ZIP**, nur die Build-Dateien und Java-Quellen.
- Erkennung wurde an einem v14-Referenzbrett und drei daraus erstellten Bildschirmgrößen getestet, **nicht** auf der echten Nothing-Phone-Anzeige. Andere Designs können versagen.
- Im Einstieg mitten in die Partie ist das Zugrecht ohne sichtbare letzte Zugmarkierung nicht immer sicher erkennbar; dann gibt es die manuelle Einmal-Auswahl.
- Eine Bildschirmaufnahme muss **bei jeder neuen Aufnahme-Session** von Android ausdrücklich erlaubt werden, besonders auf Android 14+.
- Keine Garantie für minimale Antwortzeiten, perfekten Enginezug oder ein bei jeder Bildschirmfreigabe unsichtbares Overlay.

## APK selbst in Android Studio bauen

1. Android Studio installieren und den Ordner `ChessProAuto_Android_v1` als Projekt öffnen.
2. JDK 17, Android SDK Platform 35 und Android SDK Build-Tools 35.0.0 installieren (Android Studio bietet dies bei Bedarf an).
3. Projekt mit Gradle 8.9 / Android Gradle Plugin 8.7.3 synchronisieren. Bei einer fehlenden Gradle-Wrapper-Datei eine lokale Gradle-8.9-Installation im Gradle-Dialog wählen. Alternativ den beiliegenden GitHub-Actions-Workflow benutzen.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Die Debug-Datei liegt danach in `app/build/outputs/apk/debug/app-debug.apk` und kann auf dein Nothing Phone übertragen werden. Auf dem Telefon gegebenenfalls „Installation aus dieser Quelle zulassen“ einstellen.

## Alternativ über GitHub Actions bauen (ohne eigenes Android SDK)

Lege den Inhalt dieses Projektordners in einem eigenen GitHub-Repository ab. Starte **Actions → Android APK erstellen → Run workflow**. Nach einem erfolgreichen Build ist unter **Artifacts** die Datei `ChessProAuto-Android-v1-debug-apk` verfügbar. Die Build-Konfiguration befindet sich unter `.github/workflows/build-apk.yml`.

> Das Build-Skript ist eingerichtet, aber in dieser Umgebung nicht ausgeführt. Ein erfolgreicher CI-Build kann daher noch nicht zugesichert werden.

## Auf dem Handy benutzen

1. App öffnen und **Training starten** wählen.
2. Systemberechtigung für **Über anderen Apps anzeigen** erteilen.
3. Android-Bildschirmaufnahme ausdrücklich erlauben und den **gesamten Bildschirm** freigeben.
4. Chess.com oder eine unterstützte Schach-App mit einem Bot-/Trainingsbrett öffnen.
5. Eine erkannte Stellung wird geprüft. Ist deine Seite am Zug, fragt die App die Stockfish-Cloud nach dem Zug und markiert das Startfeld **grün**, das Zielfeld **rot**.
6. Ist die Spielerseite falsch, die App öffnen und **Ich spiele SCHWARZ / WEISS** wählen. Ist das Zugrecht beim Partieneinstieg unbekannt, einmalig **Weiß/Schwarz ist am Zug** wählen.
7. Zum Beenden die App erneut öffnen und **Analyse stoppen** auswählen.

## Tests, die bereits gelaufen sind

Auf Java 17: FEN-Startstellung, Zugfolge, Legalisierung, Rochade, en passant, Bauernumwandlung, Zugverfolgung, Einstieg in die Partie. Vier simulierte Screenshotformate bestanden die Board- und Figurenanalyse, darunter die Stellung **nach 1. e4, Schwarz am Zug**.

Mit installiertem Java können die Kern-Tests im Projekt ausgeführt werden: `bash tests/run-tests.sh`.

## Sicherheit und Datenschutz

Das Bild wird auf dem Gerät analysiert; nur die daraus berechnete Schach-FEN wird über HTTPS an die Cloud-API gesendet. System-Overlay und Bildschirmaufnahme benötigen sichtbare Android-Berechtigungen. Es gibt keine versteckte Aufnahme. Der Prototyp ist für Bot-Spiele, private Übung und Analyse bestimmt. Er ist keine Hilfe für unerlaubte Engine-Nutzung gegen menschliche Gegner.

## Dateistruktur

- `app/src/main/java/de/chesspro/auto/MainActivity.java`: Steuerung
- `ProjectionService.java`: Screenshot- und Analyse-Lebenszyklus
- `BoardDetector.java`: Brettsuche
- `PieceVision.java`: Figurenerkennung
- `ChessPosition.java`: Schachregeln, FEN und legale Zugverfolgung
- `EngineClient.java`: Cloud-Stockfish
- `MoveOverlay.java`: grüne/rote Feldmarkierungen
- `tests/`: Desktoptests ohne Android-SDK
- `.github/workflows/build-apk.yml`: Buildpipeline für eine Debug-APK
