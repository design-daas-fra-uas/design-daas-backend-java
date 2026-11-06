# 5 Zusammenfassung und Ausblick

Dieses abschließende Kapitel fasst die Ergebnisse der Arbeit zusammen (5.1), beantwortet die in Kapitel 1
formulierte Forschungsfrage explizit (5.2), gleicht das realisierte Backend systematisch gegen die in
Kapitel 3 abgeleiteten funktionalen und nicht-funktionalen Anforderungen ab (5.3), benennt die Grenzen der
Arbeit (5.4) und gibt einen Ausblick auf mögliche Weiterentwicklungen (5.5), bevor ein kurzes Fazit das
Kapitel und damit die Arbeit schließt (5.6).

## 5.1 Zusammenfassung der Ergebnisse

Ausgangspunkt dieser Arbeit war ein als „qualitativ schwach" und „schlecht dokumentiert" beschriebenes
PHP-Backend des Desktop-as-a-Service-Systems DESIGN, dessen Funktionalität durch ein neues Backend auf Basis
von Java 21 und Spring Boot ersetzt werden sollte, ohne Frontend oder DaaS-Anwendung zu verändern. Kapitel 3
hat dazu zunächst das Verhalten des alten Backends durch Quellcode-Analyse rekonstruiert und daraus 13
funktionale (FA-1 bis FA-13) sowie 9 nicht-funktionale Anforderungen (NFA-1 bis NFA-9) abgeleitet. Kapitel 4
hat beschrieben, wie das neue Backend diese Anforderungen in vier fachlichen Modulen (`auth`, `permission`,
`user`, `desktop`) umsetzt, welche Architekturentscheidungen dabei getroffen wurden (4.1) und welche
querschnittlichen Sicherheits- und Qualitätsmaßnahmen (4.6) ergänzt wurden. Abschnitt 4.8 hat diese
Umsetzung schließlich anhand einer Testsuite aus 30 Testklassen mit 247 Testmethoden und einer tatsächlich
erreichten Zeilenabdeckung von 87 % überprüft.

Im Ergebnis liegt ein eigenständiger, über Docker paketierter Spring-Boot-Dienst vor, der die vier
Funktionsbereiche des alten Backends unter denselben Pfaden, HTTP-Methoden und JSON-Feldformaten anbietet
(NFA-3) und dessen zentrale Verhaltenseigenschaft – die 4-stufige, fail-closed Berechtigungskaskade – aus
dem alten `PermissionCalculationService` unverändert übernimmt (NFA-1, NFA-2), an einer dokumentierten
Stelle (gleichrangige, widersprüchliche Regeln innerhalb einer Stufe) jedoch um eine deterministische
Konfliktauflösung ergänzt, die im alten Backend unspezifiziert war (Abschnitt 3.4.2, 4.3.2). Gleichzeitig
adressiert das neue Backend die in der Ausschreibung konstatierten Mängel durch zwei automatisierte,
builtgebundene Qualitätsschranken, die im alten Backend so nicht existierten: eine ArchUnit-Testklasse mit
19 Regeln zur internen Schichtung (NFA-8) und eine JaCoCo-Mindestabdeckung von 70 % (NFA-9), beide
verbindlicher Bestandteil von `mvn verify`. Der in Abschnitt 4.7 beschriebene, aus dem unveränderten
Produktionsartefakt betriebene Demonstrator hat gezeigt, dass alle acht in Abschnitt 3.5 definierten
Anwendungsfälle gegen die generierte Swagger-UI ausführbar sind.

## 5.2 Beantwortung der Forschungsfrage

Die in Kapitel 1 gestellte Forschungsfrage lautete, wie sich das Backend von DESIGN von PHP nach Java und
Spring Boot migrieren lässt, sodass Frontend und DaaS-Anwendung ohne Anpassung funktionsfähig bleiben, und
welche architektonischen Maßnahmen dabei die Wartbarkeit gegenüber dem bisherigen Stand verbessern. Beide
Teilfragen lassen sich anhand der Kapitel 3 und 4 wie folgt beantworten:

Die **Migrationsfähigkeit ohne Anpassung der Clients** wird durch die Kombination aus Reverse-Engineering-
Anforderungsanalyse (Kapitel 3.1–3.2) und 1:1-Übernahme der nach außen sichtbaren Schnittstelle
(Pfade, Felder, Statuscodes, Kaskadenpriorität) erreicht: Da jede FA aus Abschnitt 3.2.2 unmittelbar aus
einem beobachteten Endpunkt des alten Backends abgeleitet wurde und NFA-3 deren Beibehaltung explizit
fordert, bestand zu keinem Zeitpunkt der Implementierung ein Freiheitsgrad, der Frontend oder
DaaS-Anwendung zu Anpassungen gezwungen hätte. Der Demonstrator aus Abschnitt 4.7 bestätigt dies empirisch
anhand der acht Anwendungsfälle aus Abschnitt 3.5.

Die **Verbesserung der Wartbarkeit** wird nicht durch eine einzelne Maßnahme, sondern durch das
Zusammenwirken mehrerer, in Kapitel 4 begründeter Architekturentscheidungen erreicht: eine nach fachlicher
Domäne gegliederte, intern einheitlich geschichtete Modulstruktur (4.1.2), deren Schichtung erstmals –
anders als im alten Backend, das mit `PHPat` nur die Modulgrenzen, nicht aber die interne
Controller-Service-Repository-Schichtung prüft – automatisiert durch ArchUnit erzwungen wird (4.1.3),
sowie eine über Flyway versionierte, reviewbare Schemaverwaltung anstelle impliziter Hibernate-Auto-DDL
(4.1.5). Die in Abschnitt 4.8.5 vorgenommene, quellcodebasierte Gegenüberstellung beider Backends (Tabelle
4.4) macht diese Verbesserung messbar: Während das alte Backend über keine im Build erzwungene
Testabdeckung verfügt, erzwingt das neue Backend 70 % und erreicht tatsächlich 87 %; während das alte
Backend nur die Modulgrenzen, nicht aber die interne Schichtung architektonisch prüft, prüft das neue
Backend beides. Die Forschungsfrage ist damit sowohl konzeptionell (Kapitel 3/4) als auch empirisch
(Demonstrator, Testsuite, JaCoCo-Bericht) beantwortet.

## 5.3 Abgleich mit den Anforderungen aus Kapitel 3

Tabelle 5.1 und 5.2 stellen den Erfüllungsgrad jeder einzelnen Anforderung aus Abschnitt 3.2 fest und
verweisen auf die jeweilige Realisierungsstelle in Kapitel 4. Dieser Abgleich bildet die Grundlage für die
in Abschnitt 5.4 benannten Grenzen der Arbeit: Jede dort beschriebene Einschränkung lässt sich auf genau
eine Zeile dieser beiden Tabellen zurückführen.

*Tabelle 5.1: Erfüllungsgrad der funktionalen Anforderungen*

| ID    | Anforderung (Kurzform)                              | Erfüllt | Nachweis                                  |
|-------|------------------------------------------------------|:-------:|--------------------------------------------|
| FA-1  | Login liefert Zugriffstoken                          | ✅      | 4.2.2, AF-1 (3.5), Demonstrator 4.7.2      |
| FA-2  | Refresh-Token erneuert Zugriffstoken                  | ✅      | 4.2.4, AF-2 (3.5), Demonstrator 4.7.2      |
| FA-3  | Logout widerruft Token serverseitig                   | ✅      | 4.2.5, AF-3 (3.5), Demonstrator 4.7.2      |
| FA-4  | Session/Identität zu gültigem Token abfragbar         | ✅      | 4.2.5 (Session-Introspektion)              |
| FA-5  | Selbstregistrierung mit E-Mail-Bestätigung            | ✅      | 4.4.1, AF-4 (3.5), Demonstrator 4.7.2      |
| FA-6  | Admin verwaltet Nutzer                                | ✅      | 4.4.2, AF-5 (3.5)                          |
| FA-7  | Admin verwaltet weitere Admin-Konten                  | ✅      | 4.4.2, AF-5 (3.5)                          |
| FA-8  | Admin verwaltet Nutzergruppen und Desktop-Verknüpfung | ✅      | 4.4.3, AF-5/AF-6 (3.5)                     |
| FA-9  | Anwendungsfreischaltung mit Admin-Benachrichtigung    | ✅      | 4.4.4, AF-8 (3.5), Demonstrator 4.7.2      |
| FA-10 | Admin verwaltet Desktops                              | ✅      | 4.5.1, AF-6 (3.5), Demonstrator 4.7.2      |
| FA-11 | Admin verwaltet Desktop-Gruppen und Verknüpfung       | ✅      | 4.5.2, AF-6 (3.5), Demonstrator 4.7.2      |
| FA-12 | Nutzer prüft Desktop-Zugriff                          | ✅      | 4.5.2                                      |
| FA-13 | Berechtigungsprüfung über 4-stufige Kaskade           | ✅      | 4.3, AF-1/AF-7 (3.5), Demonstrator 4.7.2   |

Alle 13 funktionalen Anforderungen sind vollständig umgesetzt und — mit Ausnahme von FA-4 und FA-12, die
keinen eigenen Platz in der Demonstrator-Vorführung erhalten haben, da sie keine eigenständigen
Anwendungsfälle, sondern unterstützende Abfragen innerhalb anderer Abläufe sind — durch die Testsuite aus
Abschnitt 4.8.2 (Integrationstests) und zusätzlich durch den Demonstrator aus Abschnitt 4.7 belegt.

*Tabelle 5.2: Erfüllungsgrad der nicht-funktionalen Anforderungen*

| ID    | Anforderung (Kurzform)                                   | Erfüllt | Nachweis                                              |
|-------|------------------------------------------------------------|:-------:|---------------------------------------------------------|
| NFA-1 | Fail-closed bei Unklarheit                                  | ✅      | 4.3.3, `AuthHardeningIntegrationTest` (4.8.2)          |
| NFA-2 | Gleiche Stufenpriorität wie Altsystem                       | ✅      | 4.3.1, `PermissionServiceTest` (4.8.2)                 |
| NFA-3 | Identische Pfade/Felder/Statuscodes                         | ✅      | 4.1.2, 4.8.5 (Tabelle 4.4)                             |
| NFA-4 | Passwort-Hashing, Kontosperrung                             | ✅ (mit Einschränkung) | 4.2.2, 4.8.4                             |
| NFA-5 | Ratenbegrenzung von Login/Registrierung                     | ✅      | 4.2.6–4.2.7                                            |
| NFA-6 | Serverseitig widerrufbare Zugriffstoken                     | ✅      | 3.4.2, 4.2.3–4.2.5                                     |
| NFA-7 | Zustandslosigkeit, horizontale Skalierbarkeit               | ✅      | 3.4.3, 4.1.4                                           |
| NFA-8 | Automatisiert geprüfte Schichtung                           | ✅      | 4.1.3, 4.8.1, 4.8.5 (Tabelle 4.4)                      |
| NFA-9 | Erzwungene Mindest-Testabdeckung                            | ✅      | 4.8.1, 4.8.3 (87 % bei Mindestwert 70 %)               |

Acht der neun nicht-funktionalen Anforderungen sind ohne Einschränkung erfüllt. NFA-4 ist mit der in
Abschnitt 4.8.4 dokumentierten Einschränkung erfüllt: Die Kontosperrung selbst greift zuverlässig, der
Fehlversuchszähler wird jedoch ausschließlich bei erfolgreichem Login zurückgesetzt, nicht durch Ablauf der
Sperrzeit, was bei einem tatsächlich vergessenen Passwort zu wiederholten 15-minütigen Sperrfristen führen
kann. Diese Einschränkung war eine bewusste Entscheidung zugunsten von Fail-closed-Verhalten gegenüber
Benutzerfreundlichkeit (Abschnitt 4.8.4) und wird in Abschnitt 5.5 als Ansatzpunkt für künftige Arbeiten
wieder aufgegriffen.

## 5.4 Grenzen der Arbeit

Die folgenden Grenzen ergeben sich unmittelbar aus der in Abschnitt 1.5 (Abgrenzung) festgelegten
Scope-Entscheidung sowie aus den in Abschnitt 5.3 benannten Einschränkungen und sind daher nicht als
unentdeckte Mängel, sondern als bewusst gesetzte Grenzen der Untersuchung zu verstehen:

1. **Kein Eingriff in Bereitstellung, Frontend oder DaaS-Anwendung.** Diese Arbeit implementiert
   ausschließlich das Backend; die eigentliche Desktop-Bereitstellung (Container-/VM-Lebenszyklus, Apache
   Guacamole, Proxmox) bleibt unverändert Aufgabe der Python-DaaS-Anwendung (Abschnitt 1.5). Eine
   Aussage über deren interne Qualität oder Wartbarkeit kann diese Arbeit nicht treffen.
2. **Reverse-Engineering statt Anforderungen aus Stakeholder-Interviews.** Da alle Anforderungen aus
   Abschnitt 3.2 durch Quellcode-Analyse des alten Backends rekonstruiert wurden (Abschnitt 3.2), spiegeln
   sie dessen *beobachtbares* Verhalten wider, nicht notwendigerweise dessen *ursprünglich beabsichtigtes*
   Verhalten. Undokumentierte Entwurfsentscheidungen des alten Backends, die sich nicht im Code
   niederschlagen, konnten dadurch nicht erfasst werden.
3. **Eine bewusst in Kauf genommene Verhaltenseinschränkung bei der Kontosperrung** (NFA-4, Abschnitt 4.8.4):
   Der Fehlversuchszähler wird nicht durch Ablauf der Sperrzeit zurückgesetzt, wodurch sich wiederholte
   Fehlversuche nach Sperrablauf zu einer gefühlten Dauersperre summieren können.
4. **Demonstrator statt Produktivbetrieb.** Der in Abschnitt 4.7 beschriebene Demonstrator läuft gegen eine
   lokale H2- bzw. MariaDB-Instanz mit deaktiviertem Mailversand (`MAIL_ENABLED=false`) und belegt damit die
   funktionale Korrektheit, nicht jedoch Aussagen über Verhalten unter Produktionslast (z. B. gleichzeitige
   Zugriffe mehrerer hundert Nutzer), da ein entsprechender Lasttest nicht Teil des Arbeitsumfangs war.
5. **Testabdeckung als Indikator, nicht als Beweis der Korrektheit.** Die in Abschnitt 4.8.3 ausgewiesene
   Zeilenabdeckung von 87 % misst, welcher Code ausgeführt wird, nicht, ob jede denkbare Kombination von
   Eingaben korrekt behandelt wird; insbesondere die im Vergleich niedrigere Verzweigungsabdeckung von 73 %
   (Abschnitt 4.8.3) zeigt, dass nicht jeder Sonderfall der Berechtigungskaskade in beiden Richtungen
   getestet ist.

## 5.5 Ausblick

Aus den in Abschnitt 5.4 benannten Grenzen sowie aus während der Realisierung identifizierten, aber
bewusst außerhalb des Arbeitsumfangs belassenen Ideen ergeben sich folgende Ansatzpunkte für künftige
Arbeiten:

- **Schrittweiser Abbau des Fehlversuchszählers.** Statt den in Abschnitt 4.8.4 beschriebenen
  Fehlversuchszähler ausschließlich bei erfolgreichem Login vollständig zurückzusetzen, könnte
  `AccountLockoutService` ihn nach Ablauf der Sperrzeit schrittweise reduzieren (etwa um einen Versuch je
  abgelaufenem Zeitfenster) statt ihn bei einem erneuten Fehlversuch sofort wieder auf den Schwellenwert
  springen zu lassen. Dies würde die in 5.4 benannte gefühlte Dauersperre entschärfen, ohne das
  Fail-closed-Prinzip (NFA-1) aufzugeben.
- **Erhöhung der Verzweigungsabdeckung gezielter Fail-closed-Pfade.** Abschnitt 4.8.3 weist eine
  Verzweigungsabdeckung von 73 % gegenüber 87 % Zeilenabdeckung aus. Eine gezielte Ergänzung der Testsuite
  um Randfälle der Berechtigungskaskade (4.3) und der Fehlerbehandlung (4.6.2) könnte diese Lücke
  schließen, ohne die Architektur selbst zu verändern.
- **Lasttest vor Produktivbetrieb.** Da Zustandslosigkeit und horizontale Skalierbarkeit (NFA-7) bislang nur
  konzeptionell (3.4.3) und nicht empirisch unter Last nachgewiesen wurden, wäre ein Lasttest mit mehreren
  parallelen Instanzen hinter einem Load Balancer ein naheliegender nächster Schritt, bevor das Backend den
  produktiven PHP-Dienst ablöst.
- **Rotation und Fallback des RSA-Schlüsselpaars.** Abschnitt 3.4.2 weist darauf hin, dass ein Verlust des
  beim ersten Start erzeugten RSA-Schlüsselpaars sämtliche zuvor ausgestellten Tokens entwertet. Ein
  Mechanismus zur geplanten Schlüsselrotation (z. B. über mehrere gleichzeitig gültige Schlüssel mit
  `kid`-Claim, vgl. JWK-Standard [1]) würde diesen Single Point of Failure entschärfen.
- **Migration weiterer PHP-Altlasten.** Diese Arbeit beschränkt sich auf das Backend-Repository (Abschnitt
  1.5); eine naheliegende Fortsetzung wäre, die hier etablierten Qualitätsschranken (ArchUnit, JaCoCo) als
  Vorlage für eine schrittweise Qualitätsverbesserung der verbleibenden, nicht migrierten Teile des
  Gesamtsystems DESIGN zu nutzen, sofern sich künftige Arbeiten dafür entscheiden.

## 5.6 Fazit

Diese Arbeit hat gezeigt, dass sich das Backend eines produktiv eingesetzten Desktop-as-a-Service-Systems
von PHP nach Java und Spring Boot migrieren lässt, ohne Frontend oder DaaS-Anwendung anzupassen, sofern die
Migration auf einer sorgfältigen, quellcodebasierten Rekonstruktion der bestehenden Schnittstelle und
Verhaltenslogik aufbaut (Kapitel 3). Gleichzeitig hat sie belegt, dass eine solche Migration genutzt werden
kann, um dokumentierte Qualitätsmängel des Altsystems nicht nur zu beheben, sondern durch automatisierte,
builtgebundene Schranken dauerhaft abzusichern (Kapitel 4): Architektur und Testabdeckung sind im neuen
Backend keine Frage der Disziplin einzelner Entwickler mehr, sondern eine vom Build selbst erzwungene
Eigenschaft. Die in Kapitel 1 gestellte Forschungsfrage ist damit sowohl konzeptionell als auch empirisch
beantwortet; die in Abschnitt 5.4 benannten Grenzen zeigen zugleich, dass diese Arbeit einen abgeschlossenen,
aber nicht den einzig möglichen Schritt in der Weiterentwicklung von DESIGN darstellt.

---

## Quellen in diesem Kapitel

```
[1] M. Jones, "JSON Web Key (JWK)," IETF, RFC 7517, May 2015.
```

Diese Quelle ist Teil des vollständigen Literaturverzeichnisses in Kapitel 2 (dort [28]). Alle übrigen
Aussagen dieses Kapitels fassen die in den Kapiteln 1, 3 und 4 bereits belegten Ergebnisse zusammen, ohne
neue, dort nicht bereits zitierte Quellen heranzuziehen.

## Offene Punkte zu diesem Kapitel

- [ ] Abgleich der Tabellen 5.1/5.2 nach Abschluss aller Testläufe final gegen den letzten Surefire-/
  JaCoCo-Bericht prüfen (aktuell auf Stand von Abschnitt 4.8 referenziert).
- [ ] Mit Erstprüfer Prof. Dr. Baun klären, ob ein zusätzlicher Abschnitt zur persönlichen Reflexion des
  methodischen Vorgehens (AP1–AP6) erwartet wird oder ob die vorliegende Form ausreicht.
