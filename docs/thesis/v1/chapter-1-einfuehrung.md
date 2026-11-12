# 1 Einführung

Cloud- und browserbasierte Arbeitsumgebungen gewinnen zunehmend an Bedeutung,
weil sie einen geräteunabhängigen Zugriff auf Anwendungen ermöglichen, ohne dass
eine lokale Installation nötig ist. Das System **DESIGN** verfolgt mit seinem
Desktop-as-a-Service-Ansatz (DaaS) das Ziel, die Vorteile lokal installierter
Anwendungen (Datenschutz, Unterstützung von Altanwendungen, keine Abhängigkeit
von einzelnen Anbietern) mit denen webbasierter Software (geräteunabhängiger
Zugriff über den Browser, keine Installation) zu verbinden (Baun & Bouché, 2023,
2024). DESIGN gliedert sich in drei eigenständige Teile, die jeweils in einem
eigenen Open-Source-Repository gepflegt werden (Baun, 2026):

- Die **DaaS-Anwendung** (*design-daas*) ist in Python geschrieben und stellt
  die Arbeitsumgebungen bereit. Sie verwaltet Container mit Docker und virtuelle
  Maschinen über Proxmox VE und leitet deren Anzeige über Apache Guacamole in
  den Browser weiter. Außerdem enthält sie das Installationsprogramm für das
  Gesamtsystem.
- Das **Frontend** (*design-daas-application-frontend*) ist eine React-Anwendung
  und bildet die Oberfläche im Browser. Nutzer starten und bedienen darüber ihre
  Anwendungen, Administratoren verwalten Konten, Gruppen und Desktops.
- Das **Backend** (*design-daas-application-backend*) verwaltet Nutzerkonten,
  Gruppen und Desktops, stellt bei der Anmeldung Zugriffstoken aus und
  entscheidet, welche Funktionen ein Nutzer aufrufen darf. Es ist in PHP
  geschrieben; sein Nachfolger (*design-daas-backend-java*) ist Gegenstand
  dieser Arbeit.

## 1.1 Motivation

Laut Aufgabenstellung dieser Arbeit ist das bestehende Backend von DESIGN in
**PHP** implementiert, „qualitativ schwach“, und „die Dokumentation ist
ebenfalls schlecht“ (Baun, 2026). Das erschwert Wartung und Weiterentwicklung
des Gesamtsystems, obwohl die beiden anderen Teile, DaaS-Anwendung und Frontend,
nach derselben Quelle qualitativ gut, ausreichend gut dokumentiert und
vergleichsweise einfach zu installieren sind. Das Backend bildet jedoch die
Grundlage für Authentifizierung, Autorisierung sowie Benutzer- und
Desktopverwaltung des gesamten Systems. Fehler oder mangelnde
Nachvollziehbarkeit an dieser Stelle wirken sich daher auf alle anderen
Systemteile aus. Hinzu kommt, dass das Projekt, in dem DESIGN entstanden ist,
abgeschlossen ist (Baun, 2026). Es gibt also kein Entwicklungsteam mehr, das das
Verhalten des Backends aus eigener Kenntnis erklären könnte.

## 1.2 Problemstellung

Aus der Ausschreibung ergibt sich die Aufgabe, ein neues Backend für DESIGN zu
entwickeln und zu implementieren, wahlweise in Python oder einer anderen
modernen Programmiersprache (Baun, 2026). Es soll die bestehende Funktionalität
des alten PHP-Backends ersetzen, ohne die fachliche Logik von Frontend und
DaaS-Anwendung zu verändern. Das neue Backend muss deshalb deren bisherige
Erwartungen an die Schnittstelle (Endpunkte, Feldformate, Verhalten der
Berechtigungsprüfung) unverändert erfüllen. Das setzt voraus, dass das Verhalten
des alten Backends zunächst verstanden und dokumentiert wird, bevor es neu
implementiert werden kann. Die mangelhafte Dokumentation des Ist-Systems
erschwert diese Aufgabe zusätzlich.

## 1.3 Zielsetzung

Ziel dieser Arbeit ist die Entwicklung und Implementierung eines neuen Backends
für DESIGN auf Basis von **Java 21** und **Spring Boot**. Das neue Backend soll
funktional gleichwertig zum alten PHP-Backend sein und dessen zentrale
Verhaltenseigenschaften beibehalten, insbesondere die mehrstufige
Berechtigungskaskade. Gleichzeitig sollen Wartbarkeit und Nachvollziehbarkeit
gegenüber dem Ist-Zustand verbessert werden, unter anderem durch automatisch
geprüfte Architekturregeln, eine Mindest-Testabdeckung und eine vollständige
Dokumentation. Schließlich soll das neue Backend in die Installation der
DaaS-Anwendung eingebunden und im Zusammenspiel mit Frontend und DaaS-Anwendung
durch End-to-End-Tests geprüft werden.

## 1.4 Forschungsfrage

Aus der Aufgabenstellung und der beschriebenen Problemstellung ergibt sich die
folgende Forschungsfrage:

„Wie lässt sich das Backend des Desktop-as-a-Service-Systems DESIGN von PHP nach
Java und Spring Boot migrieren, sodass Frontend und DaaS-Anwendung ohne
Änderungen an ihrer fachlichen Logik funktionsfähig bleiben, und welche
architektonischen Maßnahmen verbessern dabei die Wartbarkeit des Backends
gegenüber dem bisherigen Stand?“

Diese Frage wird in zwei Teilen beantwortet. Kapitel 3 analysiert das bestehende
PHP-Backend und leitet daraus die funktionalen und nicht-funktionalen
Anforderungen an das neue Backend ab. Kapitel 4 beschreibt, wie diese
Anforderungen mit Java und Spring Boot umgesetzt wurden und welche
architektonischen Maßnahmen, zum Beispiel automatisch geprüfte Architekturregeln
und eine Mindest-Testabdeckung, zur besseren Wartbarkeit beitragen. Kapitel 5
beantwortet die Frage auf dieser Grundlage.

## 1.5 Abgrenzung

Diese Arbeit beschränkt sich auf das **Backend**-Repository
(*design-daas-backend-java*). Die fachliche Logik von Frontend
(*design-daas-application-frontend*) und DaaS-Anwendung (*design-daas*) wird
nicht verändert; ihre bestehenden Schnittstellen gelten als gegebene
Randbedingung. Komponenten, die zur DaaS-Anwendung oder zur Infrastruktur von
DESIGN gehören, etwa Apache Guacamole, Proxmox VE oder der Container- und
VM-Lebenszyklus, sind nicht Gegenstand der Implementierung dieser Arbeit.
Sie werden nur so weit behandelt, wie es zur Einordnung des Backends in das
Gesamtsystem nötig ist (Abschnitte 2.1 und 3.1).

Am Frontend wurden einzelne Stellen angepasst, weil dort Fehler vorlagen und der
Code teilweise nicht sauber strukturiert war. Diese Anpassungen beschränken sich
bewusst auf den Teil des Codes, den das neue Backend für eine korrekte Anbindung
benötigt. In der DaaS-Anwendung wurden ebenfalls nur die Anbindung an das
Backend bereinigt und das Installationsprogramm so erweitert, dass es das neue
Backend mitinstalliert (Abschnitt 4.7.3). Für die End-to-End-Tests des
Gesamtsystems entstand zusätzlich das eigenständige Repository *design-daas-e2e*
mit Playwright-Tests (Abschnitt 4.8.6). Eine vollständige Überarbeitung oder
funktionale Erweiterung von Frontend und DaaS-Anwendung ist nicht Ziel dieser
Arbeit.

## 1.6 Methodisches Vorgehen

Das Vorgehen orientiert sich an den sechs Arbeitspaketen der Ausschreibung
(Baun, 2026): Recherche des DESIGN-Gesamtsystems (AP1), Analyse des alten
PHP-Backends (AP2), Entwicklung (AP3) und Implementierung (AP4) des neuen
Backends, Aufbau eines Demonstrators (AP5) sowie Test, Evaluation und
Dokumentation (AP6). Methodisch handelt es sich um eine Anforderungsanalyse
durch Reverse Engineering mit anschließender Neuimplementierung.

## 1.7 Aufbau der Arbeit

Kapitel 2 stellt die theoretischen Grundlagen dar: das DaaS-Konzept von DESIGN,
die Authentifizierung mit OAuth2 und JWT, Modelle der Autorisierung, die
verwendeten Spring-Technologien und Testwerkzeuge sowie die Migration von
Altsystemen. Kapitel 3 analysiert Ist- und Zielzustand des Backends und leitet
daraus funktionale sowie nicht-funktionale Anforderungen ab. Kapitel 4
beschreibt die Realisierung der einzelnen Backend-Module (Authentifizierung,
Autorisierung, Benutzerverwaltung, Desktop-Inventar), die übergreifenden
Sicherheits- und Qualitätsmaßnahmen, die Einbindung in die DaaS-Anwendung sowie
Tests und Dokumentation. Kapitel 5 fasst die Ergebnisse zusammen, ordnet sie
gegenüber den Anforderungen aus Kapitel 3 ein und gibt einen Ausblick auf
mögliche Weiterentwicklungen.
