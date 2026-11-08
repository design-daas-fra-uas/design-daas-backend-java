# 1 Einführung

Cloud- und browserbasierte Arbeitsumgebungen gewinnen zunehmend an Bedeutung, weil sie einen geräteunabhängigen
Zugriff auf Anwendungen ermöglichen, ohne dass eine lokale Installation nötig ist. Das System **DESIGN**
verfolgt mit seinem Desktop-as-a-Service-Ansatz (DaaS) das Ziel, die Vorteile lokal installierter Anwendungen
(Datenschutz, Unterstützung von Legacy-Software, kein Vendor-Lock-in) mit denen webbasierter Software
(geräteunabhängiger Zugriff über den Browser, keine Installation) zu verbinden [1], [2]. DESIGN gliedert sich
in drei eigenständige Teile: die **DaaS-Anwendung** (Python, verwaltet Container, virtuelle Maschinen,
Guacamole und Proxmox), das **Frontend** (JavaScript, React) und das **Backend**.

## Motivation

Laut Aufgabenstellung dieser Arbeit ist das bestehende Backend von DESIGN in **PHP** implementiert und dabei
„qualitativ schwach“ sowie „schlecht dokumentiert“. Das erschwert Wartung und Weiterentwicklung des
Gesamtsystems, obwohl die beiden anderen Teile, DaaS-Anwendung und Frontend, nach gleicher Quelle qualitativ
gut, gut dokumentiert und vergleichsweise einfach zu installieren sind. Das Backend bildet jedoch die
Grundlage für Authentifizierung, Autorisierung sowie Benutzer- und Desktop-Verwaltung des gesamten Systems.
Fehler oder eine mangelnde Nachvollziehbarkeit an dieser Stelle wirken sich daher auf alle anderen
Systemteile aus.

## Problemstellung

Aus der Ausschreibung ergibt sich die konkrete Aufgabe, ein neues Backend für DESIGN zu entwickeln und zu
implementieren, wahlweise in Python oder einer anderen modernen Programmiersprache, das die bestehende
Funktionalität des alten PHP-Backends ersetzt, ohne dabei Frontend oder DaaS-Anwendung zu verändern. Da
beide bestehenden Komponenten nicht Teil dieser Arbeit sind, muss das neue Backend deren bisherige
Schnittstellenerwartungen (Endpunkte, Feldformate, Verhalten der Berechtigungsprüfung) unverändert erfüllen.
Das setzt voraus, dass das Verhalten des alten Backends zunächst hinreichend verstanden und dokumentiert
wird, bevor es neu implementiert werden kann. Diese Aufgabe wird durch die mangelhafte Dokumentation des
Ist-Systems zusätzlich erschwert.

## Zielsetzung

Ziel dieser Arbeit ist die Entwicklung und Implementierung eines neuen Backends für DESIGN auf Basis von
**Java 21** und **Spring Boot**. Das neue Backend soll funktional äquivalent zum alten PHP-Backend sein und
dessen zentrale Verhaltenseigenschaften, insbesondere die mehrstufige Berechtigungskaskade, beibehalten.
Gleichzeitig sollen Wartbarkeit und Nachvollziehbarkeit gegenüber dem Ist-Zustand verbessert werden, unter
anderem durch automatisiert geprüfte Architekturregeln und eine Mindest-Testabdeckung.

## Forschungsfrage

Aus der Aufgabenstellung und der beschriebenen Problemstellung ergibt sich die folgende Forschungsfrage:

„Wie lässt sich das Backend des Desktop-as-a-Service-Systems DESIGN von PHP nach Java und Spring Boot
migrieren, sodass Frontend und DaaS-Anwendung ohne Anpassung funktionsfähig bleiben, und welche
architektonischen Maßnahmen verbessern dabei die Wartbarkeit des Backends gegenüber dem bisherigen Stand?“

Diese Frage wird in zwei Teilen beantwortet. Kapitel 3 analysiert das bestehende PHP-Backend und leitet
daraus die funktionalen und nicht-funktionalen Anforderungen an das neue Backend ab. Kapitel 4 beschreibt,
wie diese Anforderungen in Java und Spring Boot umgesetzt wurden und welche architektonischen Maßnahmen
(zum Beispiel automatisiert geprüfte Architekturregeln und eine Mindest-Testabdeckung) zur besseren
Wartbarkeit beitragen.

## Abgrenzung

Diese Arbeit beschränkt sich auf das **Backend**-Repository (`design-daas-backend-java`). Frontend
(`design-daas-application-frontend`) und DaaS-Anwendung beziehungsweise Engine (`design-daas`) werden nicht
verändert. Ihre bestehenden Schnittstellenverträge gelten als gegebene Randbedingung. Komponenten, die laut
Recherche (Kapitel 2) zur DaaS-Anwendung oder zur Infrastrukturebene von DESIGN gehören, etwa Apache
Guacamole, Proxmox VE oder der Container- und VM-Lebenszyklus, sind nicht Gegenstand der Implementierung
dieser Arbeit und werden nur insoweit behandelt, wie sie zur Einordnung des Backends in das Gesamtsystem
nötig sind (Kapitel 2.1, 3.1).

Am Frontend selbst wurden einzelne Stellen angepasst, weil dort Fehler vorlagen und der Code teilweise nicht
sauber strukturiert war. Diese Anpassungen beschränken sich bewusst auf den Teil des Codes, den das neue
Backend für eine korrekte Anbindung benötigt. Eine vollständige Überarbeitung, Refaktorierung oder
funktionale Erweiterung des Frontends ist nicht Ziel dieser Arbeit.

## Methodisches Vorgehen

Das Vorgehen orientiert sich an den in der Ausschreibung genannten Arbeitspaketen AP1–AP6: Recherche des
DESIGN-Gesamtsystems (AP1), Analyse des alten PHP-Backends (AP2), Entwurf (AP3) und Implementierung (AP4)
des neuen Backends, Aufbau eines Demonstrators (AP5) sowie Test, Evaluation und Dokumentation (AP6). Kapitel
3 dieser Arbeit leitet aus AP1/AP2 funktionale und nicht-funktionale Anforderungen ab; Kapitel 4 beschreibt
die auf AP3/AP4 aufbauende Realisierung komponentenweise; Kapitel 5 fasst AP6 zusammen.

## Aufbau der Arbeit

Kapitel 2 stellt die theoretischen Grundlagen zum DaaS-Konzept von DESIGN, zu OAuth2/JWT-basierter
Authentifizierung, zu Autorisierungsmodellen sowie zu den verwendeten Spring-Technologien dar. Kapitel 3
analysiert Ist- und Zielzustand des Backends und leitet daraus funktionale sowie nicht-funktionale
Anforderungen ab. Kapitel 4 beschreibt die Realisierung der einzelnen Backend-Module
(Authentifizierung, Autorisierung, Benutzerverwaltung, Desktop-Inventar) sowie die querschnittlichen
Sicherheits- und Qualitätsmaßnahmen. Kapitel 5 fasst die Ergebnisse zusammen, ordnet sie gegenüber den
Anforderungen aus Kapitel 3 ein und gibt einen Ausblick auf mögliche Weiterentwicklungen.

---

## Quellen in diesem Kapitel

```
[1] C. Baun and J. Bouché, "Challenges and Solutions of Developing and Implementing a Novel
    Desktop-as-a-Service," in Proc. 20th Int. Conf. Mobile Web Intell. Inf. Syst. (MobiWis 2024),
    Vienna, Austria, Aug. 2024.
[2] C. Baun and J. Bouché, "Closing the Gap between Web Applications and Desktop Applications by
    designing a novel Desktop-as-a-Service (DaaS) with seamless support for Desktop Applications,"
    Open Journal of Cloud Computing (OJCC), vol. 8, no. 1, pp. 1-19, 2023.
```

Beide Quellen sind Teil des vollständigen Literaturverzeichnisses in Kapitel 2.

## Offener Punkt zu diesem Kapitel
- [ ] Formulierung der Forschungsfrage mit Erstprüfer Prof. Dr. Baun final abstimmen.
