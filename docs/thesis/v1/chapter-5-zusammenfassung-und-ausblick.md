# 5 Fazit und Ausblick

Dieses Kapitel fasst die Ergebnisse zusammen (5.1) und beantwortet die Forschungsfrage aus Abschnitt 1.4 (5.2). Danach wird das Backend systematisch mit den Anforderungen aus Kapitel 3 und den Arbeitspaketen der Ausschreibung abgeglichen (5.3). Es folgen eine kritische Betrachtung der Grenzen dieser Arbeit (5.4), ein Ausblick auf mögliche Weiterentwicklungen (5.5) und eine Schlussbetrachtung (5.6).

## 5.1 Zusammenfassung der Ergebnisse

Ausgangspunkt war das PHP-Backend von DESIGN, das die Ausschreibung als qualitativ schwach und schlecht dokumentiert beschreibt (Baun, 2026). Da es keine verlässliche Dokumentation gab, wurde sein Verhalten zunächst aus dem Quellcode rekonstruiert (Kapitel 3). Daraus und aus der Ausschreibung ergaben sich 13 funktionale und 11 nicht-funktionale Anforderungen. Zusätzlich wurde im Quellcode von Frontend und DaaS-Anwendung ermittelt, welche Teile der alten Schnittstelle die beiden Clients tatsächlich nutzen (Tabelle 3.5).

Auf dieser Grundlage entstand mit Java 21 und Spring Boot ein eigenständiger Dienst, der als Docker-Image ausgeliefert wird und in die vier Module *auth*, *permission*, *user* und *desktop* gegliedert ist (Kapitel 4). Die Authentifizierung beruht auf RS256-signierten JSON Web Token, deren Widerruf zusätzlich in der Datenbank geprüft wird. Abmeldung, Passwortänderung und Deaktivierung eines Kontos wirken dadurch sofort, nicht erst nach Ablauf eines Tokens. Die vierstufige Berechtigungskaskade des alten *PermissionCalculationService* wurde mit gleicher Reihenfolge der Stufen und mit Verweigerung als Standard übernommen. Für widersprüchliche Regeln innerhalb einer Stufe, die das alte System nicht eindeutig behandelte, gilt nun eine feste Regel: Ein ausdrückliches Verweigern hat Vorrang (Abschnitt 4.3.2).

Um die Wartbarkeit zu sichern, gehören zwei automatische Prüfungen fest zum Maven-Build: 19 ArchUnit-Regeln für Schichtung, Abhängigkeiten und Namen sowie eine Mindest-Testabdeckung von 70 % der Codezeilen mit JaCoCo. Die Testsuite umfasst 31 Testklassen. Laut dem zuletzt erzeugten JaCoCo-Bericht sind 87 % der Zeilen und 73 % der Verzweigungen durch Tests abgedeckt (Abschnitt 4.8). Der Demonstrator aus Abschnitt 4.7 führt die wichtigsten Anwendungsfälle in der Swagger-UI vor.

Auch die zweite Schwäche, die die Ausschreibung nennt, die schlechte Dokumentation, behebt das neue Backend auf drei Ebenen (Abschnitt 4.9): mit einer aus dem Code erzeugten API-Dokumentation, mit Javadoc-Kommentaren an allen Typen und öffentlichen Methoden und mit sieben Begleitdokumenten für Nutzung, Entwicklung und Betrieb, darunter eine Architekturdokumentation nach arc42. Wie die übrigen Teile von DESIGN lässt sich das Backend in wenigen Schritten lokal oder als Container installieren.

## 5.2 Beantwortung der Forschungsfrage

Die Forschungsfrage aus Abschnitt 1.4 hat zwei Teile. Der erste fragt, wie sich das Backend so migrieren lässt, dass Frontend und DaaS-Anwendung weiter funktionieren, der zweite, welche architektonischen Maßnahmen dabei die Wartbarkeit verbessern.

Zum ersten Teil zeigt die Arbeit, dass eine Migration ohne Änderungen an der fachlichen Logik der Clients möglich ist, wenn sie auf einer Rekonstruktion des Altsystems aus seinem Quellcode aufbaut. Entscheidend war dabei nicht, jeden alten Endpunkt nachzubilden. Entscheidend war, genau die Verträge exakt nachzubilden, auf die sich die Clients tatsächlich stützen: den Token-Endpunkt mit Password Grant, die Sitzungsabfrage, die Verwaltungsendpunkte des Frontends und vor allem die Berechtigungsabfrage der DaaS-Anwendung mit ihrer vierstufigen Kaskade. Ganz ohne Anpassung der Clients ging es allerdings nicht. Im Frontend werden Konten nun mit PATCH aktiviert und deaktiviert, und ein Logout ist hinzugekommen. In der DaaS-Anwendung wertet die Anbindung die Sitzungsantwort etwas umfassender aus (Abschnitt 1.5). Diese Änderungen sind klein und betreffen nur die Anbindung an das Backend. Die Antwort auf den ersten Teil lautet daher: Die Migration gelingt mit wenigen, klar abgegrenzten Anpassungen an der Anbindung der Clients, wenn vorher vollständig erhoben wird, welche Teile der Schnittstelle sie tatsächlich nutzen.

Zum zweiten Teil lässt sich die bessere Wartbarkeit auf drei Maßnahmen zurückführen, die sich ergänzen. Erstens ist der Code nach fachlichen Modulen gegliedert, die alle gleich geschichtet sind (Abschnitt 4.1.2). Zweitens ist diese Struktur nicht bloß dokumentiert: ArchUnit prüft sie bei jedem Build, während das alte Backend mit PHPat nur einzelne Namensräume voneinander abschirmt (Abschnitt 4.1.3). Drittens sind Datenbankschema und Testabdeckung über Flyway-Migrationen und die JaCoCo-Schranke an den Build gebunden (Abschnitte 4.1.5 und 4.8.1). Hinzu kommen Maßnahmen, die das System leichter verständlich machen, etwa die aus dem Code erzeugte OpenAPI-Dokumentation und die klar festgelegte Konfliktregel der Berechtigungskaskade. Tabelle 4.4 zeigt, dass diese Maßnahmen über den Stand des Altsystems hinausgehen. Einschränkend ist festzuhalten, dass die Wartbarkeit hier an strukturellen Merkmalen gemessen wird. Wie hoch der Wartungsaufwand über einen längeren Zeitraum tatsächlich ist, ließ sich im Rahmen der Arbeit nicht messen.

Ordnet man diese Maßnahmen den Teilmerkmalen der Wartbarkeit nach ISO/IEC 25010 zu (Abschnitt 2.5.3), ergibt sich folgendes Bild. Die **Modularität** stützen die Gliederung in vier fachliche Module und die geprüften Abhängigkeitsregeln; eine Prüfung auf zyklische Abhängigkeiten zwischen den Modulen fehlt noch. Die **Analysierbarkeit** profitiert von den vollständigen Javadoc-Kommentaren, den Begleitdokumenten und der klar festgelegten Berechtigungskaskade (Abschnitt 4.9). Die **Modifizierbarkeit** wird dadurch gestützt, dass sich das Schema nur über versionierte Migrationen ändert und Verstöße gegen die Architektur den Build scheitern lassen. Die **Testbarkeit** zeigt sich in der Injektion über den Konstruktor, die den Einsatz von Mocks erlaubt, und in der geprüften Mindestabdeckung. Zur **Wiederverwendbarkeit** trägt die Arbeit dagegen kaum bei, da das Backend eine einzelne, auf DESIGN zugeschnittene Anwendung ist.

## 5.3 Abgleich mit den Anforderungen aus Kapitel 3

Tabelle 5.1 und Tabelle 5.2 zeigen für jede Anforderung aus Abschnitt 3.2, ob und wo sie umgesetzt wurde. Unterschieden wird zwischen erfüllt, teilweise erfüllt und nicht umgesetzt.

*Tabelle 5.1: Erfüllungsgrad der funktionalen Anforderungen*

| **ID** | **Anforderung (Kurzform)** | **Status** | **Nachweis** |
|---|---|---|---|
| FA-1 | Login liefert Zugriffstoken | erfüllt | 4.2.2; AF-1; TokenControllerIntegrationTest |
| FA-2 | Erneuerung per Refresh-Token | erfüllt | 4.2.4; AF-2 |
| FA-3 | Logout widerruft Token serverseitig | erfüllt | 4.2.5; AF-3 |
| FA-4 | Session/Identität abfragbar | erfüllt | 4.2.5; OAuth2SessionControllerIntegrationTest |
| FA-5 | Selbstregistrierung mit E-Mail-Bestätigung | erfüllt | 4.4.1; AF-4 |
| FA-6 | Admin verwaltet Nutzer | teilweise | 4.4.2; Einzelabruf eines Nutzers entfällt (Tabelle 3.5) |
| FA-7 | Admin verwaltet Admin-Konten | teilweise | 4.4.2; Anlegen und Auflisten umgesetzt, Löschen entfällt |
| FA-8 | Nutzergruppen verwalten und verknüpfen | erfüllt | 4.4.3; 4.5.2; AF-5 |
| FA-9 | Anwendungsfreischaltung anfragen | erfüllt | 4.4.4; AF-8 |
| FA-10 | Desktops anlegen, einsehen, auflisten | erfüllt | 4.5.1; AF-6 |
| FA-11 | Desktop-Gruppen verwalten und verknüpfen | teilweise | 4.5.2; Einzelabruf einer Desktop-Gruppe entfällt |
| FA-12 | Desktop-Zugriff des Nutzers prüfen | nicht umgesetzt | von keinem Client genutzt (Tabelle 3.5) |
| FA-13 | Berechtigungsprüfung über vierstufige Kaskade | erfüllt | 4.3; AF-7; PermissionServiceTest |

Neun der dreizehn funktionalen Anforderungen sind vollständig umgesetzt. Bei FA-6, FA-7 und FA-11 fehlen einzelne Operationen, nämlich der Abruf einzelner Einträge und das Löschen, und FA-12 wurde nicht umgesetzt. In allen vier Fällen werden die betroffenen Endpunkte weder vom Frontend noch von der DaaS-Anwendung aufgerufen (Tabelle 3.5). Ihr Fehlen schränkt den Betrieb von DESIGN daher nicht ein. Es ist aber eine gewollte Abweichung von den Anforderungen, die aus dem Altsystem abgeleitet wurden, und muss berücksichtigt werden, falls die Clients später erweitert werden.

*Tabelle 5.2: Erfüllungsgrad der nicht-funktionalen Anforderungen*

| **ID** | **Anforderung (Kurzform)** | **Status** | **Nachweis** |
|---|---|---|---|
| NFA-1 | Fail-closed bei Unklarheit | erfüllt | 4.3.3; 4.6.1; PermissionServiceTest |
| NFA-2 | Stufenreihenfolge und Default-Deny wie im Altsystem | erfüllt | 4.3.1; 4.3.2 |
| NFA-3 | Gleiche Pfade, Methoden, Felder, Statuscodes | teilweise | genutzte Endpunkte übernommen; Abweichungen in Tabelle 3.5 |
| NFA-4 | Passwort-Hashing und Kontosperrung | erfüllt (mit Einschränkung) | 4.2.2; 4.8.4 |
| NFA-5 | Ratenbegrenzung von Login und Registrierung | erfüllt | 4.2.6; 4.2.7 |
| NFA-6 | Serverseitig widerrufbare Zugriffstoken | erfüllt | 4.2.3 bis 4.2.5 |
| NFA-7 | Zustandslos und horizontal skalierbar | konzeptionell erfüllt | 3.4.3; 4.1.4; kein Lasttest |
| NFA-8 | Automatisiert geprüfte Schichtung | erfüllt | 4.1.3; 4.8.1 |
| NFA-9 | Erzwungene Mindest-Testabdeckung | teilweise | 4.8.1; 4.8.3; Merge-Sperre erfordert CI-Pipeline |
| NFA-10 | Dokumentation für Nutzung, Entwicklung und Betrieb | erfüllt | 4.6.5; 4.9 |
| NFA-11 | Einfache Installation | erfüllt | 4.7.1; README |

Sieben der elf nicht-funktionalen Anforderungen sind ohne Einschränkung erfüllt. NFA-3 ist nur teilweise erfüllt: Das neue Backend übernimmt zwar Pfade, Felder und Statuscodes der genutzten Endpunkte, verwendet beim Aktivieren und Deaktivieren aber PATCH statt POST und bietet ungenutzte Endpunkte nicht an. NFA-4 ist mit der in Abschnitt 4.8.4 beschriebenen Einschränkung der Kontosperre erfüllt. NFA-7 ist durch den zustandslosen Entwurf konzeptionell erfüllt, wurde aber nicht unter Last nachgewiesen. Bei NFA-9 scheitert der Build zuverlässig, wenn die Mindestabdeckung unterschritten wird. Damit dadurch auch das Übernehmen fehlerhafter Änderungen verhindert wird, braucht es eine CI-Pipeline, die nicht Teil des Repositorys ist.

Das Ergebnis lässt sich auch direkt an den sechs Arbeitspaketen der Ausschreibung messen (Baun, 2026). Tabelle 5.3 zeigt, dass alle Arbeitspakete bearbeitet wurden, und verweist auf die jeweiligen Ergebnisse.

*Tabelle 5.3: Ergebnisse zu den Arbeitspaketen der Ausschreibung*

| Arbeitspaket | Ergebnis | Abschnitt |
|---|---|---|
| AP1: Recherche des DESIGN-Gesamtsystems | Einordnung des Backends in Architektur und Komponenten von DESIGN | 2.1; 3.1.1 |
| AP2: Analyse des alten Backends in PHP | Rekonstruktion von Schnittstelle, Domänenmodell und Berechtigungskaskade; Analyse der tatsächlich genutzten Endpunkte | 3.1.2; 3.2 |
| AP3: Entwicklung eines neuen Backends | Zielarchitektur mit vier fachlichen Modulen und begründete Architekturentscheidungen | 3.4; 4.1 |
| AP4: Implementierung des neuen Backends | Spring-Boot-Dienst in Java 21 (Version 2.0.0) | 4.2 bis 4.6 |
| AP5: Aufbau eines Demonstrators | Demonstration der Anwendungsfälle über die Swagger-UI auf Basis des Docker-Images | 4.7 |
| AP6: Test, Evaluation und Dokumentation | Testsuite mit Build-Prüfungen, Anforderungsabgleich, API-, Code- und Begleitdokumentation | 4.8; 4.9; 5.3 |

## 5.4 Kritische Reflexion und Grenzen der Arbeit

Die Ergebnisse sind vor dem Hintergrund mehrerer Grenzen zu sehen. Einige folgen aus der Abgrenzung in Abschnitt 1.5, andere aus Entscheidungen während der Umsetzung.

**Anforderungen aus Reverse Engineering.** Alle Anforderungen wurden aus dem Quellcode des alten Backends und der Clients rekonstruiert. Sie geben deshalb das beobachtbare Verhalten des Altsystems wieder, nicht unbedingt das ursprünglich beabsichtigte. Entwurfsentscheidungen, die sich nicht im Code niederschlagen, konnten nicht erfasst werden, und Gespräche mit den Entwicklern des Altsystems fanden nicht statt.

**Verhaltenskompatibilität nur indirekt geprüft.** Abgesichert wurde die Kompatibilität über die Analyse der Clients, Integrationstests und den Demonstrator. Automatische Vertragstests, die dieselben Anfragen an altes und neues Backend stellen und die Antworten vergleichen, gibt es nicht. Abweichungen bei selten genutzten Feldern oder in Fehlerfällen lassen sich daher nicht ganz ausschließen. Für solche Charakterisierungstests im Sinne von Feathers (2004) hätte das alte Backend parallel in einer Testumgebung laufen müssen; das war im Zeitrahmen der Arbeit nicht vorgesehen.

**Ablösestrategie.** Das neue Backend ersetzt das alte auf einen Schlag und nicht schrittweise nach dem Strangler-Fig-Muster (Abschnitt 2.5.2). Das ist vertretbar, weil das Backend überschaubar ist und Frontend wie DaaS-Anwendung nur über REST angebunden sind. Es bedeutet aber, dass beim Umstieg alle Funktionen zugleich funktionieren müssen und ein Fehler nicht auf einzelne Endpunkte begrenzt bleibt. Für eine produktive Umstellung empfiehlt es sich daher, das alte Backend eine Zeit lang lauffähig zu halten, um bei Problemen zurückwechseln zu können.

**Pflege der Berechtigungsregeln.** Das alte Backend bot eigene HTTP-Endpunkte für die Zuordnung von Nutzergruppen zu Endpunktgruppen und einen Kommandozeilenbefehl für die Stammdaten der Endpunkte. Im neuen Backend werden diese Regeln derzeit direkt in der Datenbank gepflegt. Für den Betrieb genügt das, für Administratoren ist es aber unbequemer und fehleranfälliger.

**Kompromisse bei der Sicherheit.** Der Password Grant wurde aus Gründen der Kompatibilität beibehalten, obwohl RFC 9700 von ihm abrät (Lodderstedt et al., 2025). Bereits verwendete Refresh-Token werden zwar abgelehnt, eine Wiederverwendung führt aber nicht zum Widerruf aller zugehörigen Token. Die Kontosperre kann bei einem vergessenen Passwort zu wiederholten Sperren führen (Abschnitt 4.8.4), und die Standardzugangsdaten des ersten Administrators müssen vor einem produktiven Einsatz geändert werden (Abschnitt 4.4.5).

**Wahl der Programmiersprache.** Die Ausschreibung nennt Python an erster Stelle, lässt aber andere moderne Sprachen ausdrücklich zu (Baun, 2026). Java bringt die in Abschnitt 1.3 genannten Vorteile, hat aber einen Preis: Die DaaS-Anwendung ist in Python geschrieben, das Backend nun in Java. Wer DESIGN künftig pflegt, muss sich deshalb in zwei Umgebungen mit unterschiedlichen Werkzeugen und Bibliotheken auskennen. Ob die bessere Absicherung von Architektur und Sicherheit diesen Nachteil aufwiegt, wird sich erst über einen längeren Zeitraum der Wartung zeigen.

**Aktualität der Dokumentation.** Zum Zeitpunkt der Abgabe sind die Begleitdokumente vollständig und aktuell. Dass sie bei künftigen Änderungen mitgepflegt werden, schreibt das Entwicklerhandbuch zwar vor, anders als Architektur und Testabdeckung wird es aber nicht automatisch geprüft.

**Skalierung und Betrieb.** Dass das Backend horizontal skaliert, wurde nicht unter Last nachgewiesen. Außerdem hält die Ratenbegrenzung ihre Zähler im Arbeitsspeicher der jeweiligen Instanz, sodass die Limits bei mehreren Instanzen je Instanz gelten. Alle Instanzen müssen zudem dasselbe RSA-Schlüsselpaar verwenden; geht es verloren, werden alle ausgestellten Token ungültig.

**Aussagekraft der Tests.** Die Integrationstests laufen gegen H2 statt gegen MariaDB, sodass Unterschiede zwischen den Datenbanken unentdeckt bleiben können. Die Testabdeckung zeigt außerdem nur, welcher Code ausgeführt wird, nicht, ob jede Kombination von Eingaben richtig behandelt wird. Die niedrigere Abdeckung der Verzweigungen von 73 % deutet darauf hin, dass nicht jeder Sonderfall in beide Richtungen getestet ist.

## 5.5 Ausblick

Aus den genannten Grenzen ergeben sich folgende Ansatzpunkte für künftige Arbeiten:

- **Vertragstests mit den Clients:** Vertragstests, die von den Erwartungen der Clients ausgehen (Consumer-Driven Contracts), würden die Kompatibilität mit Frontend und DaaS-Anwendung dauerhaft und automatisch absichern.
- **Ablösung des Password Grants:** Mittelfristig sollte das Frontend auf den Authorization-Code-Grant mit PKCE umgestellt werden (Sakimura et al., 2015; Lodderstedt et al., 2025), etwa mit Spring Authorization Server als eigenem Autorisierungsserver. Mit OpenID Connect ließe sich dabei auch die Identität des Nutzers auf standardisierte Weise übermitteln.
- **Eigener Grant für die DaaS-Anwendung:** Die DaaS-Anwendung meldet sich derzeit mit dem Password Grant über ein technisches Administratorkonto an. Für die Kommunikation zwischen zwei Diensten ist jedoch der Client-Credentials-Grant gedacht (Abschnitt 2.2.1). Er käme ohne Nutzerpasswort aus und ließe sich auf die Berechtigungsabfrage beschränken.
- **Verwaltung der Berechtigungsregeln:** Eine Schnittstelle, über die sich Endpunkte, Endpunktgruppen und Zugriffsregeln pflegen lassen, gegebenenfalls mit Import aus einer Datei, würde die direkte Pflege in der Datenbank ablösen.
- **Sicherheit der Token:** Wiederverwendete Refresh-Token zu erkennen und dann alle zugehörigen Token zu widerrufen, sowie Schlüssel planmäßig über ein JSON Web Key Set mit kid-Kennung zu wechseln (Jones, 2015b), würde das Backend besser gegen gestohlene Token und verlorene Schlüssel schützen.
- **Skalierung:** Eine Ratenbegrenzung mit gemeinsamem Zustand für alle Instanzen und ein Lasttest mit mehreren Instanzen hinter einem Load Balancer sollten einem produktiven Einsatz vorausgehen.
- **Kontosperre:** Statt den Zähler für Fehlversuche nur bei erfolgreicher Anmeldung zurückzusetzen, könnte er nach Ablauf der Sperre schrittweise sinken, oder die Sperrdauer könnte schrittweise steigen.
- **Dokumentation absichern:** Eine Prüfung der Javadoc-Kommentare im Build, etwa mit dem doclint-Modus des Javadoc-Werkzeugs, und eine Prüfung der Links in den Begleitdokumenten würden verhindern, dass die Dokumentation unbemerkt veraltet.
- **Qualitätssicherung:** Integrationstests gegen eine MariaDB-Instanz im Container, zusätzliche Tests für die Randfälle der Berechtigungskaskade und eine CI-Pipeline, die fehlerhafte Änderungen blockiert, würden die bestehenden Prüfungen im Build sinnvoll ergänzen.

## 5.6 Schlussbetrachtung

Die Arbeit hat gezeigt, dass sich das Backend von DESIGN von PHP nach Java und Spring Boot migrieren lässt, ohne die fachliche Logik von Frontend und DaaS-Anwendung zu verändern. Voraussetzung war, sowohl das Altsystem als auch die tatsächlich genutzte Schnittstelle sorgfältig aus dem Quellcode zu rekonstruieren. Zugleich wurde die Neuentwicklung genutzt, um die beiden Schwächen zu beheben, die die Ausschreibung am alten Backend nennt. Die Qualität ist dauerhaft abgesichert, weil Architekturregeln und Testabdeckung nicht mehr allein von der Sorgfalt der Entwickler abhängen, sondern bei jedem Build geprüft werden. Die Dokumentation deckt Nutzung, Weiterentwicklung und Betrieb ab, und die Beschreibung der API entsteht direkt aus dem Code. Alle sechs Arbeitspakete der Ausschreibung wurden bearbeitet. Die genannten Grenzen zeigen aber auch, dass diese Arbeit ein tragfähiger, jedoch noch nicht abgeschlossener Schritt in der Weiterentwicklung von DESIGN ist.
