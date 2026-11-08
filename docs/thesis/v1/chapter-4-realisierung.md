# 4 Realisierung

Dieses Kapitel beschreibt, wie die funktionalen (FA) und nicht-funktionalen Anforderungen (NFA) aus Kapitel 3 im neuen Backend *design-daas-backend-java* umgesetzt wurden. Es beginnt mit den Architekturentscheidungen, die für alle Module gelten (4.1). Danach folgen die vier fachlichen Module *auth*, *permission*, *user* und *desktop* (4.2 bis 4.5) und die übergreifenden Themen, die sie verbinden (4.6). Den Abschluss bilden Demonstrator (4.7), Tests (4.8) und Dokumentation (4.9). Alle Codeausschnitte stammen aus dem Repository *design-daas-backend-java* in Version 2.0.0 und sind teilweise gekürzt. Vergleiche mit dem alten Backend beziehen sich auf *design-daas-application-backend*.

## 4.1 Architekturentscheidungen

Bevor die einzelnen Module beschrieben werden, klärt dieser Abschnitt die Entscheidungen, die allen Modulen zugrunde liegen: wie der Quellcode gegliedert ist (4.1.2), wie diese Gliederung automatisch durchgesetzt wird (4.1.3), wie die Authentifizierung aufgebaut ist (4.1.4) und wie das Datenbankschema verwaltet wird (4.1.5). Abschnitt 4.1.6 fasst die Entscheidungen in einer Tabelle zusammen und ordnet sie den Anforderungen aus Kapitel 3 zu.

### 4.1.1 Ausgangslage und Entscheidungsrahmen

Die Anforderungen aus Kapitel 3 verlangen zweierlei. Erstens muss sich das neue Backend nach außen so verhalten wie das alte (NFA-2, NFA-3), weil Frontend und DaaS-Anwendung nicht Gegenstand dieser Arbeit sind und ohne fachliche Änderungen weiterlaufen sollen. Zweitens soll es gerade die Mängel des alten Backends beheben, also die schlechte Wartbarkeit und die fehlende Dokumentation. Diese beiden Ziele bestimmen die folgenden Architekturentscheidungen. Zu jeder Entscheidung wird deshalb auch begründet, welchem der beiden Ziele sie dient.

### 4.1.2 Modulare Gliederung nach fachlicher Domäne

Das alte PHP-Backend ist bereits in fachliche Module gegliedert (Verzeichnisse *src/Main*, *src/Util*, *src/OAuth2*, *src/User*, *src/Desktop* und *src/Permissions*), jedes mit der inneren Schichtung *Model → Repository → Service → Controller*. Das neue Backend übernimmt diese Gliederung, benennt die Module aber um und fasst sie zu vier fachlichen Paketen zusammen, die den Funktionsbereichen aus Abschnitt 3.2.1 entsprechen:

```text
de.frauas.design.backend
├── auth        (Token-Ausgabe/-Prüfung, Sessions, Lockout)
├── permission    (Berechtigungskaskade)
├── user        (Nutzer, Admins, User-Groups, Registrierung)
├── desktop    (Desktops, Desktop-Groups)
├── bootstrap    (Start-Tasks, z. B. Default-Admin)
├── config    (Spring-Konfiguration)
└── shared    (DTOs, Exceptions, Security, Utils)
```

*Code 4.1: Paketdiagramm der vier Fachmodule und des Querschnittscodes*

Jedes fachliche Paket ist intern gleich aufgebaut: *controller → service → repository → model*, ergänzt um eigene Unterpakete für *dto* und *exception*. Diese Struktur hat zwei Vorteile. Sie entspricht der bekannten Struktur des PHP-Backends, sodass sich die Bedeutung der fachlichen Logik beim Portieren nicht unbemerkt verschiebt. Zugleich ist sie im Java- und Spring-Umfeld üblich und deshalb leicht nachzuvollziehen.

### 4.1.3 Automatisierte Durchsetzung der Architektur mit ArchUnit

Die größte Schwäche des alten Backends ist seine schlechte Wartbarkeit. Damit die neue Architektur nicht nach und nach verwässert, etwa weil ein Controller direkt ein Repository aufruft, ist die Schichtenregel nicht nur dokumentiert. Sie ist als ausführbarer Test formuliert, der bei jedem Build läuft:

```java
@ArchTest
static final ArchRule layerDependencies = layeredArchitecture()
    .consideringAllDependencies()
    .layer("Controllers").definedBy("de.frauas.design.backend..controller..")
    .layer("Services").definedBy("de.frauas.design.backend..service..")
    .layer("Repositories").definedBy("de.frauas.design.backend..repository..")
    .whereLayer("Controllers")
        .mayNotBeAccessedByAnyLayer()
    .whereLayer("Services")
        .mayOnlyBeAccessedByLayers("Controllers", "Config", "Services", "Bootstrap")
    .whereLayer("Repositories")
        .mayOnlyBeAccessedByLayers("Services", "Config", "Bootstrap");
```

*Code 4.2: ArchUnit-Regel zur Schichtenarchitektur (gekürzt)*

Weitere Regeln in derselben Testklasse *ArchitectureTest* prüfen Namenskonventionen: Klassen mit *@Service* müssen auf *Service* oder *ServiceImpl* enden, Klassen mit *@Repository* auf *Repository*. Außerdem müssen Services ihre Abhängigkeiten über den Konstruktor erhalten statt über Felder, *java.util.logging* und *System.out* sind verboten, und das Paket *shared* darf von keinem Fachmodul abhängen, damit keine zirkulären Abhängigkeiten entstehen. Insgesamt umfasst *ArchitectureTest* 19 Regeln. Verstößt eine Änderung gegen eine davon, schlägt *mvn test* fehl. Die Architektur ist damit keine bloße Absicht mehr, sondern eine Bedingung, ohne die der Build nicht gelingt. So wird NFA-8 umgesetzt: Die interne Schichtung wird automatisch geprüft, nicht nur per Konvention eingehalten.

Auch das alte PHP-Backend nutzt schon ein vergleichbares Werkzeug. Die Testklasse *BasicArchitectureTest.php* enthält drei *PHPat*-Regeln. Sie schützen den Namensraum *Application\Core* vor Zugriffen von außen und stellen sicher, dass *Application\Migrations* und *Application\Test* nicht von anderen Namensräumen verwendet werden. Neu ist also nicht, dass es Architekturtests gibt. Neu ist, dass sie die innere Schichtung jedes Moduls prüfen, die im PHP-Code nur per Konvention galt, und dass sie mit einer festen Mindest-Testabdeckung zusammenwirken (Abschnitt 4.8.1). Beides zusammen sichert die Qualität deutlich enger ab als im alten System.

### 4.1.4 Zustandslose Authentifizierung statt vollem Autorisierungsserver

Das alte Backend verwendet die Bibliothek *league/oauth2-server* mit dem Password Grant. Für das neue Backend wurde geprüft, ob sich ein vollständiger Autorisierungsserver mit *spring-security-oauth2-authorization-server* lohnt. Benötigt werden aber nur zwei Grant-Typen, *password* und *refresh_token*, und *POST /oauth2/user/token* nimmt die Zugangsdaten direkt als Formularparameter entgegen, wie es der bisherige Vertrag mit dem Frontend vorsieht. Deshalb fiel die Wahl auf eine schlanke eigene Lösung: einen selbst geschriebenen Aussteller für RS256-JWTs (*TokenIssuer*), dessen Token Spring Security als Resource Server prüft (*JwtDecoder*). So entfällt der Konfigurationsaufwand eines vollständigen Autorisierungsservers für einen Vertrag, der ohnehin nur zwei Grant-Typen kennt. In Kauf genommen wird dabei, dass RFC 9700 vom Password Grant abrät (Abschnitt 2.2.1). Er bleibt nur deshalb erhalten, weil Frontend und DaaS-Anwendung ihn verwenden (Abschnitt 5.4).

### 4.1.5 Schema ausschließlich über Flyway

Hibernate läuft im Modus **ddl-auto: validate** und erzeugt das Schema nie selbst. Alle Änderungen am Schema laufen über versionierte Flyway-Migrationen (*V1__init.sql* bis *V4__*.sql*). Dadurch ist jede Schemaänderung ausdrücklich im Code sichtbar und lässt sich prüfen. Zugleich werden Überraschungen durch automatisch erzeugtes Schema vermieden, das in Entwicklungs-, Test- und Produktivumgebung unterschiedlich ausfallen könnte. Das dient der Wart- und Testbarkeit (NFA-8 und NFA-9). Flyway übernimmt dabei die Aufgabe, die im alten Backend Doctrine Migrations erfüllt hat (Tabelle 3.1); es wird also kein neues Konzept eingeführt, sondern ein vorhandenes gleichwertig ersetzt.

### 4.1.6 Zusammenfassung der Architekturentscheidungen

Tabelle 4.1 fasst die Entscheidungen aus den Abschnitten 4.1.2 bis 4.1.5 zusammen und ordnet jede einer Anforderung aus Kapitel 3 zu. Die Abschnitte 4.2 bis 4.6 zeigen, wie sie im Code umgesetzt sind.

*Tabelle 4.1: Zentrale Architekturentscheidungen und ihr Anforderungsbezug*

| **Entscheidung** | **Begründung** | **NFA** |
|---|---|---|
| Vier fachliche Module, intern einheitlich geschichtet | Struktur des alten Backends beibehalten, Portierungsrisiko senken | NFA-2, <br> NFA-3 |
| Schichtenregeln als ArchUnit-Build-Gate | Architektur nicht nur Konvention, sondern erzwungen | NFA-8 |
| Eigener RS256-JWT-Aussteller statt vollem Autorisierungsserver | Nur zwei Grant-Typen nötig, geringerer Konfigurationsaufwand | NFA-7 |
| Flyway statt Hibernate-Auto-DDL | Nachvollziehbare, reviewbare Schemaänderungen | NFA-8, <br> NFA-9 |
| JaCoCo-Mindestabdeckung als Build-Gate | Testabdeckung ist kein optionales Ziel, sondern Build-Voraussetzung | NFA-9 |

## 4.2 Authentifizierung

Das Modul *auth* setzt die Anforderungen FA-1 bis FA-4 um: Anmeldung mit Benutzername und Passwort (FA-1), Erneuerung von Token (FA-2), Abmeldung (FA-3) und Abfrage der Sitzung (FA-4). Die folgenden Abschnitte folgen dem Weg einer Anfrage. Sie beginnen bei der Unterscheidung der Grant-Typen (4.2.1) und führen über die Passwortprüfung mit ihren Sicherheitsmaßnahmen (4.2.2), die Ausstellung der Token (4.2.3) und ihre Erneuerung (4.2.4) bis zu Abmeldung und Sitzungsabfrage (4.2.5). Zum Schluss werden die Ratenbegrenzung (4.2.6) und ihre threadsichere Umsetzung (4.2.7) beschrieben.

### 4.2.1 Unterscheidung der Grant-Typen

*TokenController* nimmt Anfragen an *POST /oauth2/user/token* entgegen und erzeugt daraus ein typisiertes *GrantRequest*, entweder ein *PasswordGrantRequest* oder ein *RefreshGrantRequest*. Die eigentliche Logik liegt in *TokenService.grantToken*, das mit einem *switch* über Pattern Matching zur passenden Variante verzweigt:

```java
@Transactional(noRollbackFor = TokenGrantException.class)
public TokenResponseDto grantToken(GrantRequest request) {
    return switch (request) {
        case PasswordGrantRequest r
        -> passwordGrant(r.username(), r.password(), r.scope());
        case RefreshGrantRequest r
        -> refreshGrant(r.refreshToken());
    };
}
```

*Code 4.3: Grant-Dispatch in TokenService.grantToken*

Weil *GrantRequest* als *sealed interface* definiert ist, prüft der Java-Compiler, ob dieses *switch* alle Fälle abdeckt. Käme ein dritter Grant-Typ hinzu, ohne dass hier ein Fall ergänzt wird, ließe sich der Code nicht mehr übersetzen. Ein Test, der prüft, ob ein neuer Grant-Typ auch hier berücksichtigt wurde, ist damit überflüssig; der Compiler erzwingt es. Das zeigt, wie Sprachmittel von Java 21 zur Wartbarkeit beitragen. Im PHP-Code wäre das mangels vergleichbarer Sprachmittel nicht möglich gewesen. Der Token-Endpunkt akzeptiert, wie in RFC 6749 vorgesehen, nur formularkodierte Parameter (*application/x-www-form-urlencoded*). Anfragen mit einem JSON-Rumpf lehnt er mit dem Statuscode 415 ab.

### 4.2.2 Password Grant: Ablauf und Sicherheitsmaßnahmen

*TokenService.passwordGrant* setzt mehrere Sicherheitsmaßnahmen um, die über die OAuth2-Spezifikation hinausgehen. Zuerst wird geprüft, ob es überhaupt einen Nutzer mit dem angegebenen Benutzernamen gibt:

```java
var userOpt = userRepository.findByEmail(username);

if (userOpt.isEmpty()) {
    // always run BCrypt to prevent timing-based user enumeration
    passwordEncoder.matches(password, dummyHash());
    log.warn("passwordGrant — unknown user={}", LogMasking.maskEmail(username));
    throw new InvalidCredentialsException();
}
```

*Code 4.4: Schutz vor zeitbasierter Benutzer-Enumeration*

Ist der Benutzername unbekannt, wird trotzdem ein BCrypt-Vergleich ausgeführt, und zwar gegen einen Dummy-Hash. Dieser wird beim Start mit demselben PasswordEncoder und damit mit demselben Kostenfaktor erzeugt wie echte Passwort-Hashes. Ohne diesen Schritt würde eine Anfrage mit unbekanntem Benutzernamen messbar schneller scheitern als eine mit bekanntem Benutzernamen und falschem Passwort, denn BCrypt ist absichtlich langsam. Ein Angreifer könnte so über die Antwortzeit herausfinden, welche Benutzernamen existieren. Im alten PHP-Backend ist eine solche Maßnahme nicht dokumentiert; sie ist eine Verbesserung gegenüber dem bisherigen Stand (NFA-4).

Danach prüft *AccountLockoutService.assertNotLocked*, ob das Konto gerade gesperrt ist, noch bevor das Passwort verglichen wird. Ist das Passwort falsch, zählt *AccountLockoutService.recordFailedAttempt* den Fehlversuch. Nach fünf Fehlversuchen in Folge wird das Konto für 15 Minuten gesperrt:

```java
private static final int MAX_FAILED_ATTEMPTS = 5;
private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

public void recordFailedAttempt(BaseUser user) {
    int attempts = user.getFailedLoginAttempts() + 1;
    user.setFailedLoginAttempts(attempts);
    if (attempts >= MAX_FAILED_ATTEMPTS) {
        user.setLockedUntil(Instant.now(clock).plus(LOCKOUT_DURATION));
    }
    userRepository.save(user);
}
```

*Code 4.5: Zählung von Fehlversuchen und Kontosperrung*

Eine Besonderheit ist im Javadoc der Klasse dokumentiert: Der Zähler für Fehlversuche wird nur bei einer erfolgreichen Anmeldung zurückgesetzt (*resetOnSuccess*), nicht automatisch nach Ablauf der Sperre. Läuft die Sperre ab und schlägt der nächste Versuch wieder fehl, wird das Konto sofort erneut für die volle Dauer gesperrt, weil der Zähler nie heruntergezählt wurde. Dieses Verhalten ist absichtlich streng gewählt. Welche Folgen es im Betrieb hat, beschreibt Abschnitt 4.8.4 als bekannte Einschränkung.

Sind Sperre und Passwort in Ordnung, wird geprüft, ob das Konto aktiviert ist. Anschließend gleicht *ScopeResolver.resolveAuthorizedScope* den angeforderten Scope mit der Rolle des Nutzers ab. So kann ein normaler Nutzer sich nicht selbst Administratorrechte verschaffen, indem er *scope=admin* anfragt. Erst danach wird ein Token ausgestellt. Abbildung 4.1 fasst alle Prüfschritte und ihre Abbruchpfade zusammen.

![Abbildung 4.1: Prüfschritte beim Password Grant mit Abbruchpfaden (eigene Darstellung)](images/abb-4-1.png)

*Abbildung 4.1: Prüfschritte beim Password Grant mit Abbruchpfaden (eigene Darstellung)*

### 4.2.3 Tokenausstellung: RS256-JWT und opaker Refresh-Token

*TokenIssuer.issueAccessToken* erzeugt ein RS256-signiertes JWT mit einer eindeutigen **jti** (JWT-ID) und speichert diese *jti* zusätzlich in der Tabelle *access_tokens*:

```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .id(jti)
        .issuer(issuer)
        .subject(String.valueOf(user.getId()))
        .issuedAt(now)
        .expiresAt(now.plusSeconds(accessTokenTtlSeconds))
        .claim("email", user.getEmail())
        .claim("name", user.getName())
        .claim("scope", scope)
        .build();

JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
```

*Code 4.6: Aufbau und Signatur des Zugriffstokens*

Dass **RS256** verwendet wird, also ein asymmetrisches Verfahren, und nicht etwa HS256, hat einen praktischen Vorteil: Der öffentliche Schlüssel kann weitergegeben werden, ohne das Geheimnis zum Signieren preiszugeben. Das ist wichtig, falls künftig ein anderer Dienst Token nur prüfen, aber nicht ausstellen soll. Das Schlüsselpaar wird beim ersten Start erzeugt und im Verzeichnis *OAUTH_KEYS_DIR* gespeichert. Geht diese Datei verloren, werden alle zuvor ausgestellten Token ungültig. Zugriffstoken gelten standardmäßig 3600 Sekunden, Refresh-Token 30 Tage; beide Werte lassen sich über Umgebungsvariablen ändern.

Da JWTs an sich zustandslos sind und sich auf dem Server nicht widerrufen lassen, speichert das Backend jede ausgestellte *jti* zusätzlich mit einem *revoked*-Flag in der Tabelle *access_tokens*. Das ist ein Kompromiss zwischen Zustandslosigkeit (NFA-7) und Widerrufbarkeit (NFA-6). Das Refresh-Token ist dagegen kein JWT, sondern eine zufällige Zeichenkette aus 256 Bit in Hexadezimaldarstellung (*SecureRandom*). Es dient nur als Schlüssel in der Datenbank und muss selbst keine Angaben transportieren:

```java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();
...
byte[] bytes = new byte[32];
SECURE_RANDOM.nextBytes(bytes);
String tokenValue = HexFormat.of().formatHex(bytes);
```

*Code 4.7: Erzeugung des opaken Refresh-Tokens*

### 4.2.4 Refresh-Token-Rotation

Beim Refresh Grant wird nicht einfach ein neues Zugriffstoken zum bestehenden Refresh-Token ausgestellt. Stattdessen entsteht bei jeder Erneuerung auch ein neues Refresh-Token, und das alte wird widerrufen. Dieses Verfahren heißt Token-Rotation (*TokenService.refreshGrant*):

```java
String newAccessToken = tokenIssuer.issueAccessToken(user, grantedScope);
String newRefreshToken = tokenIssuer.createRefreshToken(user, grantedScope);

refreshTokenEntity.revoke();
refreshTokenRepository.save(refreshTokenEntity);
```

*Code 4.8: Refresh-Token-Rotation in TokenService.refreshGrant*

Das begrenzt den Schaden, falls ein Refresh-Token abgefangen wird, denn jedes Refresh-Token lässt sich nur einmal einlösen. Ein bereits verwendetes Token wird abgelehnt, weil es in der Datenbank als widerrufen markiert ist. Nicht umgesetzt ist dagegen, was RFC 9700 zusätzlich empfiehlt: bei einer erkannten Wiederverwendung alle Token zu widerrufen, die aus derselben Anmeldung hervorgegangen sind (Lodderstedt et al., 2025; Abschnitt 5.5). Außerdem prüft *refreshGrant* vor jeder Erneuerung, ob der Nutzer noch existiert und aktiviert ist (*userOpt.get().isEnabled()*). Ist das nicht der Fall, wird das Refresh-Token gelöscht und die Erneuerung abgelehnt. Ein deaktiviertes Konto erhält so kein neues Zugriffstoken mehr, auch wenn ein früher ausgestelltes Refresh-Token formal noch gültig wäre. Das bereits ausgestellte Zugriffstoken bleibt allerdings bis zu seinem Ablauf gültig.

### 4.2.5 Abmeldung und Abfrage der Sitzung

*POST /oauth2/user/logout* ruft *TokenRevocationService.logout* auf. Diese Methode markiert die *jti* des aktuellen Zugriffstokens und, falls mitgeschickt, auch das Refresh-Token als widerrufen (FA-3). Im alten PHP-Backend gibt es dafür kein Gegenstück (Abschnitt 3.2.2). Da der Widerruf über die *jti* ein neu eingeführter Mechanismus ist (Abschnitt 4.2.3), musste auch die Abmeldung neu entworfen werden.

Über *GET /oauth2/user/session* kann das Frontend prüfen, ob ein Nutzer angemeldet ist, ohne das Token selbst auswerten zu müssen. Dazu liest *JwtUserResolver* die Nutzer-ID aus dem bereits geprüften JWT (FA-4). Die Antwort enthält neben Nutzer-ID und Name auch die aktuelle Rolle des Nutzers. Sie wird bei jeder Abfrage aus der Datenbank gelesen, sodass eine geänderte Rolle sofort auch für bereits ausgestellte Token gilt. Die DaaS-Anwendung nutzt denselben Endpunkt, um das Bearer-Token eines Nutzers zu prüfen.

Abbildung 4.2 zeigt den gesamten Lebenszyklus der Token: Ausstellung, Prüfung bei jeder Anfrage, Erneuerung mit Rotation, Widerruf und schließlich das zeitgesteuerte Löschen nicht mehr benötigter Einträge (Abschnitt 4.6.6).

![Abbildung 4.2: Lebenszyklus von Zugriffs- und Refresh-Token (eigene Darstellung)](images/abb-4-2.png)

*Abbildung 4.2: Lebenszyklus von Zugriffs- und Refresh-Token (eigene Darstellung)*

### 4.2.6 Ratenbegrenzung sensibler Endpunkte

Zusätzlich zur Kontosperre begrenzt der *RateLimitingFilter*, wie viele Anfragen eine Client-IP an */oauth2/user/token*, */user* (Registrierung), */user/validate_email* und */user/resend_code* stellen darf (NFA-5). Der Filter ist mit *@Order(-200)* so eingeordnet, dass er vor der Filterkette von Spring Security (*-100*) läuft. Zu viele Anfragen werden also abgewiesen, bevor überhaupt Authentifizierungslogik ausgeführt wird:

```java
@Component
@Order(-200)
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        String clientIp = clientIpResolver.resolve(request);
        int limit = limitFor(path);

        if (rateLimiter.tryConsume(clientIp, path, limit)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        // ... JSON error body
    }
}
```

*Code 4.9: RateLimitingFilter (gekürzt)*

Die Client-IP ermittelt der *ClientIpResolver*. Den Header *X-Forwarded-For* wertet er nur aus, wenn die Anfrage von einem als vertrauenswürdig eingetragenen Proxy stammt (*RATE_LIMIT_TRUSTED_PROXIES*). Sonst könnte ein Angreifer die Ratenbegrenzung umgehen, indem er diesen Header fälscht. Die vier Limits für *auth-token*, *register*, *validate-email* und *resend-code* lassen sich getrennt einstellen; voreingestellt sind 20, 5, 10 und 3 Anfragen pro Minute (*application.yml*). Die Werte unterscheiden sich, weil auch die Missbrauchsszenarien verschieden sind: Die Anmeldung wird im Alltag viel häufiger aufgerufen als das Anfordern eines neuen Bestätigungscodes.

### 4.2.7 Threadsichere Ratenbegrenzung ohne Sperren

Gezählt wird je Client-IP und Pfad in der Klasse *TokenBucket*, von der *RateLimiter* für jede Kombination aus IP und Pfad genau eine Instanz verwaltet. Da vom selben Client mehrere Anfragen gleichzeitig eintreffen können, muss das Zählen threadsicher sein. Statt einer Sperre mit *synchronized* werden der Zeitpunkt der letzten Auffüllung und die Zahl der verbleibenden Token in einen einzigen *long*-Wert gepackt und mit *AtomicLong.compareAndSet* aktualisiert:

```java
boolean tryConsume() {
    while (true) {
        long current = state.get();
        long lastRefillSeconds = current >>> 32;
        long storedTokens = current & 0xFFFFFFFFL;

        long elapsedMillis = Math.max(0, nowMillis - lastRefillSeconds * 1_000L);
        long refilledTokens = (long) (elapsedMillis * refillTokensPerMillisecond);
        long availableTokens = Math.min(capacity, storedTokens + refilledTokens);

        if (availableTokens <= 0) {
            return false;
        }
        long nextState = (nowSeconds << 32) | (availableTokens - 1);
        if (state.compareAndSet(current, nextState)) {
            return true;
        }
        // CAS failed: another thread updated state concurrently, retry
    }
}
```

*Code 4.10: Sperrenfreie Aktualisierung in TokenBucket.tryConsume (gekürzt)*

Zeitstempel und Tokenzahl liegen in den oberen und unteren 32 Bit desselben *long*-Werts. Dadurch können die beiden Werte nie in einem inkonsistenten Zwischenzustand gelesen werden, und für einen Codepfad, der bei jeder Anfrage an einen der vier begrenzten Endpunkte durchlaufen wird, ist keine Sperre nötig. Schlägt *compareAndSet* fehl, weil ein anderer Thread den Zustand inzwischen geändert hat, wird die Schleife einfach wiederholt, statt auf eine Sperre zu warten (optimistische Nebenläufigkeitskontrolle). Eine Einschränkung ist im Code dokumentiert: Jeder erfolgreiche Aufruf setzt den Auffüllzeitpunkt auf die aktuelle, auf ganze Sekunden gerundete Zeit und verwirft angefangene Token. Bei gleichmäßigem Verkehr füllt sich der Eimer deshalb etwas langsamer auf als eingestellt. Für die Abwehr von Missbrauch ist das unkritisch, weil die Begrenzung dadurch nur strenger wird. Außerdem liegen die Zähler im Arbeitsspeicher der jeweiligen Instanz. Laufen mehrere Instanzen, gelten die Limits deshalb je Instanz (Abschnitt 5.4).

## 4.3 Autorisierung mit der Berechtigungskaskade

Abschnitt 4.2 klärt, wer ein Nutzer ist. Das Modul *permission* entscheidet, was dieser Nutzer tun darf. Es setzt FA-13 um, die Berechtigungsprüfung für jede Funktion der DaaS-Anwendung, und ist damit der sicherheitskritischste Teil des Backends: Die DaaS-Anwendung fragt diese Entscheidung vor jedem Funktionsaufruf ab. Die folgenden Abschnitte beschreiben zunächst, wie die Kaskade portiert wurde (4.3.1). Danach geht es um zwei Fragen, die im alten Code nicht geklärt waren: widersprüchliche Regeln innerhalb einer Stufe (4.3.2) und fehlerhafte Eingaben (4.3.3). Abschnitt 4.3.4 beschreibt eine Optimierung, Abschnitt 4.3.5 die Tests der Kaskade.

Gemessen an den Modellen aus Abschnitt 2.3 verbindet das Backend zwei Ebenen. Die grobe Ebene ist ein einfaches rollenbasiertes Modell im Sinne von Flat RBAC. Jeder Nutzer hat genau eine der Rollen *user* oder *admin*; sie steht als Scope im Token und wird von Spring Security geprüft. Die feine Ebene ist die vierstufige Kaskade. Ihre Regeln erlauben oder verweigern einzelnen Nutzern oder Nutzergruppen den Zugriff auf einzelne Funktionen oder Gruppen von Funktionen. Sie ähnelt damit Zugriffskontrolllisten, deren Einträge sich auf Nutzergruppen als rollenähnliche Bündel beziehen. Attributbasierte Regeln im Sinne von ABAC, etwa abhängig von Uhrzeit oder Netzwerk, gibt es nicht. In den Begriffen des XACML-Modells ist der *PermissionService* der Policy Decision Point. Durchgesetzt wird die Entscheidung von der DaaS-Anwendung als Policy Enforcement Point, denn sie führt eine Aktion nur aus, wenn der Zugriff erlaubt ist.

### 4.3.1 Portierung der PHP-Logik

Das alte Backend entscheidet in *PermissionCalculationService::getPermission()* anhand einer vierstufigen Rangfolge. Im PHP-Code (*PermissionCalculationService.php*) erscheinen die vier Stufen als Folge früher Rückgaben. Zuerst wird eine direkte Regel für Nutzer und Endpunkt gesucht, danach in einer Schleife über alle Gruppen des Nutzers eine Regel für Gruppe und Endpunkt. Es folgt eine Regel für Nutzer und Endpunktgruppe und zuletzt, wieder in einer Schleife über die Gruppen, eine Regel für Gruppe und Endpunktgruppe. Greift keine Regel, gilt **AccessEnum::DENY**. Diese Reihenfolge musste exakt erhalten bleiben, weil die DaaS-Anwendung ihre Berechtigungsabfragen unverändert an das neue Backend stellt (NFA-2, NFA-3). Die neue Methode *PermissionService.checkPermission* bildet die vier Stufen genau nach, verwendet aber *Optional#or* statt verschachtelter *foreach*-Schleifen:

```java
Optional<Boolean> allow = checkDirectUserRule(userId, functionName)
        .or(() -> checkUserGroupRule(groupIdsSupplier.get(), functionName))
        .or(() -> checkUserEndpointGroupRule(userId, functionName))
        .or(() -> checkUserGroupEndpointGroupRule(groupIdsSupplier.get(), functionName));

if (allow.isEmpty()) {
    return PermissionResultDto.deny(userId, userName);
}
```

*Code 4.11: Vierstufige Berechtigungskaskade in PermissionService.checkPermission*

Die Verkettung mit *Optional#or* bildet die Kaskade direkt im Code ab. Jede Stufe liefert entweder ein Ergebnis, dann endet die Kette, oder *Optional.empty()*, dann kommt die nächste Stufe an die Reihe. Liefert keine Stufe ein Ergebnis, wird der Zugriff verweigert (fail-closed, NFA-1). Die vier Stufen entsprechen den vier Zuordnungsentitäten *EndpointUserAccess*, *EndpointUserGroupAccess*, *EndpointGroupUserAccess* und *EndpointGroupUserGroupAccess* aus Abbildung 3.2, die im neuen Backend unter denselben Namen als JPA-Entitäten existieren.

### 4.3.2 Konfliktauflösung innerhalb einer Stufe

Im alten Code ungeklärt ist die Frage, was geschieht, wenn innerhalb einer Stufe mehrere Regeln zugleich zutreffen, etwa weil ein Nutzer zwei Gruppen mit widersprüchlichen Regeln für dieselbe Funktion angehört. Die neue Implementierung entscheidet diesen Fall eindeutig mit *allMatch*: Ein einziges ausdrückliches *deny* wiegt schwerer als beliebig viele *allow*-Einträge.

```java
private <T> Optional<Boolean> resolveStepDecision(
        List<T> matches, Function<T, Boolean> allowExtractor, String stepName, String principalDescription) {
    if (matches.isEmpty()) {
        return Optional.empty();
    }
    boolean allow = matches.stream().map(allowExtractor).allMatch(Boolean::booleanValue);
    if (matches.size() > 1) {
        log.warn("checkPermission — {} matched {} rules for {}, resolving result={} (explicit deny wins)",
                stepName, matches.size(), principalDescription, allow ? "allow" : "deny");
    }
    return Optional.of(allow);
}
```

*Code 4.12: Konfliktauflösung innerhalb einer Kaskadenstufe*

Diese Regel, nach der eine ausdrückliche Verweigerung immer gewinnt, ist eine Neuerung gegenüber dem alten Verhalten und im Javadoc der Klasse dokumentiert. Im PHP-Original (*PermissionCalculationService::getPermission*) durchläuft eine *foreach*-Schleife die Gruppen des Nutzers und gibt beim ersten Treffer sofort zurück (*return $endpointUserGroupAccess->relation();*). Gehört ein Nutzer zwei Gruppen mit widersprüchlichen Regeln an, hängt das Ergebnis dort also von der Reihenfolge ab, in der die Gruppen geliefert werden, und diese ist nirgends festgelegt. Das neue Backend ersetzt dieses zufällige Verhalten durch eine eindeutige Regel, die im Zweifel zugunsten der Sicherheit entscheidet. So kann eine großzügige Gruppenregel keine Sperre aushebeln, die an anderer Stelle absichtlich gesetzt wurde. Die Abweichung vom PHP-Verhalten wurde bei der Analyse in AP2 erkannt und hier ausdrücklich festgelegt. Ein *log.warn*-Aufruf bei mehrdeutigen Treffern macht solche Fälle im Betrieb sichtbar, ohne die Entscheidung zu verändern.

### 4.3.3 Fail-closed bei fehlerhafter Eingabe

Unbekannte Nutzer-IDs, *null*-Werte und leere Funktionsnamen werden abgefangen, bevor die Datenbank überhaupt befragt wird, und führen direkt zu *deny*:

```java
if (userId == null || functionName == null || functionName.isBlank()) {
    return PermissionResultDto.deny(userId != null ? userId : UNRESOLVED_USER_ID, UNKNOWN_USER_NAME);
}

Optional<BaseUser> user = userRepository.findById(userId);
if (user.isEmpty()) {
    return PermissionResultDto.deny(userId, UNKNOWN_USER_NAME);
}
```

*Code 4.13: Fail-closed-Behandlung ungültiger Eingaben*

Das spart Datenbankzugriffe bei offensichtlich ungültigen Anfragen. Vor allem aber stellt es sicher, dass ein Fehler beim Aufrufer, etwa eine vergessene Nutzer-ID, nie versehentlich zu einem *allow* führt. Die Methode prüft in zwei Schritten: zuerst, ob die Eingabe formal gültig ist, und erst danach, ob der Nutzer existiert. Beide Fälle enden mit *deny*, werden aber unterschiedlich protokolliert, mit *warn* beziehungsweise *info*. Ein unbekannter Nutzer kommt im normalen Betrieb eher vor als eine formal ungültige Anfrage.

### 4.3.4 Caching der Gruppenzugehörigkeit innerhalb einer Prüfung

Die Stufen 2 und 4 benötigen beide die Gruppen des Nutzers. Diese werden deshalb erst geladen, wenn sie gebraucht werden, und höchstens einmal je Anfrage (*Supplier* mit internem Zwischenspeicher). Entscheidet bereits Stufe 1, entfällt die Datenbankabfrage ganz:

```java
Supplier<List<Integer>> groupIdsSupplier = new Supplier<>() {
    private List<Integer> cachedGroupIds;

    public List<Integer> get() {
        if (cachedGroupIds == null) {
            cachedGroupIds = userRepository.findGroupIdsByUserId(userId);
        }
        return cachedGroupIds;
    }
};
```

*Code 4.14: Bedarfsgesteuertes Laden der Gruppenzugehörigkeit*

### 4.3.5 Testbarkeit der Kaskade

Weil die Berechtigungskaskade der sicherheitskritischste Teil des Backends ist und über jeden Funktionsaufruf der DaaS-Anwendung entscheidet (NFA-1, NFA-2), ist sie auch am gründlichsten getestet. *PermissionServiceTest* prüft jede der vier Stufen einzeln, den Fall ohne passende Regel, die Konfliktauflösung bei mehreren Treffern in einer Stufe (Abschnitt 4.3.2) und die Sonderfälle aus Abschnitt 4.3.3, also fehlende Nutzer-ID, leeren Funktionsnamen und unbekannten Nutzer. *PermissionControllerIntegrationTest* ergänzt Ende-zu-Ende-Tests gegen den echten HTTP-Endpunkt (Abschnitt 4.8.2).

## 4.4 Benutzerverwaltung

Das Modul *user* setzt FA-5 bis FA-9 um: die Registrierung mit Bestätigung der E-Mail-Adresse (FA-5), die Verwaltung von Nutzern (FA-6) und Administratoren (FA-7) durch Administratoren, die Nutzergruppen (FA-8) und die Anfrage zur Freischaltung von Anwendungen (FA-9). Die Abschnitte 4.4.1 und 4.4.2 folgen dem Lebenszyklus eines Kontos von der Registrierung bis zur Verwaltung. Abschnitt 4.4.3 zeigt, wie Nutzergruppen die Module *permission* und *desktop* verbinden. Die Abschnitte 4.4.4 und 4.4.5 beschreiben zwei weitere Funktionen des Moduls.

### 4.4.1 Registrierung mit E-Mail-Verifikation

*UserService.createUser* legt einen Nutzer zunächst deaktiviert an, erzeugt einen achtstelligen hexadezimalen Registrierungscode, der 24 Stunden gilt, und verschickt ihn per E-Mail:

```java
private static final String REGISTRATION_CODE_CHARS = "ABCDEF0123456789";
private static final int REGISTRATION_CODE_LENGTH = 8;

private String generateRegistrationCode() {
    StringBuilder sb = new StringBuilder(REGISTRATION_CODE_LENGTH);
    for (int i = 0; i < REGISTRATION_CODE_LENGTH; i++) {
        sb.append(REGISTRATION_CODE_CHARS.charAt(SECURE_RANDOM.nextInt(REGISTRATION_CODE_CHARS.length())));
    }
    return sb.toString();
}

```

*Code 4.15: Erzeugung des Registrierungscodes*

Die E-Mail wird erst nach dem Commit der Datenbanktransaktion versendet (*runAfterCommit*), nicht innerhalb der Transaktion:

```java
private void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    } else {
        action.run();
    }
}
```

*Code 4.16: E-Mail-Versand nach erfolgreichem Commit*

Das hat zwei Gründe. Ein langsamer SMTP-Server hält so keine Datenbanktransaktion und deren Sperren unnötig lange offen. Außerdem wird keine E-Mail verschickt, wenn die Transaktion am Ende scheitert und zurückgerollt wird; sonst erhielte ein Nutzer einen Code für ein Konto, das gar nicht existiert. Dasselbe Muster verwenden *requestApplication* (Abschnitt 4.4.4) und *resendRegistrationCode*.

*UserService.validateEmail* prüft Code und Ablaufzeit und aktiviert erst danach das Konto:

```java
if (user.getRegistrationCode() == null || !user.getRegistrationCode().equals(registrationCode)) {
    return false;
}
if (user.getRegistrationCodeTimeout() != null
        && Instant.now(clock).isAfter(user.getRegistrationCodeTimeout())) {
    return false;
}
user.setEnabled(true);
user.setRegistrationUsedMoment(Instant.now(clock));
user.setRegistrationCode(null);
userRepository.save(user);
```

*Code 4.17: Prüfung des Registrierungscodes in UserService.validateEmail*

Nach erfolgreicher Bestätigung wird der Code gelöscht (*setRegistrationCode(null)*), damit er nicht ein zweites Mal verwendet werden kann. Der Zeitpunkt der Bestätigung wird dauerhaft gespeichert (*registrationUsedMoment*). Mit 16 möglichen Zeichen an acht Stellen gibt es rund 4,3 Milliarden mögliche Codes (32 Bit). Zusammen mit der Gültigkeit von 24 Stunden und der Begrenzung auf zehn Prüfanfragen pro Minute und IP-Adresse (Abschnitt 4.2.6) ist es sehr unwahrscheinlich, einen Code zu erraten.

*resendRegistrationCode* gibt bewusst wenig preis. Bei unbekannten, bereits bestätigten oder Administratorkonten passiert nach außen nichts Sichtbares, es gibt weder Fehler noch Bestätigung. So lässt sich der Endpunkt nicht nutzen, um herauszufinden, welche E-Mail-Adressen registriert sind, ähnlich wie bei der Maßnahme gegen Zeitmessung in Abschnitt 4.2.2. Der bisherige Code wird durch einen neuen ersetzt, sodass je Konto immer höchstens ein Code gültig ist.

### 4.4.2 Admin-initiierte Verwaltung

Über *AdminController* und *AdminService* können Administratoren weitere Administratorkonten anlegen (nur mit *SCOPE_admin*). *UserController* und *UserService* erlauben es, normale Nutzer aufzulisten, teilweise zu ändern (*PATCH*), zu aktivieren und zu deaktivieren. Ändert sich das Passwort eines Nutzers oder wird sein Konto deaktiviert, veröffentlicht der Service ein *UserSessionsInvalidatedEvent*:

```java
if (request.getPassword() != null) {
    accountValidator.validatePassword(request.getPassword());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(id));
}
public UserDto disableUser(Integer id) {
    User user = getUserOrThrow(id);
    user.setEnabled(false);
    userRepository.save(user);
    eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(id));
    return UserDto.from(user);
}
```

*Code 4.18: Invalidierung aller Sitzungen bei Passwortänderung und Deaktivierung*

Das Ereignis trennt die Nutzerverwaltung vom Widerruf der Token. *UserService* muss nicht wissen, wie Sitzungen ungültig gemacht werden; das übernimmt ein Listener im Modul *auth*. Die Schichtentrennung aus Abschnitt 4.1 gilt damit nicht nur für direkte Methodenaufrufe, sondern auch für Ereignisse. Bei einer Passwortänderung wie bei einer Deaktivierung ist die Wirkung dieselbe: Alle bisher ausgestellten Zugriffs- und Refresh-Token des Nutzers werden ungültig. Wer zuvor ein Token gestohlen hat, kann es nach dem Eingreifen eines Administrators nicht mehr verwenden.

### 4.4.3 User-Groups als Bindeglied zum Berechtigungs- und Desktop-Modul

*UserGroup* verbindet Benutzerverwaltung, Berechtigungskaskade (Stufen 2 und 4, Abschnitt 4.3) und Desktop-Inventar (Abschnitt 4.5): Eine Gruppe kann sowohl Berechtigungsregeln als auch Zuordnungen zu Desktop-Gruppen haben. *UserGroupService* bietet die üblichen Operationen zum Anlegen, Lesen, Ändern und Löschen von Gruppen. Die Verknüpfung mit Desktop-Gruppen liegt dagegen im Modul *desktop* (Abschnitt 4.5), damit keine gegenseitige Abhängigkeit zwischen *user* und *desktop* entsteht. Eine automatische Prüfung auf solche zyklischen Abhängigkeiten enthalten die ArchUnit-Regeln bisher nicht; sie wäre eine sinnvolle Erweiterung. Die Beziehungen entsprechen weitgehend dem Datenmodell des alten Backends aus Abbildung 3.2.

### 4.4.4 Anwendungsfreischaltung (FA-9)

Schon das alte Backend bietet mit *UserService::requestApplication()* einen Ablauf, über den ein Nutzer Zugriff auf eine weitere Anwendung anfragen kann, die er im Frontend sieht. Daraufhin wird eine E-Mail an das zentrale Postfach des Systems verschickt, deren Antwortadresse die E-Mail-Adresse des Nutzers ist. Dieser Ablauf wurde unverändert als eigener Anwendungsfall übernommen (FA-9, Abschnitt 3.2) und in *UserService.requestApplication* portiert:

```java
/**
 * Sends an application-request email on behalf of the authenticated user.
 * Mirrors PHP UserService::requestApplication().
 */
@Transactional(readOnly = true)
public void requestApplication(Integer userId, String application) {
    BaseUser baseUser = userRepository.findById(userId).orElseThrow(() -> new AccountNotFoundException(userId));
    String email = baseUser.getEmail();
    String name = baseUser.getName();
    String id = String.valueOf(baseUser.getId());
    runAfterCommit(() -> mailService.sendApplicationRequestEmail(email, name, id, application));
}
```

*Code 4.19: Anfrage einer Anwendungsfreischaltung in UserService.requestApplication*

Anders als *createUser* läuft diese Methode in einer rein lesenden Transaktion (*readOnly = true*), da sie nichts in der Datenbank ändert, sondern nur eine E-Mail auslöst. Trotzdem nutzt sie ebenfalls *runAfterCommit*, damit auch bei einem Rollback dieser lesenden Transaktion keine E-Mail verschickt wird.

### 4.4.5 Bootstrap eines Standard-Administrators

Ein frisch aufgesetztes Backend hat noch kein Administratorkonto, mit dem sich weitere Administratoren anlegen ließen; FA-7 setzt ein solches Konto aber voraus. Deshalb legt *DataInitializer* beim Start einmalig einen konfigurierten Standard-Administrator an, sofern es noch keinen mit dieser E-Mail-Adresse gibt:

```java
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (userRepository.existsByEmail(properties.getEmail())) {
            return;
        }
        Admin admin = new Admin();
        admin.setName("Default Admin");
        admin.setEmail(properties.getEmail());
        admin.setPassword(passwordEncoder.encode(properties.getPassword()));
        admin.setEnabled(true);
        userRepository.save(admin);
    }
}
```

*Code 4.20: Anlage eines Standard-Administrators beim Start (gekürzt)*

E-Mail-Adresse und Passwort lassen sich über *ADMIN_EMAIL* und *ADMIN_PASSWORD* einstellen; voreingestellt sind *admin@design.local* und *Admin123!*. Mit *app.admin-initializer.enabled=false* lässt sich die Funktion ganz abschalten, etwa in einer Produktivumgebung, in der Administratorkonten nur von Hand angelegt werden. *DataInitializer* liegt im eigenen Paket *bootstrap* und nicht in *user*, weil es sich um eine Aufgabe beim Start der Anwendung handelt und nicht um fachliche Nutzerverwaltung (vgl. Abschnitt 4.1.2). Da die Standardwerte öffentlich dokumentiert sind, müssen sie vor jedem produktiven Einsatz ersetzt werden. Andernfalls gäbe es einen Zugang mit bekannten Zugangsdaten.

## 4.5 Desktop-Inventar

Das Modul *desktop* setzt FA-10 und FA-11 um, die Verwaltung von Desktops und Desktop-Gruppen. Es verbindet die Nutzerverwaltung (Abschnitt 4.4) mit der Infrastruktur, die die DaaS-Anwendung tatsächlich bereitstellt. Da es kaum sicherheitskritische Logik enthält, behandeln die beiden folgenden Abschnitte nur zwei Entwurfsentscheidungen, die der Konsistenz der Daten dienen: die Trennung von Inventar und Bereitstellung (4.5.1) und die beidseitig gepflegte n:m-Verknüpfung mit Nutzergruppen (4.5.2).

### 4.5.1 Reines Inventar, keine Bereitstellung

*DesktopService* verwaltet nur Metadaten zu Desktops und Desktop-Gruppen. Container und virtuelle Maschinen stellt, wie in den Abschnitten 2.1 und 3.1 beschrieben, die DaaS-Anwendung bereit, nicht dieses Backend. Mit *createDesktop* lassen sich auf Wunsch in einem Aufruf auch gleich Untergruppen anlegen:

```java
@Transactional
public void createDesktop(DesktopCreateRequest request) {
    Set<String> subGroupDescriptions = new HashSet<>();
    if (request.getGroups() != null) {
        for (DesktopCreateRequest.SubGroupRequest sgReq : request.getGroups()) {
            String description = sgReq.getDescription();
            if (!subGroupDescriptions.add(description) || desktopGroupRepository.existsByDescription(description)) {
                throw new DesktopGroupAlreadyExistsException(description);
            }
        }
    }
    Desktop desktop = new Desktop();
    desktop.setDescription(request.getDescription());
    Desktop savedDesktop = desktopRepository.save(desktop);
    if (request.getGroups() != null) {
        for (DesktopCreateRequest.SubGroupRequest sgReq : request.getGroups()) {
            DesktopGroup g = new DesktopGroup();
            g.setDescription(sgReq.getDescription());
            linkDesktopAndGroup(savedDesktop, g);
            desktopGroupRepository.save(g);
        }
    }
}
```

*Code 4.21: Anlegen eines Desktops mit Untergruppen*

Doppelte Beschreibungen werden in zwei Schritten abgefangen: zuerst innerhalb der Anfrage selbst, wo *subGroupDescriptions.add(...)* bei einem Duplikat *false* liefert, und danach gegen die bereits gespeicherten Daten (*existsByDescription*). Ohne den ersten Schritt könnte eine einzelne Anfrage zwei Untergruppen mit gleichem Namen anlegen, denn zum Zeitpunkt der Prüfung existiert noch keiner der beiden Einträge in der Datenbank.

### 4.5.2 Verknüpfung von User-Groups und Desktop-Groups

*addUserGroupToDesktopGroup* verknüpft eine Nutzergruppe mit einer Desktop-Gruppe und verhindert dabei doppelte Verknüpfungen:

```java
@Transactional
public void addUserGroupToDesktopGroup(Integer desktopGroupId, Integer userGroupId) {
    DesktopGroup dg = findDesktopGroupById(desktopGroupId);
    UserGroup ug = findUserGroupById(userGroupId);
    if (ug.getDesktopGroups().contains(dg)) {
        throw new UserGroupAlreadyAssociatedException(desktopGroupId, userGroupId);
    }
    linkUserGroupAndDesktopGroup(ug, dg);
    userGroupRepository.save(ug);
}
```

*Code 4.22: Verknüpfung von Nutzergruppe und Desktop-Gruppe*

Die Verknüpfung wird auf beiden Seiten eingetragen (*userGroup.getDesktopGroups().add(...)* und *desktopGroup.getUserGroups().add(...)* in *linkUserGroupAndDesktopGroup*). Beide Entitäten bilden eine n:m-Beziehung über JPA ab; ohne den beidseitigen Eintrag hinge der Zustand im Speicher davon ab, von welcher Seite man die Beziehung abfragt. Diese Verknüpfung ist zugleich die Datengrundlage für FA-12, also die Prüfung, ob ein Nutzer Zugriff auf einen Desktop hat. Über die Kette *Nutzer → Nutzergruppe → Desktop-Gruppe → Desktop* lässt sich für jeden Desktop ermitteln, welche Nutzer über ihre Gruppen Zugriff haben. Einen eigenen Endpunkt für diese Prüfung gibt es jedoch nicht, da weder Frontend noch DaaS-Anwendung ihn aufrufen (Tabelle 3.5).

## 4.6 Querschnittsthemen

Die folgenden sechs Themen betreffen nicht ein einzelnes Modul, sondern den gesamten Code: die Sicherheitsfilterkette, die jede Anfrage durchläuft (4.6.1), die einheitliche Fehlerbehandlung (4.6.2), die Prüfung von Eingaben (4.6.3), CORS, Protokollierung und der Schutz personenbezogener Daten (4.6.4), die erzeugte API-Dokumentation (4.6.5) und das zeitgesteuerte Aufräumen (4.6.6). Es sind genau die Aufgaben, die laut Abschnitt 4.1.2 im Paket *shared* von den Fachmodulen getrennt liegen.

### 4.6.1 Sicherheitsfilterkette

*SecurityConfig* richtet eine zustandslose Filterkette ein. CSRF-Schutz ist abgeschaltet, weil es weder Cookies noch Sitzungen gibt, *SessionCreationPolicy.STATELESS* ist gesetzt, und außer einer festen Liste öffentlicher Endpunkte verlangt jede Anfrage ein gültiges JWT:

```java
http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.POST, "/user").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/user/validate_email").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/user/resend_code").permitAll();
            auth.requestMatchers(HttpMethod.POST, "/oauth2/user/token").permitAll();
            auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();

            if (securityProperties.isSwaggerPublic()) {
                auth.requestMatchers(SWAGGER_ENDPOINTS).permitAll();
            } else {
                auth.requestMatchers(SWAGGER_ENDPOINTS).hasAuthority(Authorities.SCOPE_ADMIN);
            }

            auth.anyRequest().authenticated();
        })
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
```

*Code 4.23: Konfiguration der zustandslosen Sicherheitsfilterkette (gekürzt)*

Alles ist verboten, was nicht ausdrücklich freigegeben ist. Das ist derselbe fail-closed-Grundsatz wie bei der Berechtigungskaskade (Abschnitt 4.3), diesmal auf Ebene der HTTP-Schicht. Eine Ausnahme gilt für die Endpunkte der Swagger-UI, gesteuert über die Einstellung *app.security.swagger-public*. In Produktivumgebungen (*application-prod.yml*) steht sie auf *false*, sodass die API-Dokumentation nur mit einem Administrator-Token sichtbar ist. Im lokalen Entwicklungsprofil (*application-local.yml*) steht sie auf *true*, damit die Demonstration aus Abschnitt 4.7 ohne vorherige Anmeldung möglich ist. Außer den fachlich öffentlichen Endpunkten ist nur der Health-Check von Spring Boot Actuator ohne Token erreichbar, weil Container-Orchestrierung und Load Balancer kein Token mitsenden können. Er gibt nur den Gesamtstatus aus, keine Details zu einzelnen Komponenten.

### 4.6.2 Fehlerbehandlung

*GlobalExceptionHandler* und die modulbezogenen Handler *TokenExceptionHandler* und *SessionExceptionHandler* übersetzen typisierte Exceptions wie *InvalidCredentialsException*, *AccountLockedException*, *UserNotFoundException* oder *WeakPasswordException* in ein einheitliches JSON-Format (*ErrorResponse*) mit dem passenden HTTP-Statuscode. Im alten PHP-Backend übernahm eine Middleware (*InvalidUserInputHandlerMiddleware*) diese Aufgabe, deren Verhalten schwer nachzuvollziehen war. Im neuen Backend ist jede fachliche Fehlersituation eine eigene, aussagekräftig benannte Exception-Klasse statt eines allgemeinen Fehlercodes. Dadurch ist sowohl im Service erkennbar, wo ein Fehler entsteht, als auch im Handler, welcher Statuscode dazu gehört.

### 4.6.3 Eingabevalidierung

Alle Request-DTOs verwenden Bean Validation (**jakarta.validation**), sodass formal ungültige Anfragen abgelehnt werden, bevor sie die Service-Schicht erreichen. Die Regeln für Passwörter sind an einer einzigen Stelle festgelegt, damit Prüfregel und Fehlermeldung nicht auseinanderlaufen:

```java
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String MESSAGE =
            "password must be 8-72 characters and contain uppercase, lowercase, and a digit";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,72}$";
}
```

*Code 4.24: Zentrale Passwortrichtlinie*

Die Obergrenze von 72 Zeichen ist nicht willkürlich. BCrypt berücksichtigt nur die ersten 72 Byte eines Passworts. Ein längeres Passwort würde stillschweigend gekürzt, und Nutzer würden fälschlich annehmen, ein längeres Passwort sei sicherer. *PasswordPolicy* wird sowohl von der Annotation an den DTOs als auch von *AccountValidator.validatePassword* verwendet, das Passwörter bei Registrierung und Passwortänderung ein zweites Mal prüft (Abschnitte 4.4.1 und 4.4.2). Beide Prüfungen wenden damit garantiert dieselbe Regel an.

### 4.6.4 CORS, Protokollierung und Schutz personenbezogener Daten

*CorsConfig* erlaubt standardmäßig nur Anfragen von der in *app.frontend.url* eingetragenen Adresse des Frontends. Da JWTs personenbezogene Daten wie E-Mail-Adresse und Name enthalten, maskiert *LogMasking.maskEmail* E-Mail-Adressen in allen Log-Einträgen über Nutzeraktionen (siehe etwa Abschnitt 4.2.2). Das betrifft alle Service-Klassen, die mit Nutzerdaten arbeiten. Wie der Schutz vor Zeitmessungsangriffen in Abschnitt 4.2.2 stammt auch diese Maßnahme nicht aus dem alten Backend, sondern ist eine eigene Verbesserung.

### 4.6.5 API-Dokumentation über OpenAPI/Swagger

Im alten Backend wurden OpenAPI-Annotationen von Hand gepflegt (*zircote/swagger-php*, Tabelle 3.1). Im neuen Backend erzeugt *springdoc-openapi* die Beschreibung der Schnittstelle automatisch aus den Controller- und DTO-Klassen. *OpenApiConfig* ergänzt nur allgemeine Angaben wie Titel und Version sowie das Sicherheitsschema für Bearer-Token, damit sich in der Swagger-UI auch geschützte Endpunkte aufrufen lassen:

```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .info(new Info().title("Design DaaS Backend API").version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
}
```

*Code 4.25: OpenAPI-Konfiguration mit Bearer-Sicherheitsschema*

Da die Beschreibung direkt aus dem Code entsteht, können Pfade, Parameter und Datenstrukturen nicht von der Implementierung abweichen, wie es bei handgepflegten Annotationen möglich ist. Jede Änderung an einem Controller oder DTO erscheint sofort unter */v3/api-docs* und in der Swagger-UI. Diese Dokumentation ist zugleich die Grundlage des Demonstrators aus Abschnitt 4.7.

### 4.6.6 Geplante Aufräumarbeiten

*TokenCleanupService* löscht abgelaufene und widerrufene Einträge aus *access_tokens* und *refresh_tokens*, damit die Tabellen nicht unbegrenzt wachsen. Er läuft über Spring Scheduling, standardmäßig täglich um 03:00 Uhr; der Zeitpunkt lässt sich über *app.token.cleanup.cron* ändern. Im alten Backend erledigte ein eigener Dienst (Yacron) solche Aufgaben. Hier läuft die Aufgabe in der Anwendung selbst, sodass ein Bestandteil weniger betrieben werden muss.

## 4.7 Demonstrator (AP5)

Arbeitspaket AP5 verlangt einen Demonstrator, der die wichtigsten Anwendungsfälle aus Abschnitt 3.5 vorführt. Anders als die DaaS-Anwendung hat das Backend keine eigene Oberfläche. Als Werkzeug für die Demonstration dient deshalb die Swagger-UI aus Abschnitt 4.6.5. Mit ihr lässt sich jeder Endpunkt direkt im Browser mit Beispieldaten aufrufen und die Antwort samt HTTP-Statuscode ansehen, ohne einen eigenen HTTP-Client wie Postman oder curl.

### 4.7.1 Aufbau der Demonstrationsumgebung

Der Demonstrator läuft mit demselben Artefakt, das auch produktiv eingesetzt wird. Das mehrstufige **Dockerfile** baut das Projekt mit Maven (*mvn package -DskipTests*) und kopiert nur die erzeugte JAR-Datei in ein schlankes Laufzeit-Image auf Basis von *eclipse-temurin:21-jre-alpine*:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
...
VOLUME /app/keys
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

*Code 4.26: Mehrstufiges Dockerfile (gekürzt)*

Im Volume */app/keys* liegt das RS256-Schlüsselpaar aus Abschnitt 4.2.3, damit es einen Neustart des Containers übersteht. Für die Demonstration genügt zusätzlich eine lokal erreichbare MariaDB-Instanz. Konfiguriert wird alles über Umgebungsvariablen (*.env.example*):

```bash
DB_URL=jdbc:mariadb://localhost:3306/design_backend
DB_USERNAME=design
DB_PASSWORD=design_password
OAUTH_KEYS_DIR=./keys
MAIL_ENABLED=false
FRONTEND_URL=http://localhost:3000
```

*Code 4.27: Umgebungsvariablen der Demonstrationsumgebung (Auszug aus .env.example)*

Mit *MAIL_ENABLED=false*, der Voreinstellung, verschickt *MailService* keine echten E-Mails, schreibt den Registrierungscode aber auf der Stufe DEBUG ins Log. So lässt sich die Registrierung mit Bestätigung (FA-5) ohne SMTP-Server vorführen: Man liest den Code aus dem Log ab und sendet ihn an */user/validate_email*. Beim ersten Start legt *DataInitializer* (Abschnitt 4.4.5) automatisch einen Administrator an (*admin@design.local*, *Admin123!*). Mit ihm lassen sich weitere Konten verwalten, ohne vorher von Hand einen Datensatz in die Datenbank schreiben zu müssen. Im lokalen Profil (*application-local.yml*), das voreingestellt ist, gilt *app.security.swagger-public=true*; die Swagger-UI ist dann ohne Anmeldung unter *http://localhost:8080/swagger-ui.html* erreichbar.

Damit erfüllt das Backend den Anspruch der Ausschreibung, dass sich die Teile von DESIGN relativ einfach installieren lassen (NFA-11). Für einen lokalen Start genügen zwei Schritte, die im README des Repositorys beschrieben sind: eine MariaDB-Instanz als Container starten und das Backend mit *mvn spring-boot:run* ausführen. Das Datenbankschema legt Flyway beim ersten Start selbst an, und der Standard-Administrator entsteht automatisch. Für den Betrieb als Container genügen *docker build* und *docker run* mit einer Umgebungsdatei.

Abbildung 4.3 zeigt die vorgesehene Betriebsumgebung. Das Backend ist nicht direkt erreichbar, sondern steht hinter einem Reverse Proxy, der die TLS-Verbindung beendet. Das ist nötig, weil Bearer-Token nur über TLS übertragen werden dürfen (Abschnitt 2.2.1); das Betriebshandbuch des Repositorys führt diesen Punkt deshalb in seiner Sicherheitscheckliste auf. Damit die Ratenbegrenzung die tatsächliche Adresse des Clients sieht, muss die Adresse des Proxys in *RATE_LIMIT_TRUSTED_PROXIES* eingetragen sein (Abschnitt 4.2.6).

![Abbildung 4.3: Betriebsansicht des neuen Backends (eigene Darstellung)](images/abb-4-3.png)

*Abbildung 4.3: Betriebsansicht des neuen Backends (eigene Darstellung)*

### 4.7.2 Vorgeführte Abläufe

In der Swagger-UI werden die wichtigsten Abläufe aus Abschnitt 3.5 vorgeführt. Abbildung 4.4 und Abbildung 4.5 zeigen zwei davon.

- **Anmeldung und Berechtigungsprüfung (FA-1, FA-13):** *POST /oauth2/user/token* mit *grant_type=password* und den Zugangsdaten des Standard-Administrators liefert ein Zugriffstoken, das über die Schaltfläche „Authorize“ der Swagger-UI hinterlegt wird. Danach liefert *GET /permissions/{functionName}/{userId}* nach der Kaskade aus Abschnitt 4.3 die Entscheidung „erlaubt“ oder „verweigert“.
- **Token erneuern und abmelden (FA-2, FA-3):** *POST /oauth2/user/token* mit *grant_type=refresh_token* und dem erhaltenen Refresh-Token liefert ein neues Token-Paar; das alte Refresh-Token ist danach nicht mehr verwendbar (Abschnitt 4.2.4). *POST /oauth2/user/logout* widerruft das aktuelle Token; eine weitere Anfrage mit demselben Zugriffstoken wird mit *401 Unauthorized* abgelehnt.
- **Registrierung und Bestätigung der E-Mail-Adresse (FA-5):** *POST /user* legt ein deaktiviertes Konto an. Der Registrierungscode wird bei *MAIL_ENABLED=false* aus dem Log abgelesen und an *POST /user/validate_email* geschickt. Danach kann sich der neue Nutzer anmelden.
- **Desktops und Desktop-Gruppen anlegen (FA-10, FA-11):** *POST /desktop* legt mit dem Administrator-Token einen Desktop samt Untergruppe an. *POST /desktop_group/{id}/user_group/{userGroupId}* verknüpft diese Gruppe mit einer Nutzergruppe, die zuvor über *POST /user_group* angelegt wurde.
- **Freischaltung einer Anwendung anfragen (FA-9):** *POST /user/request-application/{application}/{isoCode}* löst die Benachrichtigung aus Abschnitt 4.4.4 aus; bei *MAIL_ENABLED=false* ist sie im Log sichtbar.

> [Screenshot aus der lokalen Demonstrationsumgebung hier einfügen]

*Abbildung 4.4: Swagger-UI mit Beispielanfrage und -antwort für den Password Grant (POST /oauth2/user/token)*

> [Screenshot aus der lokalen Demonstrationsumgebung hier einfügen]

*Abbildung 4.5: Swagger-UI mit einer allow- und einer deny-Antwort von GET /permissions/{functionName}/{userId}*

Der Demonstrator zeigt damit, dass sich die Anwendungsfälle aus Abschnitt 3.5 mit dem neuen Backend ausführen lassen. Da Frontend und DaaS-Anwendung im Betrieb genau diese HTTP-Schnittstellen nutzen, eignet sich die Swagger-UI für diesen Nachweis. Einen Ende-zu-Ende-Test mit beiden Clients ersetzt sie allerdings nicht (Abschnitt 5.4).

> [Hinweis: Falls das Backend gemeinsam mit Frontend und DaaS-Anwendung betrieben wurde, diesen Test hier kurz beschreiben und mit einem Screenshot belegen]

## 4.8 Tests und Evaluation (AP6)

Die Abschnitte 4.1 bis 4.7 haben beschrieben, wie die Anforderungen aus Kapitel 3 umgesetzt wurden. Dieser Abschnitt zeigt, dass die Umsetzung funktioniert und die Qualitätsziele aus Kapitel 1 erreicht. Zuerst werden die Qualitätsprüfungen erläutert, die der Build automatisch durchsetzt (4.8.1), dann Art und Umfang der Tests (4.8.2) und die erreichte Testabdeckung (4.8.3). Abschnitt 4.8.4 beschreibt eine bekannte Einschränkung, und Abschnitt 4.8.5 vergleicht altes und neues Backend anhand ihres Quellcodes.

### 4.8.1 Automatisierte Qualitätssicherung im Build

Der Build (*mvn verify*) setzt zwei Qualitätsprüfungen durch: die 19 ArchUnit-Regeln aus Abschnitt 4.1.3 und eine Mindest-Testabdeckung von 70 % der Codezeilen mit JaCoCo:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

*Code 4.28: JaCoCo-Mindestabdeckung in der pom.xml (gekürzt)*

Liegt die Testabdeckung darunter, schlägt der Build fehl. Testabdeckung ist damit keine Empfehlung, sondern eine Voraussetzung für jeden erfolgreichen Build (NFA-9).

### 4.8.2 Testarten und Umfang

Die Testsuite umfasst 31 Testklassen mit 232 JUnit-Testmethoden und 19 ArchUnit-Regeln, insgesamt also 251 Testfälle (Stand: Version 2.0.0, ermittelt aus den Surefire-Berichten unter *target/surefire-reports*). Eine zusätzliche Basisklasse (*BaseIntegrationTest*) enthält die gemeinsame Konfiguration der Integrationstests. Tabelle 4.2 schlüsselt die Tests nach Art auf.

> [Zahlen nach erneutem Lauf von mvn verify bestätigen: 0 Fehlschläge, 0 Fehler]

*Tabelle 4.2: Testsuite nach Art*

| **Testart** | **Beispiele** | **Umfang** |
|---|---|---|
| Architektur-Tests | ArchitectureTest (Schichtung, Namenskonventionen, Injektionsstil) | 19 Regeln |
| Service-Unit-Tests | TokenServiceTest, PermissionServiceTest, AccountLockoutServiceTest, UserServiceTest, DesktopServiceTest, UserGroupServiceTest, AdminServiceTest, MailServiceTest | 8 Klassen |
| Hilfs-/Infrastruktur-Unit-Tests | TokenIssuerTest, ScopeResolverTest, JwtUserResolverTest, SessionServiceTest, TokenCleanupServiceTest, TokenRevocationValidatorTest, SecurityPropertiesTest, GrantRequestTest, GlobalExceptionHandlerTest | 9 Klassen |
| Controller-Unit-Tests (ohne vollen Spring-Kontext) | PermissionControllerTest, DesktopGroupControllerTest | 2 Klassen |
| Integrationstests (voller Spring-Kontext, H2) | TokenControllerIntegrationTest, UserControllerIntegrationTest, AdminControllerIntegrationTest, DesktopControllerIntegrationTest, UserGroupControllerIntegrationTest, PermissionControllerIntegrationTest, OAuth2SessionControllerIntegrationTest, AuthHardeningIntegrationTest, InputValidationIntegrationTest, PublicEndpointsIntegrationTest | 10 Klassen |
| Anwendungskontext-Test | DesignBackendApplicationTests (Spring-Kontext startet fehlerfrei) | 1 Klasse |

Die Integrationstests laufen gegen die eingebettete Datenbank H2 statt gegen MariaDB. So kommen sie ohne externe Infrastruktur aus und laufen schnell. Die Flyway-Migrationen aus Abschnitt 4.1.5 werden dabei unverändert gegen H2 ausgeführt, sodass das Schema dem der Produktion entspricht. *AuthHardeningIntegrationTest* und *InputValidationIntegrationTest* prüfen die Sicherheits- und Validierungsmaßnahmen aus den Abschnitten 4.2 und 4.6.3 gezielt von Ende zu Ende und nicht nur auf Ebene der Services, zum Beispiel die Kontosperre nach wiederholten Fehlversuchen oder die Ablehnung zu schwacher Passwörter.

Die Unit-Tests der Services ersetzen Repositories, den E-Mail-Versand und weitere Abhängigkeiten durch Mock-Objekte von Mockito (Abschnitt 2.4.6). So lässt sich etwa jede Stufe der Berechtigungskaskade gezielt mit passenden oder fehlenden Regeln testen, ohne eine Datenbank zu starten.

### 4.8.3 Tatsächlich erreichte Testabdeckung

Der zuletzt erzeugte JaCoCo-Bericht (*target/site/jacoco/index.html*) zeigt für das gesamte Projekt die Werte in Tabelle 4.3:

*Tabelle 4.3: JaCoCo-Testabdeckung, Gesamtprojekt (91 geprüfte Klassen)*

| **Metrik** | **Abgedeckt** | **Gesamt** | **Anteil** |
|---|---|---|---|
| Instruktionen | 4.441 | 5.120 | 86 % |
| Verzweigungen (Branches) | 245 | 332 | 73 % |
| Zeilen | 973 | 1.118 | 87 % |
| Methoden | 240 | 264 | 91 % |
| Klassen | 87 | 91 | 96 % |

Mit 87 % liegt die Zeilenabdeckung deutlich über dem Minimum von 70 % (Abschnitt 4.8.1). Die Schranke begrenzt derzeit also nichts, verhindert aber, dass künftige Änderungen die Abdeckung unbemerkt nach und nach senken. Dass die Abdeckung der Verzweigungen mit 73 % niedriger ist als die der Zeilen, ist typisch für Code mit vielen Sonderfällen, wie ihn die Berechtigungskaskade (Abschnitt 4.3) und die Fehlerbehandlung (Abschnitt 4.6.2) enthalten. Jede zusätzliche Verzweigung für einen Randfall erhöht die Zahl der Zweige schneller, als Tests sie in beiden Richtungen abdecken.

> [Werte nach erneutem Lauf von mvn verify aus target/site/jacoco/index.html aktualisieren; der Bericht stammt von vor Version 2.0.0]

### 4.8.4 Bekannte Einschränkung der Kontosperre

Beim Schreiben der Tests (Abschnitt 4.8.2, *AccountLockoutServiceTest*) wurde ein Verhalten der Kontosperre aus Abschnitt 4.2.2 gezielt nachgeprüft. Laut Javadoc von *AccountLockoutService* wird der Zähler für Fehlversuche nur bei einer erfolgreichen Anmeldung zurückgesetzt, nicht beim Ablauf der Sperre. Läuft eine Sperre ab und scheitert der nächste Versuch erneut, etwa weil sich der Nutzer weiterhin falsch an sein Passwort erinnert, wird das Konto sofort wieder für 15 Minuten gesperrt; der Zähler steht ja noch auf fünf. Für einen Angreifer ist das kein Vorteil, die Sperre wird nur länger. Für einen Nutzer, der sein Passwort schlicht vergessen hat, kann es sich aber wie eine Dauersperre anfühlen, weil jeder weitere Fehlversuch die volle Sperrzeit neu startet. Diese Einschränkung wurde in Kauf genommen, weil Sicherheit hier Vorrang vor Bequemlichkeit hat. Kapitel 5 greift sie als mögliche Verbesserung wieder auf, etwa durch einen schrittweisen Abbau des Zählers.

### 4.8.5 Vergleich mit dem alten Backend

Tabelle 4.4 vergleicht die wichtigsten Maßnahmen zur Qualitätssicherung beider Backends. Grundlage sind die Konfigurationsdateien und der Quellcode beider Repositories, nicht deren Dokumentation: die *pom.xml* dieses Projekts sowie *phpunit.xml*, *composer.json* und *BasicArchitectureTest.php* des PHP-Projekts.

*Tabelle 4.4: Qualitätssicherung, altes Backend (PHP) vs. neues Backend (Java)*

| **Kriterium** | **Altes Backend (PHP)** | **Neues Backend (Java)** |
|---|---|---|
| Schichtenarchitektur erzwungen? | Teilweise: PHPat prüft Modulabhängigkeiten, aber nicht die interne Model → Repository → Service → Controller-Schichtung je Modul | Ja: ArchUnit prüft zusätzlich die interne Schichtung jedes Fachmoduls sowie Namens- und Injektionskonventionen (19 Regeln) |
| Testabdeckung als Build-Gate? | Nein: phpunit.xml konfiguriert Coverage-Erfassung (<coverage includeUncoveredFiles="true"/>), aber keinen Mindestwert, der den Build scheitern lässt | Ja: JaCoCo erzwingt ≥ 70 % Zeilenabdeckung im Build; tatsächlich erreicht: 87 % (Tabelle 4.3) |
| Anzahl automatisierter Tests | 69 PHPUnit-Testdateien (tests/src/{Unit,Integration}) | 31 JUnit-5-Testklassen mit 251 Testfällen |
| Berechtigungskaskade | 4-stufig, PermissionCalculationService, Konfliktfall bei mehreren Treffern pro Stufe nicht dokumentiert | 4-stufig, identische Priorität, zusätzlich dokumentierte und getestete Konfliktauflösung ("explizite Verweigerung gewinnt", 4.3.2) |
| Schema-Verwaltung | Doctrine Migrations | Flyway, Hibernate nur validate |
| Auth-Mechanismus | league/oauth2-server, Password Grant | Eigener RS256-JWT-Aussteller + Spring-Security-Resource-Server, zusätzlich Timing-Schutz und Kontosperrung (4.2.2) |
| API-Dokumentation | Manuell gepflegte zircote/swagger-php-Annotationen | Automatisch aus Code generiert (springdoc-openapi, 4.6.5) — kann nicht von der Implementierung abweichen |

Die Zahl der Testdateien (69 gegenüber 31) sagt dabei wenig über die Qualität aus, da PHPUnit und JUnit 5 Tests unterschiedlich fein aufteilen; eine JUnit-5-Klasse kann zum Beispiel mehrere mit *@Nested* gruppierte Testmethoden enthalten. Aussagekräftiger ist der Vergleich der automatischen Qualitätsprüfungen. Das alte Backend hatte bereits Architekturregeln, aber keine erzwungene Testabdeckung. Das neue Backend verbindet beides zu einer festen Voraussetzung für jeden Build. Damit setzt es direkt an der Qualität an, die die Ausschreibung am PHP-Backend bemängelt, und erfüllt NFA-8 und NFA-9 auf eine Weise, die sich mit jedem Build überprüfen lässt.

## 4.9 Dokumentation (AP6)

Neben der Qualität bemängelt die Ausschreibung ausdrücklich die schlechte Dokumentation des alten Backends (Baun, 2026). Zu AP6 gehört deshalb neben Test und Evaluation auch die Dokumentation. Das neue Backend ist auf drei Ebenen dokumentiert (NFA-10): durch eine aus dem Code erzeugte API-Dokumentation, durch Kommentare im Code und durch Begleitdokumente im Repository.

### 4.9.1 API-Dokumentation aus dem Code

Wie in Abschnitt 4.6.5 beschrieben, erzeugt springdoc-openapi die Beschreibung der Schnittstelle aus den Controller- und DTO-Klassen und stellt sie über die Swagger-UI bereit. Pfade, Parameter und Datenstrukturen können deshalb nicht von der Implementierung abweichen. Die Swagger-UI dient zugleich als Demonstrator (Abschnitt 4.7).

### 4.9.2 Dokumentation im Code

Für den Code gelten feste Regeln, die im Entwicklerhandbuch des Repositorys festgehalten sind:

- Jeder Typ und jede öffentliche Methode trägt einen Javadoc-Kommentar; ausgenommen sind nur von Lombok erzeugte Zugriffsmethoden.
- Jedes der sieben Pakete (*auth*, *user*, *desktop*, *permission*, *shared*, *config*, *bootstrap*) besitzt eine Datei *package-info.java*, die seine Aufgabe und seine Beziehungen zu den anderen Paketen erklärt.
- Kommentare erklären vor allem, warum etwas so umgesetzt ist, und weniger, was der Code tut. Ein Beispiel ist die Begründung des Dummy-Hashes in *TokenService* (Abschnitt 4.2.2).
- Bekannte Einschränkungen werden direkt am betroffenen Code dokumentiert, etwa die Ungenauigkeit beim Auffüllen in *TokenBucket* (Abschnitt 4.2.7).

Laut der im Entwicklerhandbuch festgehaltenen Überprüfung (Stand: 07.11.2026) haben alle 122 Typen und alle 96 öffentlichen Methoden einen Javadoc-Kommentar; überschriebene und von Lombok erzeugte Methoden sind dabei nicht mitgezählt. Bei dieser Überprüfung wurden auch Kommentare korrigiert, die nach späteren Änderungen nicht mehr stimmten, etwa nachdem die Namensspalte der Desktop-Gruppen mit der Migration *V4* entfallen war. Mit *mvn javadoc:javadoc* lässt sich aus den Kommentaren eine HTML-Dokumentation erzeugen.

### 4.9.3 Begleitdokumente

Tabelle 4.5 zeigt die Begleitdokumente im Repository. Sie richten sich an unterschiedliche Zielgruppen und sind alle über das README erreichbar.

*Tabelle 4.5: Begleitdokumente des neuen Backends*

| Dokument | Zielgruppe | Inhalt |
|---|---|---|
| README.md | alle | Überblick, Schnellstart, Konfiguration über Umgebungsvariablen, Betrieb mit Docker |
| docs/user-manual.md | Nutzer, Administratoren, Frontend-Entwickler | Registrierung, Anmeldung, Umgang mit Tokens, typische Abläufe für Administratoren |
| docs/api.md | Entwickler von Frontend und DaaS-Anwendung | alle Endpunkte mit Zugriffsregeln, Anfragedaten und Statuscodes |
| docs/database.md | Entwickler, Datenbankadministratoren | Schema, ER-Diagramm, Migrationshistorie und Auswertung der Berechtigungen |
| docs/arc42.md | Architekten, Entwickler | Architekturdokumentation nach der arc42-Vorlage (arc42, n.d.) |
| docs/development.md | Entwickler | Projektstruktur, Befehle, Konventionen, Hinzufügen neuer Endpunkte, Testhinweise |
| docs/operations.md | Betrieb | Sicherheitscheckliste für den Produktivbetrieb, Geheimnisse, Sicherungen, Überwachung, Fehlersuche |

Damit Dokumentation und Code nicht wieder auseinanderlaufen, schreibt das Entwicklerhandbuch vor, die Dokumentation in derselben Änderung anzupassen wie den Code: das Javadoc und das passende Dokument unter *docs/*, *docs/api.md* bei geänderten Endpunkten, *docs/database.md* bei neuen Migrationen und *docs/arc42.md* bei Architekturentscheidungen und Risiken. Automatisch geprüft wird diese Regel allerdings nicht; sie beruht auf Disziplin (Abschnitt 5.4). Die Begleitdokumente sind wie die Code-Kommentare auf Englisch geschrieben, wie es in quelloffenen Projekten üblich ist.
