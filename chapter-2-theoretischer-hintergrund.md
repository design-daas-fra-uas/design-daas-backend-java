# 2 Theoretischer Hintergrund (Entwurf)

> Status: Erster Entwurf zur Diskussion, Ziel ~15-20 Seiten (hier kürzer, als Grundlage zum Ausbauen).
> Zitierte Quellen sind real und verifiziert: MobiWis-2024-Paper/Folien, OJCC-2023-Artikel, Informatik-Spektrum-2024-Artikel
> (alle aus der Ausschreibung), sowie offizielle RFCs/Dokumentationen für OAuth2/JWT/Spring/ArchUnit/Flyway/JaCoCo.
> Platzhalter `[TODO]` / `[Quelle fehlt]` markieren Stellen, die noch ergänzt/belegt werden müssen.

---

Dieses Kapitel legt die theoretische Grundlage für die in Kapitel 4 beschriebenen Entwurfsentscheidungen.
Zunächst wird in 2.1 das Gesamtsystem DESIGN vorgestellt und eingeordnet, in welchem Teil dieser Architektur
sich das in dieser Arbeit entwickelte Backend bewegt. Anschließend werden die drei Themenfelder behandelt,
auf denen das Backend technisch aufbaut: die Authentifizierung über OAuth2 und JSON Web Token einschließlich
des verwendeten Signaturverfahrens RS256 (2.2), die Autorisierung über ein rollen- und kaskadenbasiertes
Berechtigungsmodell (2.3) sowie die eingesetzten Spring-Boot-Bausteine und Werkzeuge für Architektur- und
Qualitätssicherung wie ArchUnit, Flyway und JaCoCo (2.4). Abschließend ordnet 2.5 diese Grundlagen den
konkreten Modulen und Entscheidungen des Backends zu und grenzt ab, welche allgemeinen Technikgrundlagen
bewusst nicht vertieft werden.

## 2.1 Desktop-as-a-Service und die Architektur von DESIGN

Desktop-as-a-Service (DaaS) bezeichnet die Bereitstellung vollständiger Arbeitsumgebungen über das Netzwerk,
wobei Anwendungen nicht lokal installiert, sondern auf entfernten Ressourcen ausgeführt und über einen
Client dargestellt werden. Im Gegensatz zu klassischer lokaler Software bietet dieser Ansatz einfache
Fernzugriffsmöglichkeiten und zentrale Administration, bringt jedoch typische Nachteile von
Software-as-a-Service mit sich, etwa Abhängigkeit von einer Netzwerkverbindung und potenzielle
Datenschutz- und Sicherheitsbedenken [1].

Das System **DESIGN** verfolgt das Ziel, die Vorteile lokal installierter Anwendungen (Datenschutz,
Unterstützung von Legacy-Software, kein Vendor-Lock-in) mit denen webbasierter Software (geräteunabhängiger
Zugriff über den Browser, keine Installation) zu verbinden, ohne deren jeweilige Nachteile zu übernehmen [1], [2].
Um unveränderte Linux- und Windows-Anwendungen bereitzustellen, nutzt DESIGN Containervirtualisierung
(Docker) sowie virtuelle Maschinen (KVM, verwaltet über die Virtualisierungsplattform Proxmox VE); die
Fernsteuerung der jeweiligen Anwendung erfolgt über VNC- bzw. RDP-Dienste innerhalb des Containers/der VM,
die durch das Gateway **Apache Guacamole** in einen Web-Stream übersetzt und so im Browser des Nutzers
dargestellt werden [1]. Dieser Architekturansatz erlaubt drei Bereitstellungsvarianten: (1) Linux-Anwendungen
in Linux-Containern, (2) Windows-Anwendungen in Linux-Containern mittels Wine, sofern dies funktioniert, und
(3) Windows-Anwendungen in Windows-VMs, falls Wine nicht einsetzbar ist [1].

Die Autoren dokumentieren in [1] mehrere praktische Herausforderungen, die bei Entwicklung und Betrieb von
DESIGN auftraten, u. a. messbare Unterschiede in der Bereitstellungs- und Startzeit von Anwendungen
(Docker: Start ca. 5-10 s, Klonen ca. 5-30 s, Neuanlage ca. 8-10 min; Windows-VM: Start ca. 10-20 s, Klonen
ca. 4-5 min, Neuanlage ca. 30-60 min) sowie die fehlende native Unterstützung von Sound, Mikrofon und
Drucken durch VNC, die zusätzliche Komponenten gegenüber RDP erfordert [1].

**Aus Sicht dieser Arbeit relevant:** Diese Komponenten (Guacamole, Proxmox, Docker/VM-Lebenszyklus,
Sound-/Mikrofon-/Druck-Bridges) gehören zur **DaaS-Anwendung** („Engine“, Python, separates Repository) bzw.
zur Infrastrukturebene von DESIGN und werden von ihr verwaltet, **nicht** vom Backend, das Gegenstand dieser
Arbeit ist. Das Backend stellt stattdessen die Authentifizierungs-, Autorisierungs-, Benutzerverwaltungs- und
Desktop-Inventar-Schnittstelle bereit, die von Frontend und DaaS-Anwendung genutzt wird (siehe Kapitel 3.1).
Die Darstellung der Gesamtarchitektur in diesem Abschnitt dient daher der Einordnung des Backends in das
Gesamtsystem, nicht der Beschreibung eigener Beiträge.

[TODO: ggf. Architekturbild aus [1]/[2] sinngemäß neu zeichnen (keine Fremdabbildungen übernehmen, Vorlage-Regel) und hier referenzieren.]

## 2.2 Authentifizierung: OAuth2, JSON Web Token und RS256

OAuth 2.0 (RFC 6749) ist ein Autorisierungs-Framework, das einem Client ermöglicht, im Namen eines
Ressourceninhabers beschränkten Zugriff auf eine geschützte Ressource zu erhalten, ohne dessen Zugangsdaten
direkt zu verarbeiten [3]. Für Anwendungsfälle, in denen Client und Ressourceninhaber ein hohes gegenseitiges
Vertrauen zueinander haben, etwa ein first-party Frontend, das Benutzername und Passwort entgegennimmt und
direkt an das Backend weiterleitet, definiert RFC 6749 den *Resource Owner Password Credentials Grant* [3].
Dieser Grant-Typ passt zum hier beschriebenen Backend, weil Frontend und Backend aus demselben Projekt
stammen und kein externer Drittanbieter eingebunden werden muss. Die Rückgabe und Verwendung von
Zugriffstoken als sogenannte Bearer-Token ist in RFC 6750 beschrieben: Wer das Token besitzt, kann damit auf
die Ressource zugreifen, unabhängig davon, ob er der ursprüngliche Nutzer ist, weshalb die Übertragung
ausschließlich über TLS erfolgen sollte [4]. Zur zustandslosen Übermittlung von Berechtigungsinformationen
wird das JSON-Web-Token-Format (RFC 7519) genutzt. Ein JWT kodiert Ansprüche (*Claims*), etwa die Nutzer-ID,
die vergebenen Rollen und das Ablaufdatum, in einem kompakten, signierten Format, sodass ein Server den
Aussteller und die Integrität des Tokens prüfen kann, ohne selbst einen Sitzungszustand vorzuhalten [5].

### 2.2.1 Signaturverfahren: RS256 statt HS256

Damit ein JWT gegen Manipulation geschützt ist, muss es signiert werden. Das Format und die zulässigen
Algorithmen für diese Signatur sind in zwei weiteren RFCs geregelt: RFC 7515 (JSON Web Signature, JWS)
beschreibt den strukturellen Aufbau eines signierten Tokens aus Header, Payload und Signatur [6], und
RFC 7518 (JSON Web Algorithms, JWA) definiert die konkreten Signaturalgorithmen, unter anderem HS256 und
RS256 [7]. Der naheliegendste Algorithmus, HS256 (HMAC mit SHA-256), ist symmetrisch: Aussteller und Prüfer
eines Tokens benötigen denselben geheimen Schlüssel. In einem System mit mehreren Diensten, die Token nur
lesen und prüfen müssen, aber selbst keine Token ausstellen dürfen, ist das ein Problem, denn jeder Dienst,
der ein Token verifizieren können soll, müsste das geheime Schlüsselmaterial erhalten, mit dem sich auch
beliebige neue Token erzeugen ließen.

RS256 (RSASSA-PKCS1-v1_5 mit SHA-256) löst dieses Problem, weil es ein asymmetrisches Verfahren ist: Die
Signatur wird mit einem privaten RSA-Schlüssel erzeugt, die Prüfung erfolgt dagegen mit dem zugehörigen
öffentlichen Schlüssel. Der private Schlüssel bleibt ausschließlich bei der ausstellenden Stelle, während
der öffentliche Schlüssel beliebig verteilt werden kann, zum Beispiel über einen JWK-Set-Endpunkt, ohne dass
dadurch die Möglichkeit entsteht, selbst gültige Token zu fälschen. Für dieses Backend bedeutet das, dass nur
das Backend selbst den privaten Schlüssel besitzt und Zugriffstoken ausstellen darf, während andere
Komponenten des DESIGN-Systems, etwa das Frontend oder die DaaS-Anwendung, Token ausschließlich mit dem
öffentlichen Schlüssel prüfen können. Diese Trennung ist der Hauptgrund, warum im Rahmen dieser Arbeit RS256
anstelle von HS256 eingesetzt wird.

### 2.2.2 Spring Security, OAuth2-Ressourcenserver und die Rolle von Spring Authorization Server

Spring Security ist die Sicherheitskomponente des Spring-Frameworks und stellt Authentifizierung und
Autorisierung über eine Kette von Servlet-Filtern bereit, die sogenannte *SecurityFilterChain* [9]. Jede
eingehende Anfrage durchläuft diese Kette, bevor sie den eigentlichen Controller erreicht. Für JWT-basierte
APIs bietet Spring Security die Rolle des *OAuth2 Resource Server*: Über den Baustein
`spring-boot-starter-oauth2-resource-server` wird der Filterkette automatisch ein Filter hinzugefügt, der den
`Authorization`-Header einer Anfrage ausliest, das darin enthaltene Bearer-Token an einen `JwtDecoder` zur
Signatur- und Claim-Prüfung übergibt und bei Erfolg ein authentifiziertes Sicherheitsobjekt (eine
`Authentication`) für die weitere Verarbeitung bereitstellt [9]. Erst danach greifen die in diesem Backend
konfigurierten Autorisierungsregeln (siehe 2.3).

An dieser Stelle lohnt sich eine Begriffsklärung, weil sie im Projektverlauf häufig zu Verwechslungen führt.
Broadcom/VMware pflegt neben Spring Security auch ein eigenständiges Projekt namens *Spring Authorization
Server*, das einen vollständigen OAuth2- und OpenID-Connect-Autorisierungsserver mit eigenen Endpunkten für
Token-Ausstellung, Introspektion und Widerruf bereitstellt [10]. Dieses Projekt wird im vorliegenden Backend
nicht als Abhängigkeit eingebunden. Die zugehörige Bibliothek `spring-security-oauth2-authorization-server`
taucht in der Maven-Konfiguration lediglich in einem Kommentar auf, weil Spring Boot sie versionsseitig
mitverwaltet; sie wird jedoch nicht deklariert und nicht verwendet. Stattdessen implementiert das Backend
eine schlanke, auf den eigenen Anwendungsfall zugeschnittene Token-Ausstellung selbst: Der Baustein
`spring-boot-starter-oauth2-resource-server` bringt transitiv die Bibliothek *Nimbus JOSE+JWT* mit, die
Spring Security intern für das Erzeugen (`JwtEncoder`) und Prüfen (`JwtDecoder`) von JWTs nutzt [11]. Auf
dieser Grundlage erzeugt das Backend beim ersten Start ein RSA-Schlüsselpaar mit 2048 Bit, persistiert es
lokal als JSON Web Key und verwendet es fortan sowohl zum Signieren neu ausgestellter Token als auch, über
den zugehörigen öffentlichen Schlüssel, zu deren Prüfung. Zusätzlich zur Standardprüfung von Signatur,
Ablaufdatum und Aussteller (*issuer*) ist die Prüfkette um einen anwendungsspezifischen Validator erweitert,
der bereits ausgestellte, aber zwischenzeitlich widerrufene Token anhand eines Datenbankeintrags erkennt.
Das ist notwendig, weil ein JWT laut Spezifikation zustandslos ist und ohne eine solche Zusatzprüfung bis zu
seinem Ablauf gültig bliebe, selbst wenn sich der Nutzer zwischenzeitlich abgemeldet hat oder gesperrt wurde.

## 2.3 Autorisierung und Berechtigungsmodelle

Neben der Authentifizierung (Wer ist der Nutzer?) ist die Autorisierung (Was darf der Nutzer?) ein eigener
Belang. Ein verbreitetes Modell ist die rollenbasierte Zugriffskontrolle (*Role-Based Access Control*, RBAC),
die Berechtigungen nicht direkt an einzelne Nutzer, sondern an Rollen bindet, denen Nutzer zugeordnet werden.
Das Modell wurde durch den Standard ANSI/INCITS 359 formalisiert [12]. Systeme, die zusätzlich Berechtigungen
für Gruppen von Nutzern und für einzelne Endpunkte oder Funktionen getrennt verwalten, kombinieren RBAC
häufig mit mehrstufigen Entscheidungs-Kaskaden. Dabei werden mehrere Regelebenen nacheinander ausgewertet,
zum Beispiel zuerst eine nutzerspezifische Regel, dann eine gruppenspezifische Regel und erst zuletzt eine
Standardregel, bis die erste Regel greift, die zu dieser Anfrage passt.

Für den Fall, dass keine dieser Regeln zutrifft, stellt sich die Frage nach dem Standardverhalten des
Systems. Die hier gewählte Antwort folgt einem klassischen Prinzip des sicheren Systementwurfs, das bereits
1975 von Saltzer und Schroeder unter dem Namen *fail-safe defaults* beschrieben wurde: Eine
Zugriffsentscheidung sollte grundsätzlich auf Verweigerung beruhen, und Zugriff darf nur gewährt werden, wenn
eine Bedingung dies ausdrücklich erlaubt, nicht umgekehrt [13]. In der Praxis wird dieses Verhalten häufig
als *fail-closed* oder *deny-by-default* bezeichnet und taucht auch in aktuellen Sicherheitsempfehlungen
wieder auf, etwa in den OWASP Proactive Controls, die eine konsequente Durchsetzung von Zugriffskontrollen
mit Default-Deny als eine der wichtigsten Maßnahmen gegen fehlerhafte Autorisierung nennen [14]. Im
vorliegenden Backend wird dieses Prinzip an zwei Stellen umgesetzt: Auf Ebene von Spring Security verlangt
die Sicherheitskonfiguration standardmäßig eine Authentifizierung für jede Anfrage, und nur einzelne, bewusst
ausgewählte Endpunkte werden explizit davon ausgenommen. Auf Ebene der eigentlichen Berechtigungsprüfung
liefert die mehrstufige Kaskade nur dann ein Ergebnis ungleich "verweigert", wenn in mindestens einer der
vier Stufen (direkte Nutzerregel, Gruppenregel, Regel auf Endpunktgruppenebene für den Nutzer, Regel auf
Endpunktgruppenebene für die Gruppe) ausdrücklich eine erlaubende Regel gefunden wird; ist keine Regel
vorhanden oder ist der Nutzer unbekannt, bleibt es bei der Verweigerung.

[Quelle fehlt: generische Literatur zu mehrstufigen/kaskadierenden Autorisierungsmodellen über den
RBAC-Standard hinaus, wird noch recherchiert.]

## 2.4 Spring Boot, Schichtenarchitektur und Werkzeuge für Qualitätssicherung

Spring Boot ist ein Framework für das Java-Ökosystem, das auf dem klassischen Spring Framework aufbaut und
durch Konventionen und Auto-Konfiguration den Aufwand reduziert, eine eigenständig lauffähige,
produktionsreife Anwendung aufzusetzen [8]. Statt zahlreicher XML-Konfigurationsdateien genügt es in der
Regel, eine passende Abhängigkeit (einen sogenannten *Starter*) einzubinden, damit Spring Boot die zugehörige
Komponente automatisch mit sinnvollen Standardwerten konfiguriert; eigene Konfigurationsklassen überschreiben
diese Standardwerte bei Bedarf gezielt. Dieses Projekt nutzt dafür unter anderem folgende Bausteine:

- **spring-boot-starter-web** stellt die REST-Schicht bereit, also die Infrastruktur, über die Controller
  HTTP-Anfragen entgegennehmen und JSON-Antworten zurückgeben.
- **spring-boot-starter-security** und **spring-boot-starter-oauth2-resource-server** bilden zusammen die in
  2.2 beschriebene JWT-Authentifizierung.
- **spring-boot-starter-data-jpa** bindet Hibernate als Implementierung der Jakarta Persistence API (JPA)
  ein und bildet Java-Klassen (*Entities*) auf Tabellen der relationalen Datenbank ab, sodass Datenzugriffe
  weitgehend ohne handgeschriebenes SQL auskommen.
- **spring-boot-starter-validation** ermöglicht deklarative Eingabevalidierung über Jakarta-Bean-Validation-
  Annotationen (z. B. Pflichtfelder, Längen- oder Formatprüfungen) direkt an den Datenübertragungsobjekten.
- **spring-boot-starter-mail** und **spring-boot-starter-thymeleaf** werden zusammen für den Versand von
  E-Mails genutzt, etwa zur Bestätigung einer neuen Registrierung: Thymeleaf erzeugt dabei den HTML-Inhalt
  der E-Mail als serverseitig gerendertes Template, das anschließend über die Mail-Starter-Komponente
  verschickt wird.
- **mariadb-java-client** ist der JDBC-Treiber, über den die Anwendung produktiv mit einer MariaDB-Datenbank
  kommuniziert.
- **springdoc-openapi-starter-webmvc-ui** erzeugt aus den Controller- und DTO-Klassen automatisch eine
  OpenAPI-Beschreibung der Schnittstelle sowie eine interaktive Swagger-UI zum Testen der Endpunkte.
- **lombok** reduziert Boilerplate-Code (Getter, Setter, Konstruktoren) über Annotationen, die zur
  Kompilierzeit in echten Java-Code übersetzt werden.
- **spring-boot-starter-test**, **spring-security-test** und **h2** bilden die Testinfrastruktur: Der
  Test-Starter bringt JUnit 5 und Mockito mit, spring-security-test stellt Hilfsmittel bereit, um
  authentifizierte Anfragen in Tests zu simulieren, und die eingebettete Datenbank H2 ersetzt in
  Integrationstests die produktive MariaDB-Instanz, ohne dass dafür eine externe Datenbank laufen muss.
- **spring-boot-starter-actuator** stellt Health- und Monitoring-Endpunkte bereit, über die sich zur
  Laufzeit prüfen lässt, ob die Anwendung und ihre Abhängigkeiten (z. B. die Datenbankverbindung) korrekt
  funktionieren.

Spring Security selbst wurde bereits in 2.2.2 im Detail erläutert. Um zusätzlich die Einhaltung einer
vorgesehenen Schichtenarchitektur (z. B. Controller ruft nur Service, Service ruft nur Repository, nicht
umgekehrt) über die gesamte Projektlaufzeit hinweg automatisiert zu prüfen, setzt dieses Projekt das
Architektur-Testwerkzeug ArchUnit ein. Mit ArchUnit lassen sich Regeln über erlaubte und verbotene
Paketabhängigkeiten als ganz normale JUnit-5-Tests formulieren, die bei jedem Build mitlaufen und damit
verhindern, dass sich die Architektur unbemerkt im Laufe der Entwicklung auflöst [15].

### 2.4.1 Flyway: versionierte Datenbankmigrationen

Eine relationale Datenbank entwickelt sich über die Projektlaufzeit genauso weiter wie der Code: Tabellen
kommen hinzu, Spalten ändern sich, Indizes werden ergänzt. Flyway ist ein Werkzeug, das diese Änderungen als
nummerierte, unveränderliche SQL-Skripte (z. B. `V1__init.sql`, `V2__add_user_group.sql`) verwaltet [16]. Beim
Start der Anwendung vergleicht Flyway die im Projekt vorhandenen Migrationsskripte mit einer eigenen Tabelle
in der Zieldatenbank, in der bereits ausgeführte Migrationen protokolliert sind, und führt dann automatisch
genau die Skripte aus, die noch nicht angewendet wurden. Jedes ausgeführte Skript wird dabei mit einer
Prüfsumme versehen; wird eine bereits angewendete Migration nachträglich verändert, bricht Flyway beim
nächsten Start mit einem Fehler ab, anstatt eine möglicherweise inkonsistente Datenbank stillschweigend zu
akzeptieren. Für diese Arbeit ist das aus zwei Gründen relevant: Erstens lässt sich damit das Datenbankschema
unter Versionskontrolle nehmen und nachvollziehbar zwischen Entwicklungs-, Test- und Produktivumgebung
synchron halten. Zweitens bauen die in H2 laufenden Integrationstests (siehe oben) genau dieselben
Migrationsskripte auf, die auch gegen MariaDB laufen, wodurch Testumgebung und Produktivumgebung bezogen auf
das Schema weitgehend identisch bleiben.

### 2.4.2 JaCoCo: Testabdeckung messen

JaCoCo (Java Code Coverage) ist ein Werkzeug, das zur Laufzeit misst, welche Teile des kompilierten Bytecodes
während der Testausführung tatsächlich durchlaufen wurden [17]. Technisch geschieht das über einen
Java-Agenten, der sich beim Start der Testausführung in die JVM einklinkt (in diesem Projekt über das
Maven-Ziel `prepare-agent`) und jede ausgeführte Code-Zeile beziehungsweise jeden ausgeführten Zweig
(*branch*) mitzählt. Nach Abschluss der Tests erzeugt das zugehörige Maven-Ziel `report` einen HTML- und
XML-Bericht, der pro Klasse und Paket ausweist, wie hoch der Anteil an durch Tests abgedecktem Code ist. Im
Rahmen dieser Arbeit dient dieser Bericht als objektive, automatisiert erzeugte Kennzahl für die
Testabdeckung der implementierten Module und wird in Kapitel 3/4 zur Bewertung der Testqualität herangezogen.

## 2.5 Einordnung für diese Arbeit

Die vorangehenden Abschnitte liefern die Grundlage für die in Kapitel 4 beschriebenen Entscheidungen: Die
OAuth2-/JWT-basierte Authentifizierung mit RS256 (2.2) bildet die Basis für das Modul *auth*, das
RBAC-/Kaskaden-Modell mit Default-Deny (2.3) für das Modul *permission* (das die Berechtigungslogik des alten
PHP-Backends verhaltenskompatibel nachbildet, siehe Kapitel 3.1/4.3), und die Spring-Boot-/ArchUnit-/Flyway-
/JaCoCo-Grundlagen (2.4) für die in Kapitel 4.1 begründete Zielarchitektur und die in Kapitel 3/4 dargestellte
Qualitätssicherung. Eine vertiefte Darstellung allgemeiner Technologiegrundlagen (z. B. generelle
REST-Prinzipien) unterbleibt bewusst, da die Vorlage verlangt, nur das für diese Arbeit Relevante zu
beschreiben und allgemeine Technik eher zu zitieren als ausführlich zu erklären.

---

## Verwendete Quellen in diesem Kapitel (IEEE, vorläufige Nummerierung)

```
[1]  C. Baun and J. Bouché, "Challenges and Solutions of Developing and Implementing a Novel
     Desktop-as-a-Service," in Proc. 20th Int. Conf. Mobile Web Intell. Inf. Syst. (MobiWis 2024),
     Vienna, Austria, Aug. 2024.
[2]  C. Baun and J. Bouché, "Closing the Gap between Web Applications and Desktop Applications by
     designing a novel Desktop-as-a-Service (DaaS) with seamless support for Desktop Applications,"
     Open Journal of Cloud Computing (OJCC), vol. 8, no. 1, pp. 1-19, 2023.
[3]  D. Hardt, Ed., "The OAuth 2.0 Authorization Framework," IETF, RFC 6749, Oct. 2012.
[4]  M. Jones and D. Hardt, "The OAuth 2.0 Authorization Framework: Bearer Token Usage," IETF,
     RFC 6750, Oct. 2012.
[5]  M. Jones, J. Bradley, and N. Sakimura, "JSON Web Token (JWT)," IETF, RFC 7519, May 2015.
[6]  M. Jones, J. Bradley, and N. Sakimura, "JSON Web Signature (JWS)," IETF, RFC 7515, May 2015.
[7]  M. Jones, "JSON Web Algorithms (JWA)," IETF, RFC 7518, May 2015.
[8]  VMware/Broadcom, "Spring Boot Reference Documentation." https://docs.spring.io/spring-boot/
     (accessed Oct. 2026).
[9]  VMware/Broadcom, "Spring Security Reference Documentation." https://docs.spring.io/spring-security/
     reference/ (accessed Oct. 2026).
[10] VMware/Broadcom, "Spring Authorization Server Reference Documentation."
     https://docs.spring.io/spring-authorization-server/reference/ (accessed Oct. 2026).
[11] Connect2id Ltd., "Nimbus JOSE + JWT." https://connect2id.com/products/nimbus-jose-jwt (accessed Oct. 2026).
[12] American National Standards Institute, "Role Based Access Control," ANSI/INCITS 359-2004, 2004.
[13] J. H. Saltzer and M. D. Schroeder, "The protection of information in computer systems,"
     Proceedings of the IEEE, vol. 63, no. 9, pp. 1278-1308, Sep. 1975.
[14] OWASP Foundation, "OWASP Proactive Controls: C7 Enforce Access Controls."
     https://owasp.org/www-project-proactive-controls/ (accessed Oct. 2026).
[15] TNG Technology Consulting, "ArchUnit - User Guide." https://www.archunit.org/userguide/html/000_Index.html
     (accessed Oct. 2026).
[16] Redgate Software, "Flyway Documentation: Migrations." https://documentation.red-gate.com/flyway
     (accessed Oct. 2026).
[17] EclEmma/Mountainminds, "JaCoCo Java Code Coverage Library - Documentation."
     https://www.jacoco.org/jacoco/trunk/doc/ (accessed Oct. 2026).
```

> Hinweis: Die Informatik-Spektrum-Quelle (doi: 10.1007/s00287-024-01583-x) ist inhaltlich sehr nah an [1]/[2]
> und sollte ergänzt werden, sobald geklärt ist, welche spezifischen Aussagen daraus zitiert werden
> (noch nicht im Volltext geprüft, `[Quelle fehlt]` falls ohne Volltextzugriff nicht verifizierbar).

## Offene Punkte zu diesem Kapitel
- [ ] Volltext des Informatik-Spektrum-Artikels (10.1007/s00287-024-01583-x) und des OJCC-Artikels beschaffen
      und die obigen Aussagen ggf. direkt daraus belegen/verfeinern (aktuell nur über die Folien gestützt).
- [ ] Generische Quelle für kaskadierende Autorisierungsmodelle über RBAC hinaus ergänzen.
- [ ] Eigenes Architekturbild für 2.1 erstellen (keine Abbildung aus [1]/[2] kopieren).
