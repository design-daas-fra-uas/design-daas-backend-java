# 1 Einführung

Arbeitsumgebungen, die in der Cloud laufen und im Browser bedient werden, gewinnen an Bedeutung: Anwendungen lassen sich damit von jedem Gerät aus nutzen, ohne sie lokal zu installieren. Das System DESIGN verfolgt mit seinem Desktop-as-a-Service-Ansatz (DaaS) das Ziel, die Vorteile beider Welten zu verbinden (Baun & Bouché, 2023). Von lokal installierter Software übernimmt es Datenschutz, die Unterstützung von Altanwendungen und die Unabhängigkeit von einzelnen Anbietern, von webbasierter Software den geräteunabhängigen Zugriff ohne Installation. DESIGN besteht aus drei eigenständigen Teilen, die jeweils in einem eigenen, quelloffenen Repository gepflegt werden: der **DaaS-Anwendung**, die Container, virtuelle Maschinen, Guacamole und Proxmox verwaltet, dem **Frontend** und dem **Backend** (Baun, 2026). Das Projekt, in dem DESIGN entstanden ist, ist inzwischen abgeschlossen (Baun, 2026). Die Ausschreibung bezeichnet DaaS-Anwendung und Frontend als Python-Anwendungen. Ein Blick in die Repositories zeigt jedoch, dass nur die DaaS-Anwendung in Python geschrieben ist; das Frontend ist eine React-Anwendung in JavaScript. Diese Arbeit geht vom tatsächlichen Stand der Repositories aus.

## 1.1 Motivation

Laut Aufgabenstellung ist das bestehende Backend von DESIGN in **PHP** geschrieben, „qualitativ schwach“ und auch „schlecht“ dokumentiert (Baun, 2026). DaaS-Anwendung und Frontend sind nach derselben Quelle dagegen qualitativ gut, relativ einfach zu installieren und ausreichend gut dokumentiert. Gerade das Backend ist jedoch für Authentifizierung, Autorisierung sowie die Verwaltung von Nutzern und Desktops im gesamten System zuständig. Fehler oder unklares Verhalten an dieser Stelle wirken sich deshalb auf alle anderen Teile aus. Hinzu kommt, dass das ursprüngliche Projekt abgeschlossen ist. Es gibt also kein Entwicklungsteam mehr, das das Verhalten des Backends aus eigener Kenntnis erklären könnte. Ob DESIGN weiterentwickelt werden kann, hängt damit unmittelbar davon ab, wie verständlich Code und Dokumentation des Backends sind.

## 1.2 Problemstellung

Die Ausschreibung verlangt, ein neues Backend für DESIGN in Python oder einer anderen modernen Programmiersprache zu entwickeln und zu implementieren (Baun, 2026). Es soll die Funktionen des alten PHP-Backends übernehmen. Zu Frontend und DaaS-Anwendung macht die Ausschreibung keine Vorgaben. Weil beide nach ihrer Einschätzung qualitativ gut sind, wird in dieser Arbeit festgelegt, sie möglichst unverändert weiterzuverwenden. Das neue Backend muss deshalb das erfüllen, was beide bisher vom Backend erwarten: dieselben Endpunkte, dieselben Feldformate und dasselbe Verhalten bei der Berechtigungsprüfung. Dazu muss das Verhalten des alten Backends zunächst verstanden und festgehalten werden, bevor es neu umgesetzt werden kann. Die schlechte Dokumentation des Altsystems macht genau diesen Schritt schwierig.

## 1.3 Zielsetzung

Ziel dieser Arbeit ist es, ein neues Backend für DESIGN auf Basis von **Java 21** und **Spring Boot** zu entwickeln und zu implementieren. Es soll fachlich dasselbe leisten wie das alte PHP-Backend und dessen wichtigste Eigenschaften beibehalten, vor allem die mehrstufige Berechtigungskaskade. Zugleich soll es die beiden Schwächen beheben, die die Ausschreibung nennt. Die Qualität soll durch automatisch geprüfte Architekturregeln und eine Mindest-Testabdeckung dauerhaft gesichert werden, und das Backend soll für Nutzung, Weiterentwicklung und Betrieb vollständig dokumentiert sein.

Die Ausschreibung lässt offen, ob Python oder eine andere moderne Programmiersprache verwendet wird (Baun, 2026). Die Wahl fiel aus vier Gründen auf Java 21 mit Spring Boot:

- **Sicherheitsbausteine:** Authentifizierung und Autorisierung sind die Kernaufgaben des Backends. Spring Security bietet dafür ausgereifte Bausteine, die sich an die einschlägigen Standards halten, etwa für die Rolle des OAuth2-Ressourcenservers und die Prüfung signierter JSON Web Token (Abschnitt 2.2.5).
- **Statische Typisierung:** Der Compiler erkennt viele Fehler schon vor der Ausführung. Ein Beispiel ist die vollständige Fallunterscheidung über versiegelte Typen beim Token-Endpunkt (Abschnitt 4.2.1).
- **Werkzeuge für die Qualitätssicherung:** Mit ArchUnit und JaCoCo gibt es etablierte Werkzeuge, die Architekturregeln und Testabdeckung im Build prüfen. Sie setzen damit direkt an der Qualität an, die die Ausschreibung bemängelt (Abschnitte 2.4.7 und 2.4.9).
- **Langfristige Wartung:** Java 21 ist eine Version mit Langzeitunterstützung (Long-Term Support, LTS). Für ein Projekt, dessen ursprüngliche Entwicklung abgeschlossen ist, ist das ein Vorteil.

Eine Umsetzung in Python, etwa mit Django oder FastAPI, wäre ebenfalls möglich gewesen. Sie hätte den Vorteil gehabt, dass Backend und DaaS-Anwendung in derselben Sprache geschrieben sind. Auf diesen Nachteil der gewählten Lösung geht Abschnitt 5.4 ein.

## 1.4 Forschungsfrage

Aus Aufgabenstellung und Problemstellung ergibt sich folgende Forschungsfrage:

„Wie lässt sich das Backend des Desktop-as-a-Service-Systems DESIGN von PHP nach Java und Spring Boot migrieren, sodass Frontend und DaaS-Anwendung ohne Änderungen an ihrer fachlichen Logik funktionsfähig bleiben, und welche architektonischen Maßnahmen verbessern dabei die Wartbarkeit des Backends gegenüber dem bisherigen Stand?“

Die Frage hat zwei Teile. Kapitel 3 analysiert das bestehende PHP-Backend und leitet daraus die funktionalen und nicht-funktionalen Anforderungen an das neue Backend ab. Kapitel 4 beschreibt, wie diese Anforderungen mit Java und Spring Boot umgesetzt wurden und welche Maßnahmen die Wartbarkeit verbessern, etwa automatisch geprüfte Architekturregeln und eine Mindest-Testabdeckung. Kapitel 5 beantwortet die Frage auf dieser Grundlage.

## 1.5 Abgrenzung

Diese Arbeit beschränkt sich auf das **Backend**-Repository (*design-daas-backend-java*). Frontend (*design-daas-application-frontend*) und DaaS-Anwendung (*design-daas*) werden nicht weiterentwickelt; ihre bestehenden Schnittstellen gelten als feste Randbedingung. Teile, die zur DaaS-Anwendung oder zur Infrastruktur von DESIGN gehören, etwa Apache Guacamole, Proxmox VE oder das Starten und Beenden von Containern und virtuellen Maschinen, werden nicht implementiert. Sie werden nur so weit beschrieben, wie es nötig ist, um das Backend im Gesamtsystem einzuordnen (Abschnitte 2.1 und 3.1).

Ganz ohne Änderungen an den beiden Clients ging es allerdings nicht. Die nötigen Anpassungen sind klein und betreffen nur die Anbindung an das Backend. Im Frontend werden Nutzerkonten jetzt mit der HTTP-Methode PATCH aktiviert und deaktiviert, ein Logout über den neuen Endpunkt POST /oauth2/user/logout ist hinzugekommen, und einzelne fehlerhafte oder unübersichtliche Stellen wurden bereinigt, soweit die Anbindung das erforderte. In der DaaS-Anwendung wertet die Anbindung nun zusätzlich das Feld role der Sitzungsantwort aus, und eine nicht mehr benötigte Abfrage von POST /permissions_info wurde entfernt. Eine weitergehende Überarbeitung oder Erweiterung der beiden Clients ist nicht Ziel dieser Arbeit.

## 1.6 Methodisches Vorgehen

Das Vorgehen folgt den sechs Arbeitspaketen, die die Ausschreibung vorschlägt (Baun, 2026). Tabelle 1.1 zeigt, wo jedes davon in dieser Arbeit behandelt wird. AP3 („Entwicklung eines neuen Backends“) wird dabei als konzeptioneller Entwurf verstanden, der der Implementierung in AP4 vorausgeht.

*Tabelle 1.1: Arbeitspakete der Ausschreibung und ihre Behandlung in dieser Arbeit*

| Arbeitspaket (Ausschreibung) | Behandlung in dieser Arbeit |
|---|---|
| AP1: Recherche des DESIGN-Gesamtsystems | Abschnitt 2.1, Abschnitt 3.1.1 |
| AP2: Analyse des alten Backends in PHP | Abschnitt 3.1.2, Abschnitt 3.2 |
| AP3: Entwicklung eines neuen Backends | Abschnitt 3.4, Abschnitt 4.1 |
| AP4: Implementierung des neuen Backends | Abschnitte 4.2 bis 4.6 |
| AP5: Aufbau eines Demonstrators | Abschnitt 4.7 |
| AP6: Test, Evaluation und Dokumentation | Abschnitt 4.8 (Test), Abschnitt 4.9 (Dokumentation), Kapitel 5 (Evaluation) |

Methodisch handelt es sich um eine Anforderungsanalyse durch Reverse Engineering mit anschließender Neuimplementierung. Das Ergebnis wird auf drei Wegen bewertet: durch einen systematischen Abgleich mit den Anforderungen und Arbeitspaketen, durch automatisierte Tests und durch einen Demonstrator (Kapitel 5).

## 1.7 Aufbau der Arbeit

Kapitel 2 stellt die Grundlagen vor: das DaaS-Konzept von DESIGN, die Authentifizierung mit OAuth2 und JWT, Modelle der Autorisierung, die verwendeten Spring-Technologien sowie die Migration von Altsystemen und den Begriff der Wartbarkeit. Kapitel 3 analysiert Ist- und Zielzustand des Backends und leitet daraus die Anforderungen ab. Kapitel 4 beschreibt die Umsetzung der einzelnen Module (Authentifizierung, Autorisierung, Benutzerverwaltung, Desktop-Inventar), die übergreifenden Sicherheits- und Qualitätsmaßnahmen sowie Demonstrator, Tests und Dokumentation. Kapitel 5 fasst die Ergebnisse zusammen, beantwortet die Forschungsfrage, gleicht das Ergebnis mit den Anforderungen ab, benennt die Grenzen der Arbeit und gibt einen Ausblick.
