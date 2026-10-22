# Masterthesis – Strukturplan
**Vorlage:** Vorlage_Thesis_de_2024 (Labor für Telekommunikationsnetze, FRA-UAS)
**Titel:** Entwicklung und Implementierung eines Backends für den innovativen Desktop-as-a-Service DESIGN
**Erstprüfer:** Prof. Dr. Christian Baun · **Zweitprüfer:** Prof. Dr. Matthias Deegener
**Zeitraum:** 22.06.2026 – 25.11.2026

> Status: Entwurf zur Diskussion. Quellen dieses Plans: `docs/Ausschreibung_DaaS_Backend_DESIGN_2026.pdf`
> (offizielle Aufgabenstellung, AP1–AP6) und `docs/ARC42.md` + Code dieses Repos (reale Architektur/Module).
> **Nichts aus einer früheren fehlerhaften Annahme (Guacamole, Mikrofon-Bridge, Drucker/CUPS, Proxmox,
> Container Pool Manager) ist enthalten — diese Komponenten existieren im Code nicht.**

---

## Fakten aus der Ausschreibung (AP1–AP6)
DESIGN besteht aus 3 Teilen: DaaS-Anwendung (Python), Frontend (React/JavaScript), **Backend (PHP, qualitativ
schwach, schlecht dokumentiert)**. Ziel: neues Backend in Python *oder einer anderen modernen Sprache*
(hier: **Java 21 / Spring Boot**).

| AP | Inhalt | Entspricht in etwa |
|---|---|---|
| AP1 | Recherche des DESIGN-Gesamtsystems | Kapitel 2 + 3.1 |
| AP2 | Analyse des alten Backends in PHP | Kapitel 3.1 / 3.2 |
| AP3 | Entwicklung eines neuen Backends (Konzept) | Kapitel 3.4, 4.1 |
| AP4 | Implementierung des neuen Backends in Java | Kapitel 4.2–4.6 |
| AP5 | Aufbau eines Demonstrators | Kapitel 4.7 |
| AP6 | Test, Evaluation und Dokumentation | Kapitel 4.8, 5 |

## Reale Module laut Code & ARC42 (`docs/ARC42.md`, `src/main/java/.../backend/`)
Der neue Backend-Dienst ist **rein API-basiert** (REST, Swagger), ohne eigene VM-/Container-Bereitstellung:
- **auth** – OAuth2-artige `password`/`refresh_token`-Grants, RS256-JWT, Logout, Session-Introspection
- **permission** – 4-stufige, fail-closed Berechtigungskaskade, portiert von PHP `PermissionCalculationService`
- **user** – Selbstregistrierung mit E-Mail-Verifikation, Admin-/Nutzerverwaltung, User-Groups
- **desktop** – CRUD für virtuelle Desktops und Desktop-Gruppen (reine Inventardaten)
- Querschnitt: Security-/CORS-/Rate-Limiting-Konfiguration, globales Exception-Handling, Flyway-Schema,
  MariaDB, optionaler SMTP-Mailversand

## Reales altes Backend (`../design-daas-application-backend/ARCHITECTURE.md`, bestätigt AP2)
PHP 8.2, Apache, Doctrine ORM 2.x + MariaDB, Doctrine Migrations, PHP-DI, Symfony Routing (PSR-7/Guzzle),
`league/oauth2-server` (Password Grant), OpenAPI via `zircote/swagger-php`, Monolog, Symfony EventDispatcher,
DB-backed Message Queue (`enqueue/dbal`), Symfony Mailer, Cron via Yacron, Docker Compose (dev + release-test
Profile, separate tmpfs-Test-DB), Tests via PHPUnit/PHPStan/PHPCS/PHPat. Funktional deckt es dieselben vier
Bereiche ab wie das neue Backend: Users/Admins (Rollen), Desktops/Desktop-Groups, feingranulare Permissions,
OAuth2-Auth, E-Mail-Benachrichtigungen — bestätigt die 1:1-Modulzuordnung in Kapitel 3/4.

---

## 1 Einführung (3–5 Seiten)
- Motivation: DaaS-Konzept DESIGN; Backend-Teil ist laut Ausschreibung qualitativ schwach / schlecht dokumentiert (PHP)
- Problemstellung: fehlende Wartbarkeit/Dokumentation des PHP-Backends erschwert Weiterentwicklung
- Zielsetzung: Neuimplementierung des Backends in Java 21 / Spring Boot bei erhaltener Kompatibilität zu Frontend und DaaS-Anwendung
- Forschungsfrage(n)
- Abgrenzung / Scope: nur dieses Backend-Repo; DaaS-Anwendung (Python) und Frontend (React) werden nicht verändert
- Methodisches Vorgehen: AP1–AP6
- Aufbau der Arbeit (1 Satz pro Kapitel)

## 2 Theoretischer Hintergrund (15–20 Seiten)
- 2.1 Desktop-as-a-Service – Konzept von DESIGN (Grundlage: MobiWis-2024-Paper/Folien, Informatik-Spektrum-, OJCC-Artikel)
- 2.2 Java 21 / Spring Boot (relevante Konzepte für diese Arbeit)
- 2.3 OAuth2 / JWT-basierte Authentifizierung
- 2.4 Autorisierungsmodelle / Berechtigungskaskaden
- 2.5 Architekturqualität in Spring-Anwendungen (Schichtenarchitektur, ArchUnit, Testabdeckung)
- 2.6 Verwandte Arbeiten (Publikationen von Baun zu DESIGN)

## 3 Anforderungsanalyse (12–15 Seiten) — *feste Gliederung*
- 3.1 Generelle Zielsetzung: Gesamtsystem DESIGN (3 Repos); Ist-Zustand altes PHP-Backend (AP1/AP2-Ergebnisse); Umfeld (Frontend, DaaS-Anwendung, MariaDB, SMTP)
- 3.2 Klärung der Anforderungen: Vorgehen (Code-/Dokumentationsanalyse des alten Backends, Ausschreibung); funktionale FA-n (Auth, Permission-Kaskade, Nutzerverwaltung, Desktop-Inventar) und nicht-funktionale NFA-n (Sicherheit, Kaskaden-Korrektheit, Wartbarkeit, Betriebsfähigkeit, Verhaltenskompatibilität zum PHP-Backend) — tabellarisch
- 3.3 Zeitliche Rahmenbedingungen: Start-/Enddatum, Meilensteine
- 3.4 Zielzustand: Soll-Architektur (Backend-Module + MariaDB + SMTP, Schnittstellen zu Frontend/DaaS-Anwendung) — Architekturbild
- 3.5 Anwendungsfälle für den Prototyp: Login/Token-Refresh/Logout, Berechtigungsprüfung, Registrierung & E-Mail-Verifikation, Admin-Verwaltung, Desktop-/Desktopgruppen-CRUD

## 4 Realisierung (30–40 Seiten) — *komponentenweise*
- 4.1 Architekturentscheidungen (Schichtenarchitektur + ArchUnit, Flyway statt Hibernate-DDL, zustandslose JWT-Auth) — Begründung gegenüber Kapitel 3
- 4.2 Authentifizierung (TokenIssuer/TokenService/SessionService, RS256-JWT, Grants)
- 4.3 Autorisierung / Berechtigungskaskade (PermissionService, Portierung der PHP-Logik)
- 4.4 Benutzerverwaltung (UserService/AdminService, Registrierung, E-Mail-Verifikation, User-Groups)
- 4.5 Desktop-Inventar (DesktopService, Desktop/DesktopGroup)
- 4.6 Querschnittsthemen (Security-/CORS-/Rate-Limiting-Konfiguration, Exception-Handling, Mailversand)
- 4.7 Demonstrator (AP5): Swagger-UI-gestützte Vorführung der Endpunkte
- 4.8 Tests & Evaluation (AP6): ArchUnit-Regeln, JaCoCo-Testabdeckung, Vergleich Verhalten alt (PHP) vs. neu (Java)

## 5 Zusammenfassung und Ausblick (3–5 Seiten)
- Rückbezug der Ergebnisse auf FA/NFA aus Kapitel 3
- Kritische Reflexion der eigenen Arbeit
- Was nicht wie geplant funktioniert hat
- Ausblick / zukünftige Arbeiten

## 6 Abkürzungen
- Automatisch aus den im Text verwendeten technischen Abkürzungen befüllt (alphabetisch)

## 7 Referenzen
- IEEE-Stil; u. a. MobiWis-2024-Paper/Folien, Informatik-Spektrum-Artikel, OJCC-Artikel, Spring-/Java-Dokumentation

## 8 Anhang (nicht in Seitenzahl enthalten)
- z. B. Inhalt der CD/DVD, größere Diagramme, Konfigurationsdateien, Testprotokolle

---

## Offene Punkte / Placeholders
- [ ] Konkrete Formulierung der Forschungsfrage
- [ ] Matrikeldaten
- [ ] Eigene Meilensteine innerhalb 22.06.2026–25.11.2026 festlegen (Literaturrecherche, AP1–AP6-Zeitplan,
      Anforderungsanalyse an Betreuer: 29.06.2026 (+1 Woche); Entwurf Ausarbeitung: 28.10.2026 (-4 Wochen);
      vollständiger Entwurf: 11.11.2026 (-2 Wochen))
- [ ] Zugriff auf MobiWis-/Informatik-Spektrum-/OJCC-Quellen als Dateien (für Kapitel 2/Referenzen)
- [ ] Ggf. Zugriff auf Frontend-Repo prüfen, falls Schnittstellenverhalten in 3.1 detaillierter belegt werden soll
