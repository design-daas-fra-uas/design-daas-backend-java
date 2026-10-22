# 3 Anforderungsanalyse (Entwurf)

> Status: Erster Entwurf, Grundlage für Diskussion. Feste Gliederung laut Vorlage: 3.1–3.5.
> Quellen: `docs/Ausschreibung_DaaS_Backend_DESIGN_2026.pdf` (AP1–AP6), `docs/ARC42.md` und Code dieses
> Repos (Ist der neuen Lösung), `../design-daas-application-backend/ARCHITECTURE.md` + `SKELETON.md`
> (Ist des alten PHP-Backends). Keine erfundenen Anforderungen — alles unten ist aus Code/Dokumentation
> abgeleitet; verbleibende Lücken sind als `[TODO]` markiert.

---

## 3.1 Generelle Zielsetzung

Das Gesamtsystem **DESIGN** besteht laut Ausschreibung aus drei eigenständigen Repositories: der
**DaaS-Anwendung** (Python, verwaltet Container/VMs, Guacamole, Proxmox), dem **Frontend** (React) und dem
**Backend**, das Gegenstand dieser Arbeit ist. Frontend und DaaS-Anwendung werden im Rahmen dieser Arbeit
nicht verändert; das Backend muss daher deren bestehenden Schnittstellenerwartungen weiterhin entsprechen.

**Ist-Zustand des alten Backends (AP2, Quelle: `ARCHITECTURE.md`/`SKELETON.md` des Repos
`design-daas-application-backend`):**
Implementiert in PHP 8.2 auf Apache, mit Doctrine ORM 2.x + MariaDB, Doctrine Migrations, PHP-DI
(Dependency Injection), Symfony Routing über eine PSR-7/Guzzle-Schicht, `league/oauth2-server` (Resource
Owner Password Credentials Grant), OpenAPI-Dokumentation via `zircote/swagger-php`, Monolog-Logging,
Symfony EventDispatcher, einer DB-gestützten Nachrichtenwarteschlange (`enqueue/dbal`) für E-Mail-Versand,
Symfony Mailer und einem Yacron-Cron-Dienst. Der Code ist in vertikale Module gegliedert
(`Main`, `Util`, `OAuth2`, `User`, `Desktop`, `Permissions`), jedes mit der internen Schichtung
`Model → Repository → Service → Controller (+ DTO/Enum/Exception)`. Das Domänenmodell nutzt Single Table
Inheritance für `BaseUser` (`Admin`/`User`), eine n:m-Beziehung zu `UserGroup`, eine Desktop-/
DesktopGroup-Struktur sowie ein eigenständiges, datenbankgetriebenes Berechtigungsmodell
(`EndpointUserAccess`, `EndpointUserGroupAccess`, `EndpointGroupUserAccess`, `EndpointGroupUserGroupAccess`)
mit einer Prioritäts-Kaskade (erste treffende Regel gewinnt, Standard ist Zugriff verweigern). Laut
Ausschreibung ist dieses Backend qualitativ schwach und schlecht dokumentiert; diese Arbeit übernimmt daher
die Verantwortung, dessen Verhalten AP2-konform zu analysieren, bevor es neu implementiert wird.

**Umfeld:** MariaDB als Datenspeicher, ein SMTP-Relay für Registrierungs-/Verifikations-E-Mails, das
React-Frontend als Konsument der Auth-/User-/Desktop-Endpunkte und die Python-DaaS-Anwendung als Konsument
der Berechtigungsprüfungs-Endpunkte. Beide bestehenden Clients setzen eine stabile, zum PHP-Backend
kompatible Schnittstelle voraus (Feldnamen, Statuscodes, Kaskadensemantik).

[TODO: Architekturbild Ist-Zustand (Komponentendiagramm: Frontend – altes PHP-Backend – DaaS-Anwendung –
MariaDB – SMTP) selbst zeichnen und hier als Bild 3.1 referenzieren.]

## 3.2 Klärung der Anforderungen

**Vorgehen:** Die Anforderungen wurden durch Quellcode- und Dokumentationsanalyse des alten PHP-Backends
(`ARCHITECTURE.md`, `SKELETON.md`, `composer.json`) sowie durch die Aufgabenstellung der Ausschreibung
ermittelt; Rücksprachen mit dem Betreuer dienten der Priorisierung und Abgrenzung
[TODO: konkrete Gesprächstermine/Ergebnisse ergänzen, sobald stattgefunden].

**Funktionale Anforderungen (FA):**

| ID | Anforderung | Quelle (altes Backend) |
|---|---|---|
| FA-1 | Nutzer authentifiziert sich per OAuth2 Password Grant (`POST /oauth2/user/token`) | `league/oauth2-server` Password Grant |
| FA-2 | Zugriffstoken kann per Refresh-Token-Grant erneuert werden | OAuth2-Modul |
| FA-3 | Nutzer kann sich abmelden; Token wird serverseitig widerrufen (`POST /oauth2/user/logout`) | OAuth2-Modul |
| FA-4 | Aktuelle Session/Token kann introspiziert werden (`GET /oauth2/user/session`) | OAuth2-Modul |
| FA-5 | Selbstregistrierung mit E-Mail-Verifikationscode (`POST /user`, `/user/validate_email`, `/user/resend_code`) | User-Modul |
| FA-6 | Admin kann Nutzer auflisten, ändern, aktivieren/deaktivieren (`GET /users`, `PATCH /user/{id}(+enable/disable)`) | User-Modul |
| FA-7 | Admin kann weitere Admins anlegen/auflisten (`GET /admins`, `POST /admin`) | User-Modul |
| FA-8 | Admin kann User-Groups verwalten (`GET/POST/PATCH /user_group(s)`) | User-Modul |
| FA-9 | Nutzer kann Freischaltung einer Anwendung anfragen; Benachrichtigung per E-Mail (`POST /user/request-application/{application}/{isoCode}`) | User-Modul |
| FA-10 | Desktops können angelegt/aufgelistet/abgefragt werden (`GET /desktops`, `POST /desktop`, `GET /desktop/{id}`) | Desktop-Modul |
| FA-11 | Desktop-Groups können verwaltet und mit User-Groups verknüpft werden (`GET/POST /desktop_group(s)`, `POST /desktop_group/{id}/user_group/{userGroupId}`) | Desktop-Modul |
| FA-12 | Für eine gegebene Funktion und einen Nutzer kann geprüft werden, ob Zugriff erlaubt ist, über eine 4-stufige Kaskade (`GET /permissions/{functionName}/{userId}`, `POST /permissions_info`) | Permissions-Modul |

**Nicht-funktionale Anforderungen (NFA):**

| ID | Anforderung | Begründung |
|---|---|---|
| NFA-1 | Fail-closed-Sicherheitsverhalten: unbekannte Nutzer/Funktionen oder fehlende Regeln führen zu Zugriffsverweigerung | Übernahme aus dem alten Backend, Sicherheitsprinzip |
| NFA-2 | Verhaltenskompatibilität der Berechtigungskaskade (identische Priorität/Konfliktauflösung wie das alte Backend) | DaaS-Anwendung bleibt unverändert nutzbar |
| NFA-3 | API-Formkompatibilität (Feldnamen, Statuscodes) zu Frontend und DaaS-Anwendung | Beide Clients werden nicht angepasst |
| NFA-4 | Passwörter werden gehasht gespeichert; Konten werden nach wiederholten Fehlversuchen gesperrt | Sicherheit |
| NFA-5 | Sensible Endpunkte sind ratenbegrenzt | Schutz vor Missbrauch/Brute-Force |
| NFA-6 | Zugriffstoken sind serverseitig widerrufbar, trotz sonst zustandsloser Tokens | Logout/erzwungene Abmeldung muss möglich sein |
| NFA-7 | Der Dienst ist zustandslos und horizontal skalierbar (kein Server-seitiger Session-State außer DB) | Betriebsfähigkeit |
| NFA-8 | Die Schichtenarchitektur (Controller→Service→Repository) wird automatisiert über den Build geprüft | Wartbarkeit |
| NFA-9 | Eine Mindest-Testabdeckung wird im Build erzwungen | Wartbarkeit/Qualitätssicherung |
| NFA-10 | Das Datenbankschema wird ausschließlich über versionierte Migrationen verändert, nicht automatisch generiert | Nachvollziehbarkeit/Reproduzierbarkeit |

[TODO: FA/NFA-IDs ggf. mit Betreuer abgleichen und nummerieren, falls zusätzliche Anforderungen aus
Gesprächen hinzukommen.]

## 3.3 Zeitliche Rahmenbedingungen

- **Start der Arbeit:** 22.06.2026
- **Ende der Arbeit:** 25.11.2026
- **Anforderungsanalyse an den Betreuer:** 29.06.2026 (1 Woche nach Start)
- **Entwurf der Ausarbeitung:** 28.10.2026 (4 Wochen vor Abgabe)
- **Vollständiger schriftlicher Entwurf:** 11.11.2026 (2 Wochen vor Abgabe)
- **Abgabe im Prüfungsamt:** 25.11.2026

Eigene Meilensteine [TODO: an tatsächlichen Projektstand anpassen/bestätigen]:

| Meilenstein | Zeitraum |
|---|---|
| AP1 – Recherche Gesamtsystem DESIGN | 22.06.–06.07.2026 |
| AP2 – Analyse altes PHP-Backend | 22.06.–13.07.2026 |
| AP3 – Entwurf neues Backend (Konzept/Architektur) | 13.07.–03.08.2026 |
| AP4 – Implementierung neues Backend (Java/Spring Boot) | [TODO: bereits weit fortgeschritten laut Code-Stand] |
| AP5 – Demonstrator | [TODO] |
| AP6 – Test, Evaluation, Dokumentation | [TODO] |
| Literaturrecherche / Kapitel 2 | laufend |
| Einarbeiten Feedback Betreuer | nach 28.10.2026 |

## 3.4 Zielzustand

Das Zielsystem ist ein eigenständiger Java-21-/Spring-Boot-Dienst (`de.frauas.design.backend`), gegliedert
in die Module **auth**, **permission**, **user**, **desktop** sowie die Querschnittspakete **shared**,
**config** und **bootstrap**, jedes mit identischer Schichtung `controller → service → repository → model`.
Authentifizierung erfolgt zustandslos über RS256-signierte JWTs mit datenbankgestützter Widerrufsliste;
Autorisierung über eine 4-stufige, fail-closed Kaskade, die die Semantik des alten PHP-Backends 1:1
nachbildet. MariaDB dient als alleiniger Datenspeicher (Schema ausschließlich über Flyway-Migrationen), ein
SMTP-Relay versendet Registrierungs-/Verifikations-E-Mails. Der Dienst ist als Docker-Image paketiert,
zustandslos und damit horizontal hinter einem Load Balancer skalierbar; Schichtenarchitektur und
Testabdeckung werden automatisiert (ArchUnit, JaCoCo) während des Builds erzwungen. Frontend und
DaaS-Anwendung greifen unverändert über dieselben REST-Endpunkte und Feldformate zu wie zuvor beim
PHP-Backend.

[TODO: Zielarchitekturbild (Bild 3.2) selbst erstellen: Backend-Module ↔ MariaDB/SMTP ↔ Frontend/DaaS-Anwendung.]

## 3.5 Anwendungsfälle für den Prototyp

| # | Anwendungsfall | Beteiligte Module |
|---|---|---|
| 1 | Login per Password Grant und anschließende Berechtigungsprüfung durch die DaaS-Anwendung | auth, permission |
| 2 | Token-Erneuerung per Refresh-Token-Grant | auth |
| 3 | Logout mit serverseitigem Tokenwiderruf | auth |
| 4 | Selbstregistrierung inkl. E-Mail-Verifikation | user |
| 5 | Admin legt Benutzer/Admin/User-Group an und (de)aktiviert Konten | user |
| 6 | Admin legt Desktop und Desktop-Group an und verknüpft sie mit einer User-Group | desktop |
| 7 | DaaS-Anwendung fragt für eine gegebene Funktion und einen Nutzer die Zugriffsentscheidung ab | permission |
| 8 | Nutzer fragt Freischaltung einer Anwendung an (Benachrichtigungs-E-Mail an Admin) | user |

---

## Offene Punkte zu diesem Kapitel
- [ ] Zwei Architekturbilder erstellen (Ist-Zustand 3.1, Zielzustand 3.4) — keine Abbildungen aus
      fremden Quellen übernehmen.
- [ ] Eigene Meilensteine in 3.3 mit tatsächlichem Projektfortschritt abgleichen (Code scheint AP3/AP4
      bereits weit umgesetzt zu haben — ggf. Reihenfolge der Arbeit im Text reflektieren, z. B.
      iteratives Vorgehen statt rein linearer AP-Abfolge).
- [ ] FA/NFA-Nummerierung final mit Betreuer abstimmen.
- [ ] Prüfen, ob zusätzliche NFA zu Datenschutz (PII-Maskierung in Logs) als eigene Zeile sinnvoll ist.
