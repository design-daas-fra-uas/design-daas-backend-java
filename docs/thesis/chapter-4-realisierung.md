# 4 Realisierung

Dieses Kapitel beschreibt, wie die in Kapitel 3 abgeleiteten funktionalen (FA) und nicht-funktionalen (NFA)
Anforderungen im neuen Backend `design-daas-backend-java` umgesetzt wurden. Die Gliederung folgt den vier
fachlichen Modulen aus Abschnitt 3.4 (`auth`, `permission`, `user`, `desktop`), ergänzt um die
Architekturentscheidungen, die diesen Modulen vorausgehen (4.1), die Querschnittsthemen, die sie verbinden (4.6), sowie
den Demonstrator (4.7) und die abschließende Evaluation anhand der Testsuite (4.8). Alle
Codeausschnitte sind kurze, wortwörtliche Auszüge aus dem Repository `design-daas-backend-java`;
Vergleiche mit dem alten Backend beziehen sich auf `design-daas-application-backend`.

---

## 4.1 Architekturentscheidungen

Bevor im Folgenden die einzelnen Fachmodule im Detail beschrieben werden, klärt dieser Abschnitt die
übergreifenden Architekturentscheidungen, die allen Modulen zugrunde liegen: die Gliederung des Quellcodes (4.1.2), ihre
automatisierte Durchsetzung (4.1.3), die Wahl des Authentifizierungsmechanismus (4.1.4) und die
Schemaverwaltung (4.1.5). Abschnitt 4.1.6 fasst diese Entscheidungen tabellarisch zusammen und ordnet sie den
Anforderungen aus Kapitel 3 zu, bevor die Abschnitte 4.2–4.6 sie anhand von Code vertiefen.

### 4.1.1 Ausgangslage und Entscheidungsrahmen

Die in Kapitel 3 abgeleiteten Anforderungen verlangen zweierlei gleichzeitig: Erstens muss das neue Backend
**verhaltenskompatibel** zum alten PHP-Backend sein (NFA-2, NFA-3), da Frontend und DaaS-Anwendung nicht
Teil dieser Arbeit sind und unverändert weiterlaufen müssen. Zweitens soll die Neuimplementierung gerade die
Mängel des alten Backends beheben – mangelnde Wartbarkeit und fehlende Dokumentation (vgl. Ausschreibung,
Kapitel 1). Diese beiden Ziele bestimmen die zentralen Architekturentscheidungen dieses Kapitels: Jede
Entscheidung wird daher im Folgenden nicht nur beschrieben, sondern auch begründet, gegen welche der beiden
Zielsetzungen sie einzahlt.

### 4.1.2 Modulare Gliederung nach fachlicher Domäne

Das alte PHP-Backend ist bereits vertikal in Module gegliedert (Verzeichnisse `src/Main`, `src/Util`,
`src/OAuth2`, `src/User`, `src/Desktop`, `src/Permissions`), jedes mit der internen Schichtung
`Model → Repository → Service → Controller`. Diese Gliederung wurde für das neue Backend übernommen, jedoch
umbenannt und auf vier fachliche Pakete konzentriert, die exakt den vier in Kapitel 3.4 beschriebenen
Fähigkeitsgruppen entsprechen:

```
de.frauas.design.backend
├── auth         (Token-Ausgabe/-Prüfung, Sessions, Lockout)
├── permission   (Berechtigungskaskade)
├── user         (Nutzer, Admins, User-Groups, Registrierung)
├── desktop      (Desktops, Desktop-Groups)
├── bootstrap    (Start-Tasks, z. B. Default-Admin)
├── config       (Spring-Konfiguration)
└── shared       (Querschnittscode: DTOs, Exceptions, Security, Utils)
```

*Code 4.1: Paketdiagramm der vier Fachmodule und des Querschnittscodes.*

Jedes fachliche Paket folgt intern derselben Schichtung `controller → service → repository → model`
(ergänzt um lokale `dto`- und `exception`-Unterpakete). Diese Entscheidung wurde getroffen, weil sie die
bestehende, aus dem PHP-Backend bekannte Struktur beibehält (geringeres Risiko von Bedeutungsverschiebungen
beim Portieren fachlicher Logik) und gleichzeitig eine im Java-/Spring-Ökosystem übliche, für Gutachter
nachvollziehbare Schichtenarchitektur ergibt.

### 4.1.3 Automatisierte Durchsetzung der Architektur mit ArchUnit

Die größte dokumentierte Schwäche des alten Backends ist mangelnde Wartbarkeit. Um zu verhindern, dass die
neue Architektur über die Projektlaufzeit informell "erodiert" (z. B. ein Repository, das direkt aus einem
Controller aufgerufen wird), wird die Schichtenregel nicht nur dokumentiert, sondern als ausführbarer Test
formuliert und bei jedem Build geprüft:

```java

@ArchTest
static final ArchRule layerDependencies = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Controllers").definedBy("de.frauas.design.backend..controller..")
        .layer("Services").definedBy("de.frauas.design.backend..service..")
        .layer("Repositories").definedBy("de.frauas.design.backend..repository..")
        .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
        .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Config", "Services", "Bootstrap")
        .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Config", "Bootstrap");
```

Zusätzliche Regeln in derselben Testklasse (`ArchitectureTest`) erzwingen Namenskonventionen (`@Service`-Klassen
müssen auf `Service`/`ServiceImpl` enden, `@Repository`-Klassen auf `Repository` usw.), Konstruktorinjektion
statt Feldinjektion in Services, ein Verbot von `java.util.logging`/`System.out`, sowie dass `shared` von
keinem Fachmodul abhängen darf (Vermeidung zirkulärer Kopplung). Insgesamt umfasst `ArchitectureTest` 19
einzelne, bei jedem Build ausgeführte Regeln. Ein Verstoß gegen eine dieser Regeln lässt `mvn test`
fehlschlagen – die Architektur ist damit nicht nur eine Absicht, sondern ein Build-Gate. Dies ist die
direkte technische Antwort auf die in der Ausschreibung genannte mangelnde Qualität des Vorgängersystems und
setzt NFA-8 ("Interne Schichtung wird automatisiert geprüft, nicht nur per Konvention") unmittelbar um.

Bemerkenswert ist, dass auch das alte PHP-Backend bereits ein vergleichbares Werkzeug einsetzt: Die Testklasse
`tests/src/Arch/Main/BasicArchitectureTest.php` definiert mittels `PHPat` drei Regeln, die den Kern-Namensraum
`Application\Core` vor Zugriffen von außerhalb isolieren und `Application\Migrations`/`Application\Test` als
jeweils eigenständige, nicht querverlinkte Namensräume absichern. Die Neuerung liegt also nicht darin,
Architekturtests grundsätzlich einzuführen, sondern darin, sie erstens auf die *interne* Schichtung jedes
Moduls auszuweiten (`Controller → Service → Repository`, im PHP-Code nur durch Konvention, nicht durch
`PHPat`-Regeln gesichert) und zweitens mit einer harten Testabdeckungsschranke zu kombinieren (Abschnitt
4.8.1) – beides zusammen ergibt eine deutlich engmaschigere automatisierte Qualitätssicherung als im
Ist-Zustand.

### 4.1.4 Zustandslose Authentifizierung statt vollem Autorisierungsserver

Das alte Backend verwendet `league/oauth2-server` mit einem Password-Grant. Für das neue Backend wurde
geprüft, ob ein vollständiger `spring-security-oauth2-authorization-server` eingesetzt werden soll. Da nur
zwei Grant-Typen benötigt werden (`password`, `refresh_token`) und `POST /oauth2/user/token` Zugangsdaten
direkt per Formularparameter entgegennimmt – wie es der alte Vertrag mit dem Frontend vorsieht –, wurde
stattdessen eine schlanke Eigenimplementierung gewählt: ein selbst gebauter RS256-JWT-Aussteller (`TokenIssuer`) in
Kombination mit Spring Securitys OAuth2-Resource-Server-Validierung (`JwtDecoder`). Diese
Entscheidung vermeidet den Konfigurationsaufwand eines vollständigen Autorisierungsservers für einen
Vertrag, der ohnehin nur zwei Grant-Typen abdeckt.

### 4.1.5 Schema ausschließlich über Flyway

Hibernate wird im Modus `ddl-auto: validate` betrieben; es erzeugt zu keinem Zeitpunkt Schema automatisch.
Alle Schemaänderungen laufen über versionierte Flyway-Migrationen (`V1__init.sql` … `V4__*.sql`). Diese
Entscheidung macht jede Schemaänderung explizit review- und nachvollziehbar und vermeidet
Hibernate-Auto-DDL-Überraschungen, die zwischen Entwicklungs-, Test- und Produktivumgebung unterschiedlich
ausfallen könnten – ein Qualitätsziel, das direkt auf die Wart- und Testbarkeitsanforderungen NFA-8/NFA-9 (Kapitel 3.2)
einzahlt und zugleich das Pendant zu den Doctrine Migrations des alten Backends bildet (Tabelle 3.1), sodass auch hier
kein Werkzeug neu eingeführt, sondern dessen Funktion 1:1 ersetzt wird.

### 4.1.6 Zusammenfassung der Architekturentscheidungen

Tabelle 4.1 fasst die in 4.1.2–4.1.5 begründeten Entscheidungen zusammen und ordnet sie jeweils einer
Anforderung aus Kapitel 3 zu. Die Tabelle dient als Scharnier zwischen Anforderungsanalyse und Realisierung:
Jede Zeile wird in den nachfolgenden Abschnitten 4.2–4.6 anhand von Code vertieft.

*Tabelle 4.1: Zentrale Architekturentscheidungen und ihr Anforderungsbezug*

| Entscheidung                                                   | Begründung                                                          | Anforderungsbezug |
|----------------------------------------------------------------|---------------------------------------------------------------------|-------------------|
| Vier fachliche Module, intern einheitlich geschichtet          | Struktur des alten Backends beibehalten, Portierungsrisiko senken   | NFA-2, NFA-3      |
| Schichtenregeln als ArchUnit-Build-Gate                        | Architektur nicht nur Konvention, sondern erzwungen                 | NFA-8             |
| Eigener RS256-JWT-Aussteller statt vollem Autorisierungsserver | Nur zwei Grant-Typen nötig, geringerer Konfigurationsaufwand        | NFA-7             |
| Flyway statt Hibernate-Auto-DDL                                | Nachvollziehbare, reviewbare Schemaänderungen                       | NFA-8, NFA-9      |
| JaCoCo-Mindestabdeckung als Build-Gate                         | Testabdeckung ist kein optionales Ziel, sondern Build-Voraussetzung | NFA-9             |

---

## 4.2 Authentifizierung

Das Fachmodul `auth` setzt die in Kapitel 3.2 beschriebenen Authentifizierungs-Anwendungsfälle FA-1 bis FA-4
um: Login per Benutzername/Passwort (FA-1), Token-Erneuerung (FA-2), Logout (FA-3) und Session-Introspektion (FA-4). Die
folgenden Abschnitte folgen dabei bewusst dem tatsächlichen Kontrollfluss einer Anfrage: vom
Dispatch der Grant-Art (4.2.1) über die eigentliche Passwort-Prüfung mit ihren Sicherheitsmaßnahmen (4.2.2),
die Ausstellung der Tokens (4.2.3) und deren Erneuerung (4.2.4), bis zu Logout/Introspektion (4.2.5) und den
beiden Querschnittsmaßnahmen Ratenbegrenzung (4.2.6) und deren nebenläufigkeitssichere Implementierung (4.2.7).

### 4.2.1 Grant-Dispatch

`TokenController` nimmt `POST /oauth2/user/token` entgegen und baut daraus ein typisiertes, _sealed_
`GrantRequest` (entweder `PasswordGrantRequest` oder `RefreshGrantRequest`). Die eigentliche
Geschäftslogik liegt in `TokenService.grantToken`, das über ein Pattern-Matching-`switch` auf die konkrete
Variante verzweigt:

```java

@Transactional(noRollbackFor = TokenGrantException.class)
public TokenResponseDto grantToken(GrantRequest request) {
    return switch (request) {
        case PasswordGrantRequest r -> passwordGrant(r.username(), r.password(), r.scope());
        case RefreshGrantRequest r -> refreshGrant(r.refreshToken());
    };
}
```

Da `GrantRequest` als `sealed interface` modelliert ist, prüft der Java-Compiler, dass dieses `switch`
*erschöpfend* ist: Würde ein dritter Grant-Typ ergänzt, ohne hier einen Fall hinzuzufügen, würde der Code
nicht mehr kompilieren. Dies ersetzt eine sonst nötige Testabdeckung "wurde der neue Grant-Typ auch im
Dispatch berücksichtigt?" durch eine vom Compiler erzwungene Eigenschaft – ein Beispiel dafür, wie moderne
Java-21-Sprachmittel zur Wartbarkeit (NFA-9) beitragen, ohne dass dies im alten PHP-Code (keine vergleichbare
Sprachunterstützung) möglich gewesen wäre.

### 4.2.2 Password Grant: Ablauf und Sicherheitsmaßnahmen

`TokenService.passwordGrant` setzt mehrere Sicherheitsmaßnahmen um, die über die reine OAuth2-Spezifikation
hinausgehen. Zunächst wird geprüft, ob überhaupt ein Nutzer mit dem angegebenen Benutzernamen existiert:

```java
var userOpt = userRepository.findByEmail(username);
if(userOpt.

isEmpty()){
        // always run BCrypt to prevent timing-based user enumeration
        passwordEncoder.

matches(password, DUMMY_HASH);
    log.

warn("passwordGrant — unknown user={}",LogMasking.maskEmail(username));
        throw new

InvalidCredentialsException();
}
```

Ist der Benutzername unbekannt, wird trotzdem ein BCrypt-Vergleich gegen einen konstanten Dummy-Hash
ausgeführt. Ohne diese Zeile würde eine Anfrage mit unbekanntem Benutzernamen messbar schneller
fehlschlagen als eine mit bekanntem Benutzernamen und falschem Passwort (BCrypt ist absichtlich langsam),
wodurch ein Angreifer per Zeitmessung gültige Benutzernamen enumerieren könnte. Diese Maßnahme war im alten
PHP-Backend nicht dokumentiert und ist eine bewusste Verbesserung gegenüber dem Ist-Zustand (NFA-4).

Anschließend prüft `AccountLockoutService.assertNotLocked`, ob das Konto gerade gesperrt ist, bevor das
Passwort überhaupt verglichen wird. Schlägt der Passwortvergleich fehl, zählt
`AccountLockoutService.recordFailedAttempt` die Fehlversuche und sperrt das Konto nach fünf
aufeinanderfolgenden Fehlversuchen für 15 Minuten:

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

Bemerkenswert ist die im Quellcode-Javadoc dokumentierte Randbedingung: Der Fehlversuchszähler wird **nur**
bei erfolgreichem Login zurückgesetzt (`resetOnSuccess`), nicht automatisch nach Ablauf der Sperrzeit. Läuft
die Sperre ab und der nächste Versuch schlägt erneut fehl, wird das Konto sofort wieder für die volle Dauer
gesperrt, da der Zähler nie heruntergezählt wurde. Dieses Verhalten ist bewusst fail-closed gewählt; seine
betrieblichen Implikationen werden in Abschnitt 4.8.4 als bekannte Einschränkung eingeordnet.

Erst nach bestandener Lockout- und Passwortprüfung wird der angeforderte Scope gegen die Rolle des Nutzers
validiert (`ScopeResolver.resolveAuthorizedScope`), um Scope-Eskalation zu verhindern (ein regulärer Nutzer
könnte sonst `scope=admin` anfragen). Erst danach wird ein Token ausgestellt. Abbildung 4.2 veranschaulicht
den vollständigen Ablauf als Sequenzdiagramm.

*Abbildung 4.2: Sequenzdiagramm des Password-Grant-Ablaufs (Client → `TokenController` → `TokenService` →
`AccountLockoutService`/`ScopeResolver`/`TokenIssuer` → Datenbank), inklusive der beiden Abbruchpfade
"unbekannter Nutzer" und "Konto gesperrt".*

### 4.2.3 Tokenausstellung: RS256-JWT und opaker Refresh-Token

`TokenIssuer.issueAccessToken` baut ein RS256-signiertes JWT mit eindeutiger `jti` (JWT-ID) und persistiert
diese `jti` zusätzlich in der Tabelle `access_tokens`:

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

Die Wahl von **RS256** (asymmetrisch, privater Schlüssel signiert, öffentlicher Schlüssel validiert) statt
eines symmetrischen Verfahrens wie HS256 bedeutet, dass der öffentliche Schlüssel verteilt werden könnte,
ohne das Signier-Geheimnis preiszugeben – relevant, falls künftig ein separater Dienst nur Tokens validieren,
aber nicht ausstellen soll. Das Schlüsselpaar wird beim ersten Start erzeugt und unter `OAUTH_KEYS_DIR`
persistiert; ein Verlust dieser Datei entwertet alle zuvor ausgestellten Tokens.

Da JWTs normalerweise zustandslos und damit serverseitig nicht widerrufbar sind, speichert das Backend
zusätzlich jede ausgestellte `jti` in der Datenbank (`access_tokens`) mit einem `revoked`-Flag – ein bewusster
Kompromiss zwischen Zustandslosigkeit (NFA-7) und Widerrufbarkeit (NFA-6, Logout). Der Refresh-Token selbst
ist dagegen **kein** JWT, sondern ein zufälliger 256-Bit-Hex-String (`SecureRandom`), da er nur als
Datenbankschlüssel dient und nicht selbst Ansprüche transportieren muss:

```java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();
...
byte[] bytes = new byte[32];
SECURE_RANDOM.

nextBytes(bytes);

String tokenValue = HexFormat.of().formatHex(bytes);
```

### 4.2.4 Refresh-Token-Rotation

Beim Refresh-Grant wird nicht einfach ein neuer Access-Token zum bestehenden Refresh-Token ausgestellt;
stattdessen wird bei jeder Erneuerung ein **neuer** Refresh-Token erzeugt und der alte widerrufen (Token-Rotation,
`TokenService.refreshGrant`):

```java
String newAccessToken = tokenIssuer.issueAccessToken(user, grantedScope);
String newRefreshToken = tokenIssuer.createRefreshToken(user, grantedScope);
refreshTokenEntity.

revoke();
refreshTokenRepository.

save(refreshTokenEntity);
```

Dies begrenzt den Schaden, falls ein Refresh-Token abgefangen wird: Sobald der legitime Client ihn erneut
verwendet, fällt ein zuvor bereits benutzter (und damit kompromittierter) Token auf, da er in der Datenbank
bereits als widerrufen markiert ist. Zusätzlich validiert `refreshGrant` vor jeder Erneuerung erneut, dass
der zugrunde liegende Nutzer noch existiert und aktiviert ist (`userOpt.get().isEnabled()`); andernfalls wird
der Refresh-Token gelöscht und die Erneuerung abgelehnt. Damit kann ein deaktiviertes Konto keinen neuen
Access-Token mehr erhalten, selbst wenn ein zuvor ausgestellter Refresh-Token noch formal gültig wäre.

### 4.2.5 Logout und Session-Introspektion

`POST /oauth2/user/logout` ruft `TokenRevocationService.logout` auf, das sowohl die `jti` des aktuellen
Access-Tokens als auch — sofern mitgegeben — den Refresh-Token als widerrufen markiert (FA-3). Dieser Pfad
hat bewusst kein Gegenstück im alten PHP-Backend (vgl. Kapitel 3.2, Abschnitt zu FA-3): Da `jti`-Widerruf ein
zusätzlicher, selbst gewählter Mechanismus ist (4.2.3), musste auch die zugehörige Logout-Funktionalität neu
entworfen werden, statt lediglich eine bestehende Verhaltensweise zu übernehmen.

`GET /oauth2/user/session` erlaubt dem Frontend, den aktuellen Anmeldestatus zu prüfen, ohne eigene
Token-Parsing-Logik implementieren zu müssen (`JwtUserResolver` extrahiert die Nutzer-ID aus dem validierten
JWT-Principal, FA-4).

### 4.2.6 Ratenbegrenzung sensibler Endpunkte

Unabhängig von Kontosperren begrenzt `RateLimitingFilter` die Anzahl Anfragen pro Client-IP für
`/oauth2/user/token`, `/user` (Registrierung), `/user/validate_email` und `/user/resend_code` (NFA-5). Der
Filter ist mit `@Order(-200)` so positioniert, dass er **vor** der Spring-Security-Filterkette (`-100`)
läuft – überhöhte Anfragen werden also bereits abgewiesen, bevor überhaupt Authentifizierungslogik ausgeführt
wird:

```java

@Component
@Order(-200)
public class RateLimitingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
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

Die Client-IP wird über `ClientIpResolver` ermittelt, der `X-Forwarded-For` nur dann auswertet, wenn die
Anfrage von einer konfigurierten, vertrauenswürdigen Proxy-Adresse stammt (`RATE_LIMIT_TRUSTED_PROXIES`) –
andernfalls könnte ein Angreifer die Ratenbegrenzung durch Fälschen dieses Headers umgehen. Die vier Limits
(`auth-token`, `register`, `validate-email`, `resend-code`) sind getrennt konfigurierbar (`application.yml`, Default
20/5/10/3 Anfragen pro Minute), da sich Missbrauchsszenarien unterscheiden: Ein
Login-Endpunkt wird legitim häufiger aufgerufen als ein Code-Erneuerungs-Endpunkt, der im Normalbetrieb nur
selten benötigt wird.

### 4.2.7 Sperrenfreie Implementierung der Ratenbegrenzung

Die eigentliche Zählung pro Client-IP und Pfad erfolgt in `TokenBucket`, einer intern von `RateLimiter`
verwalteten Klasse, die pro Schlüssel (IP + Pfad) genau eine Instanz hält. Da mehrere Anfragen vom selben
Client gleichzeitig eintreffen können, muss diese Zählung nebenläufigkeitssicher sein. Anstatt dafür eine
`synchronized`-Sperre zu verwenden, wird der Zustand (letzter Auffüllzeitpunkt und verbleibende Token) in
ein einziges `long` gepackt und per `AtomicLong.compareAndSet` aktualisiert:

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

Diese verlustfreie Codierung von Zeitstempel und Tokenzahl in den oberen bzw. unteren 32 Bit eines einzelnen
`long`-Werts vermeidet, dass zwei separate Felder inkonsistent gelesen werden könnten, und erspart eine
explizite Sperre für einen Codepfad, der bei jeder Anfrage an einen der vier rate-limitierten Endpunkte
durchlaufen wird. Schlägt der `compareAndSet`-Aufruf fehl (ein anderer Thread hat den Zustand zwischenzeitlich
geändert), wird die Schleife einfach wiederholt (optimistische Nebenläufigkeitskontrolle), statt auf eine
Sperre zu warten.

*Abbildung 4.3: Zustandsautomat von `TokenBucket.tryConsume` (Zustände "Token verfügbar"/"keine Token" und
der CAS-Wiederholungspfad), ergänzend zum obigen Codeausschnitt.*

---

## 4.3 Autorisierung / Berechtigungskaskade

Während Abschnitt 4.2 klärt, *wer* ein Nutzer ist (Authentifizierung), entscheidet das Fachmodul
`permission`, *was* dieser Nutzer tun darf. Es implementiert FA-13 ("Berechtigungsprüfung pro
DaaS-Funktion") und ist damit die sicherheitskritischste Komponente des Backends, da die Python-DaaS-Anwendung
vor jedem Funktionsaufruf genau diese Entscheidung abfragt. Die folgenden Abschnitte beschreiben zunächst die
Portierung der eigentlichen Kaskadenlogik (4.3.1) und zwei Detailfragen, die im alten Code nicht abschließend
geklärt waren: Konflikte innerhalb einer Kaskadenstufe (4.3.2) und der Umgang mit fehlerhafter Eingabe (4.3.3).
Abschnitt 4.3.4 erläutert eine Performance-Optimierung, Abschnitt 4.3.5 die resultierende
Testabdeckung.

### 4.3.1 Portierung der PHP-Logik

Das alte Backend entscheidet über `PermissionCalculationService::getPermission()` nach einer vierstufigen
Prioritätsliste. Der tatsächliche PHP-Quellcode (`src/Permissions/Service/PermissionCalculationService.php`)
zeigt diese vier Stufen als eine Folge von frühen Rückgaben: zunächst eine direkte Nutzer-Endpunkt-Regel,
danach — innerhalb einer Schleife über alle Gruppen des Nutzers — eine Nutzergruppen-Endpunkt-Regel, danach
eine Nutzer-Endpunktgruppen-Regel und schließlich, wieder innerhalb einer Gruppen-Schleife, eine
Nutzergruppen-Endpunktgruppen-Regel, mit `AccessEnum::DENY` als Rückfall, falls keine Regel greift. Diese
exakte Reihenfolge musste erhalten bleiben, da die Python-DaaS-Anwendung ihre Berechtigungsprüfungen
unverändert gegen das neue Backend stellt (NFA-2/NFA-3). Die neue `PermissionService.checkPermission`-Methode
bildet die vier Stufen 1:1 nach, jedoch mit `Optional#or` statt verschachtelter `foreach`-Schleifen:

```java
Optional<Boolean> allow = checkDirectUserRule(userId, functionName)
        .or(() -> checkUserGroupRule(groupIdsSupplier.get(), functionName))
        .or(() -> checkUserEndpointGroupRule(userId, functionName))
        .or(() -> checkUserGroupEndpointGroupRule(groupIdsSupplier.get(), functionName));

if(allow.

isEmpty()){
        return PermissionResultDto.

deny(userId, userName);
}
```

Die Verkettung über `Optional#or` setzt die Kaskade direkt in Code um: Jede Stufe liefert entweder ein
Ergebnis (dann stoppt die Kette) oder `Optional.empty()` (dann wird die nächste Stufe versucht). Liefert
keine Stufe ein Ergebnis, greift der Default `deny` (fail-closed, NFA-1). Abbildung 4.4 stellt diese vier
Stufen den gleichnamigen PHP-Entitäten gegenüber, um die Verhaltenskompatibilität zu veranschaulichen.

*Abbildung 4.4: Gegenüberstellung der vierstufigen Kaskade in `PermissionCalculationService` (PHP) und
`PermissionService` (Java) mit identischer Priorität je Stufe.*

### 4.3.2 Konfliktauflösung innerhalb einer Stufe

Eine Detailfrage, die im alten Code nicht offensichtlich dokumentiert war: Was passiert, wenn *innerhalb*
einer Stufe mehrere Regeln gleichzeitig zutreffen (z. B. ein Nutzer ist Mitglied zweier Gruppen mit
widersprüchlichen Regeln für dieselbe Funktion)? Die neue Implementierung löst dies deterministisch über
`allMatch`, wodurch ein einzelnes explizites `deny` stärker wiegt als beliebig viele `allow`-Einträge:

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

Diese "explizite Verweigerung gewinnt"-Regel ist eine bewusste, zusätzlich zum ursprünglichen Verhalten im
Javadoc der Klasse dokumentierte Entscheidung. Im PHP-Original (`PermissionCalculationService::getPermission`)
iteriert eine `foreach`-Schleife über die Gruppen des Nutzers und gibt beim **ersten** Treffer sofort zurück
(`return $endpointUserGroupAccess->relation();`); ist ein Nutzer Mitglied zweier Gruppen mit widersprüchlichen
Regeln für dieselbe Funktion, hängt das Ergebnis dort also von der – nirgends spezifizierten –
Iterationsreihenfolge der Gruppenzuordnung ab. Die neue Implementierung ersetzt dieses zufällige Verhalten
durch eine deterministische, sicherheitsorientierte Regel: Ein einzelnes explizites `deny` wiegt stärker als
beliebig viele `allow`-Einträge derselben Stufe. Dies verhindert, dass eine einzelne großzügige Gruppenregel
eine andernorts bewusst gesetzte Sperre aushebelt, und wurde im Rahmen von AP2 eigens als Abweichung vom
(undokumentierten) PHP-Verhalten analysiert und hier explizit spezifiziert. Der `log.warn`-Aufruf bei
mehrdeutigen Treffern macht diesen Fall zusätzlich im Betrieb beobachtbar, ohne die Entscheidung selbst zu
verändern.

### 4.3.3 Fail-closed bei fehlerhafter Eingabe

Unbekannte Nutzer-IDs, `null`-Werte oder leere Funktionsnamen werden **vor** jeder Datenbankabfrage abgefangen
und führen direkt zu `deny`:

```java
if(userId ==null||functionName ==null||functionName.

isBlank()){
        return PermissionResultDto.

deny(userId !=null?userId:UNRESOLVED_USER_ID, UNKNOWN_USER_NAME);
}

Optional<BaseUser> user = userRepository.findById(userId);
if(user.

isEmpty()){
        return PermissionResultDto.

deny(userId, UNKNOWN_USER_NAME);
}
```

Dies verhindert zugleich unnötige Datenbanklast bei offensichtlich ungültigen Anfragen und schließt die Lücke,
dass ein Programmfehler beim Aufrufer (z. B. vergessene Nutzer-ID) niemals versehentlich zu einem impliziten
`allow` führen kann. Die Methode fragt dabei konsequent in zwei Schritten ab: zunächst die rein syntaktische
Gültigkeit der Eingabe, danach erst die fachliche Existenz des Nutzers – beide Fälle enden in `deny`, werden
aber mit unterschiedlichem Log-Level (`warn` bzw. `info`) protokolliert, da ein unbekannter Nutzer im
Normalbetrieb erwartbarer ist als eine strukturell ungültige Anfrage.

### 4.3.4 Caching der Gruppenzugehörigkeit innerhalb einer Prüfung

Da Stufe 2 und Stufe 4 beide die Gruppenzugehörigkeit des Nutzers benötigen, wird diese nur bei Bedarf und
höchstens einmal pro Anfrage geladen (`Supplier` mit internem Cache), um eine überflüssige zweite
Datenbankabfrage zu vermeiden, falls Stufe 1 bereits entscheidet:

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

### 4.3.5 Testbarkeit der Kaskade

Da die Berechtigungskaskade die sicherheitskritischste Komponente des Backends ist (sie entscheidet laut
NFA-1/NFA-2 fail-closed über jeden Funktionsaufruf der DaaS-Anwendung), ist sie zugleich diejenige mit der
feingranularsten Testabdeckung: `PermissionServiceTest` deckt alle vier Kaskadenstufen einzeln ab, den
Default-Deny-Fall, die in 4.3.2 beschriebene Konfliktauflösung bei mehreren Treffern je Stufe sowie die in
4.3.3 beschriebenen fail-closed-Sonderfälle (`null`-Nutzer-ID, leerer Funktionsname, unbekannter Nutzer).
`PermissionControllerIntegrationTest` ergänzt dies um End-zu-End-Tests gegen den tatsächlichen
HTTP-Endpunkt (Abschnitt 4.8.2).

---

## 4.4 Benutzerverwaltung

### 4.4.1 Registrierung mit E-Mail-Verifikation

`UserService.createUser` legt einen zunächst **deaktivierten** Nutzer an, generiert einen 8-stelligen
hexadezimalen Registrierungscode mit 24 Stunden Gültigkeit und versendet ihn per E-Mail:

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

Der E-Mail-Versand wird bewusst **nach** dem Datenbank-Commit ausgeführt (`runAfterCommit`), nicht innerhalb
der Transaktion:

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

Diese Entscheidung verhindert, dass ein langsamer SMTP-Versand eine Datenbanktransaktion (und damit
gehaltene Sperren) unnötig lange offenhält, und stellt zugleich sicher, dass keine E-Mail verschickt wird,
falls die Transaktion am Ende fehlschlägt und zurückgerollt wird (sonst bekäme ein Nutzer einen Code für ein
Konto, das gar nicht existiert). Dasselbe Muster verwendet `requestApplication` (Abschnitt 4.4.4) und
`resendRegistrationCode`.

`UserService.validateEmail` prüft Code und Ablaufzeit und aktiviert das Konto erst danach:

```java
if(user.getRegistrationCode() ==null||!user.

getRegistrationCode().

equals(registrationCode)){
        return false;
        }
        if(user.

getRegistrationCodeTimeout() !=null
        &&Instant.

now(clock).

isAfter(user.getRegistrationCodeTimeout())){
        return false;
        }
        user.

setEnabled(true);
user.

setRegistrationUsedMoment(Instant.now(clock));
        user.

setRegistrationCode(null);
userRepository.

save(user);
```

Nach erfolgreicher Verifikation wird der Code gelöscht (`setRegistrationCode(null)`), sodass er kein zweites
Mal eingereicht werden kann, und der Verifikationszeitpunkt (`registrationUsedMoment`) dauerhaft festgehalten.

`resendRegistrationCode` ist absichtlich **wortkarg**: Für unbekannte, bereits verifizierte oder
Admin-Konten passiert nach außen sichtbar nichts (kein Fehler, keine Bestätigung) — dies verhindert, dass der
Endpunkt zum Enumerieren registrierter E-Mail-Adressen missbraucht werden kann, ähnlich der Timing-Maßnahme in
4.2.2. Der zuvor gültige Code wird dabei durch einen neuen ersetzt, sodass stets höchstens ein Code pro Konto
gültig ist.

### 4.4.2 Admin-initiierte Verwaltung

`AdminController`/`AdminService` erlauben das Anlegen weiterer Admin-Konten (nur mit `SCOPE_admin`),
`UserController`/`UserService` das Auflisten, Teil-Update (`PATCH`), Aktivieren und Deaktivieren regulärer
Nutzer. Ändert sich das Passwort eines Nutzers oder wird sein Konto deaktiviert, veröffentlicht der Service
ein `UserSessionsInvalidatedEvent`:

```java
if(request.getPassword() !=null){
        accountValidator.

validatePassword(request.getPassword());
        user.

setPassword(passwordEncoder.encode(request.getPassword()));
        eventPublisher.

publishEvent(new UserSessionsInvalidatedEvent(id));
        }
```

```java
public UserDto disableUser(Integer id) {
    User user = getUserOrThrow(id);
    user.setEnabled(false);
    userRepository.save(user);
    eventPublisher.publishEvent(new UserSessionsInvalidatedEvent(id));
    return UserDto.from(user);
}
```

Dieses Event entkoppelt die Nutzerverwaltung von der Token-Widerrufslogik: `UserService` muss nicht wissen, *wie*
Sessions invalidiert werden (das ist Aufgabe eines Listeners im `auth`-Modul) — ein Beispiel für die in
4.1 beschriebene strikte Schichtentrennung, die über reine Methodenaufrufe hinaus auch auf Ereignisebene
eingehalten wird. In beiden Fällen (Passwortänderung, Deaktivierung) ist der Effekt derselbe: Alle zuvor
ausgestellten Access- und Refresh-Tokens des betroffenen Nutzers werden ungültig, sodass ein Angreifer mit
einem zuvor gestohlenen Token nach einer Admin-Intervention nicht länger zugreifen kann.

### 4.4.3 User-Groups als Bindeglied zum Berechtigungs- und Desktop-Modul

`UserGroup` ist das Bindeglied zwischen Benutzerverwaltung, Berechtigungskaskade (Stufen 2 und 4, Abschnitt
4.3) und Desktop-Inventar (Abschnitt 4.5): Eine Gruppe kann sowohl Berechtigungsregeln als auch
Desktop-Group-Zuordnungen tragen. `UserGroupService` stellt CRUD-Operationen für Gruppen bereit; die
eigentliche Verknüpfungslogik zu Desktop-Groups liegt bewusst im `desktop`-Modul (siehe 4.5), um zyklische
Abhängigkeiten zwischen `user` und `desktop` zu vermeiden (durchgesetzt durch dieselbe ArchUnit-Regel aus
4.1.3, die Abhängigkeitsrichtungen zwischen Paketen prüft).

*Abbildung 4.5: Entity-Beziehungsdiagramm `User`/`Admin`/`UserGroup`/`Desktop`/`DesktopGroup` (eigene
Darstellung auf Basis der JPA-Entitäten in `user.model` und `desktop.model`).*

### 4.4.4 Anwendungsfreischaltung (FA-9)

Ein im alten Backend als `UserService::requestApplication()` vorhandener Ablauf erlaubt es einem Nutzer,
Zugriff auf eine weitere, im Frontend sichtbare Anwendung anzufragen; der zuständige Administrator wird
daraufhin per E-Mail benachrichtigt. Dieser Ablauf wurde unverändert als eigener Anwendungsfall (FA-9,
Kapitel 3.2) übernommen und in `UserService.requestApplication` portiert:

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

Bemerkenswert ist, dass diese Methode – im Gegensatz zu `createUser` – rein lesend transaktioniert ist
(`readOnly = true`), da sie keine Datenbankänderung vornimmt, sondern nur eine E-Mail auslöst; dennoch wird
dieselbe `runAfterCommit`-Infrastruktur wiederverwendet, damit ein eventueller Rollback der (hier lesenden) Transaktion
nicht trotzdem zu einem E-Mail-Versand führen kann.

### 4.4.5 Bootstrap eines Standard-Administrators

Da ein frisch aufgesetztes Backend zunächst über kein Admin-Konto verfügt, über das sich weitere Admins
anlegen ließen (FA-7 setzt bereits ein bestehendes Admin-Konto voraus), legt `DataInitializer` beim
Anwendungsstart einmalig einen konfigurierten Standard-Administrator an, sofern noch keiner mit dieser
E-Mail-Adresse existiert:

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

E-Mail-Adresse und Passwort sind über `ADMIN_EMAIL`/`ADMIN_PASSWORD` konfigurierbar (Default:
`admin@design.local`/`Admin123!`); die Funktion lässt sich über `app.admin-initializer.enabled=false`
vollständig deaktivieren, etwa für eine produktive Umgebung, in der Administratorkonten ausschließlich manuell
angelegt werden sollen. `DataInitializer` liegt bewusst im eigenen Paket `bootstrap` statt in `user`, da es
sich um eine anwendungsweite Startaufgabe und keine fachliche Nutzerverwaltungsoperation handelt (vgl. die
Paketgliederung aus 4.1.2).

---

## 4.5 Desktop-Inventar

### 4.5.1 Reines Inventar, keine Bereitstellung

`DesktopService` verwaltet ausschließlich Metadaten zu virtuellen Desktops und Desktop-Groups; die
tatsächliche Bereitstellung von Containern/VMs erfolgt – wie in Kapitel 2.1 und 3.1 dargestellt – durch die
DaaS-Anwendung, nicht durch dieses Backend. `createDesktop` erlaubt optional das kaskadierte Anlegen von
Untergruppen in einem Aufruf:

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

Die Prüfung auf doppelte Beschreibung erfolgt **zweistufig**: zunächst innerhalb der aktuellen Anfrage selbst
(`subGroupDescriptions.add(...)` gibt `false` zurück bei einem Duplikat innerhalb derselben Anfrage), danach
gegen den bereits gespeicherten Datenbestand (`existsByDescription`). Ohne die erste Prüfung könnte eine
einzelne Anfrage zwei Untergruppen mit identischem Namen anlegen, da die Datenbankprüfung zum Zeitpunkt der
Anfrage noch keinen Konflikt sähe (beide Einträge existieren ja noch nicht).

### 4.5.2 Verknüpfung von User-Groups und Desktop-Groups

`addUserGroupToDesktopGroup` verknüpft eine User-Group mit einer Desktop-Group und stellt dabei sicher, dass
keine doppelte Assoziation entsteht:

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

Die Verknüpfung wird **beidseitig** gepflegt (`userGroup.getDesktopGroups().add(...)` und
`desktopGroup.getUserGroups().add(...)` innerhalb von `linkUserGroupAndDesktopGroup`), da beide Entitäten
eine n:m-Beziehung über JPA abbilden und die In-Memory-Konsistenz der bidirektionalen Assoziation sonst von
der jeweiligen Abfragerichtung abhinge. Diese Verknüpfung ist die technische Grundlage für FA-12 ("Nutzer
kann prüfen, ob er Zugriff auf einen Desktop hat"): Über die Kette Nutzer → User-Group → Desktop-Group →
Desktop lässt sich für jeden Desktop feststellen, welche Nutzer über ihre Gruppenmitgliedschaften Zugriff
besitzen.

*Abbildung 4.6: n:m-Beziehungen `Desktop` ↔ `DesktopGroup` ↔ `UserGroup` (eigene Darstellung, ergänzend zu
Abbildung 4.5).*

---

## 4.6 Querschnittsthemen

### 4.6.1 Sicherheitsfilterkette

`SecurityConfig` konfiguriert eine zustandslose Filterkette: CSRF ist deaktiviert (keine Cookies/Sessions),
`SessionCreationPolicy.STATELESS` ist gesetzt, und bis auf eine explizite Positivliste öffentlicher Endpunkte
erfordert jede Anfrage ein gültiges JWT:

```java
http.cors(cors ->cors.

configurationSource(corsConfigurationSource))
        .

csrf(AbstractHttpConfigurer::disable)
        .

sessionManagement(session ->session.

sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .

authorizeHttpRequests(auth ->{
        auth.

requestMatchers(HttpMethod.POST, "/user").

permitAll();
            auth.

requestMatchers(HttpMethod.POST, "/user/validate_email").

permitAll();
            auth.

requestMatchers(HttpMethod.POST, "/user/resend_code").

permitAll();
            auth.

requestMatchers(HttpMethod.POST, "/oauth2/user/token").

permitAll();
            auth.

requestMatchers(HttpMethod.GET, "/version").

permitAll();

            if(securityProperties.

isSwaggerPublic()){
        auth.

requestMatchers(SWAGGER_ENDPOINTS).

permitAll();
            }else{
                    auth.

requestMatchers(SWAGGER_ENDPOINTS).

hasAuthority(Authorities.SCOPE_ADMIN);
            }
                    auth.

anyRequest().

authenticated();
        })
                .

oauth2ResourceServer(oauth2 ->oauth2.

jwt(jwt ->jwt.

decoder(jwtDecoder)));
```

Diese "deny-by-default, erlaubt nur explizit gelistete Endpunkte"-Struktur ist die Anwendung desselben
fail-closed-Prinzips wie in der Berechtigungskaskade (4.3), diesmal auf Ebene der HTTP-Schicht statt der
fachlichen Autorisierung. Bemerkenswert ist die Sonderbehandlung der Swagger-UI-Endpunkte über die
Konfigurationseigenschaft `app.security.swagger-public`: In Produktivumgebungen (`application-prod.yml`)
bleibt sie `false`, sodass die API-Dokumentation nur mit einem Admin-Token einsehbar ist; im lokalen
Entwicklungsprofil (`application-local.yml`) ist sie auf `true` gesetzt, um die in Abschnitt 4.7 beschriebene
Demonstration ohne vorherigen Login zu ermöglichen.

### 4.6.2 Fehlerbehandlung

`GlobalExceptionHandler` sowie domänenspezifische Handler (`TokenExceptionHandler`,
`SessionExceptionHandler`) übersetzen typisierte Exceptions (`InvalidCredentialsException`,
`AccountLockedException`, `UserNotFoundException`, `WeakPasswordException` u. a.) in ein einheitliches
`ErrorResponse`-JSON-Format mit passendem HTTP-Statuscode. Dies ersetzt die im alten PHP-Backend über
Middleware (`InvalidUserInputHandlerMiddleware`) realisierte, laut Ausschreibung schlecht dokumentierte
Fehlerbehandlung durch eine zentrale, typsichere Lösung: Jede fachliche Fehlerbedingung ist eine eigene,
sprechend benannte Exception-Klasse statt eines generischen Fehlercodes, wodurch sowohl der Aufrufort im
Service als auch die Zuordnung zum HTTP-Statuscode im Handler an einer Stelle nachvollziehbar sind.

### 4.6.3 Eingabevalidierung

Alle Request-DTOs nutzen Bean Validation (`jakarta.validation`), um strukturell ungültige Anfragen bereits
vor Erreichen der Service-Schicht abzulehnen. Die Passwortregeln sind dabei zentral an einer Stelle definiert,
damit Validierungsannotation und Fehlermeldung nicht auseinanderlaufen können:

```java
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String MESSAGE =
            "password must be 8-72 characters and contain uppercase, lowercase, and a digit";
    public static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).{8,72}$";
}
```

Die Obergrenze von 72 Zeichen ist kein willkürlicher Wert, sondern entspricht der technischen Grenze von
BCrypt, das nur die ersten 72 Byte eines Passworts in den Hash einbezieht; ein längeres Passwort würde
stillschweigend auf diese Länge gekürzt, was Nutzende fälschlich annehmen ließe, ein längeres Passwort erhöhe
die Sicherheit. `PasswordPolicy` wird sowohl von der Bean-Validation-Annotation auf den DTOs als auch von
`AccountValidator.validatePassword` referenziert (serverseitige Zweitprüfung bei Registrierung und
Passwortänderung, Abschnitt 4.4.1/4.4.2), sodass beide Prüfpfade garantiert dieselbe Regel anwenden.

### 4.6.4 CORS, Protokollierung und PII-Maskierung

`CorsConfig` erlaubt standardmäßig nur den in `app.frontend.url` konfigurierten Origin. Da JWTs
personenbezogene Daten (E-Mail, Name) als Claims enthalten, maskiert `LogMasking.maskEmail` E-Mail-Adressen
in allen Log-Zeilen, die Nutzeraktionen protokollieren (siehe z. B. Abschnitt 4.2.2); dies betrifft sämtliche
`service`-Klassen, die mit Nutzerdaten arbeiten, und wurde — ebenso wie die Timing-Angriffs-Abwehr in 4.2.2 —
nicht aus dem alten Backend übernommen, sondern als eigenständige Verbesserung gegenüber dem dokumentierten
Ist-Zustand ergänzt.

### 4.6.5 API-Dokumentation über OpenAPI/Swagger

Anstelle der manuell gepflegten OpenAPI-Annotationen des alten Backends (`zircote/swagger-php`, vgl. Tabelle
3.1) generiert `springdoc-openapi` die API-Spezifikation automatisch aus den Controller- und
DTO-Klassen. `OpenApiConfig` ergänzt lediglich die global benötigten Metadaten (Titel, Version) sowie das
Bearer-Token-Sicherheitsschema, das die Swagger-UI für authentifizierte Testaufrufe anbietet:

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

Da die Spezifikation direkt aus dem Code abgeleitet wird, kann sie nicht wie im alten Backend von der
tatsächlichen Implementierung abweichen (ein in der Ausschreibung implizit mitgemeinten Symptom mangelnder
Dokumentation) — jede Änderung an einem Controller oder DTO wirkt sich unmittelbar auf `/v3/api-docs` und
die Swagger-UI aus. Diese generierte Dokumentation ist zugleich die Grundlage des in Abschnitt 4.7
beschriebenen Demonstrators.

### 4.6.6 Geplante Aufräumarbeiten

`TokenCleanupService` entfernt abgelaufene/widerrufene Zeilen aus `access_tokens`/`refresh_tokens` zeitgesteuert
über Spring Scheduling, um unbegrenztes Tabellenwachstum zu vermeiden – ein Aspekt, der im alten Backend über
den separaten Yacron-Cron-Dienst gelöst war und hier in den Anwendungsprozess selbst integriert wurde (ein
Betriebsartefakt weniger).

---

## 4.7 Demonstrator (AP5)

Arbeitspaket AP5 verlangt den Aufbau eines Demonstrators, der die zentralen Anwendungsfälle aus Abschnitt 3.5
vorführt. Da das neue Backend – anders als die DaaS-Anwendung – keine eigene grafische Oberfläche besitzt,
dient die aus Abschnitt 4.6.5 generierte Swagger-UI als Demonstrationswerkzeug: Sie erlaubt, jeden Endpunkt
direkt im Browser mit konkreten Beispieldaten aufzurufen und die Antwort inklusive HTTP-Statuscode
einzusehen, ohne einen separaten HTTP-Client (z. B. Postman oder curl) zu benötigen.

### 4.7.1 Aufbau der Demonstrationsumgebung

Der Demonstrator wird aus dem unveränderten Produktionsartefakt betrieben: Das mehrstufige `Dockerfile` baut
das Projekt mit Maven (`mvn package -DskipTests`) und kopiert ausschließlich das resultierende JAR in ein
schlankes `eclipse-temurin:21-jre-alpine`-Laufzeitimage:

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

Das Volume `/app/keys` nimmt das in Abschnitt 4.2.3 beschriebene RS256-Schlüsselpaar auf, damit es einen
Container-Neustart übersteht. Für die eigentliche Demonstration genügt daneben eine lokal erreichbare
MariaDB-Instanz; die Konfiguration erfolgt vollständig über Umgebungsvariablen (`.env.example`):

```
DB_URL=jdbc:mariadb://localhost:3306/design_backend
DB_USERNAME=design
DB_PASSWORD=design_password
OAUTH_KEYS_DIR=./keys
MAIL_ENABLED=false
FRONTEND_URL=http://localhost:3000
```

Mit `MAIL_ENABLED=false` (Standardwert) versendet `MailService` keine echten E-Mails, protokolliert den
Registrierungscode aber auf DEBUG-Level in der Anwendungskonsole. Dadurch lässt sich der vollständige
Registrierungs-/Verifikationsablauf (FA-5) demonstrieren, ohne einen SMTP-Server bereitstellen zu müssen —
der Code wird einfach aus dem Log abgelesen und in `/user/validate_email` eingetragen. Beim ersten Start legt
`DataInitializer` (Abschnitt 4.4.5) automatisch einen Administrator (`admin@design.local` /
`Admin123!`) an, über den sich weitere Admin- und Nutzerkonten verwalten lassen, ohne zunächst manuell einen
Datensatz in die Datenbank einfügen zu müssen. Im lokalen Profil (`application-local.yml`, Standardprofil)
ist `app.security.swagger-public=true` gesetzt, sodass `http://localhost:8080/swagger-ui.html` ohne
vorherige Anmeldung erreichbar ist.

### 4.7.2 Vorgeführte Abläufe

Anhand der Swagger-UI werden die in Kapitel 3.5 beschriebenen Kernabläufe vorgeführt:

1. **Login und Berechtigungsprüfung (FA-1, FA-13):** `POST /oauth2/user/token` mit dem Formularparameter
   `grant_type=password` und den Zugangsdaten des Standard-Administrators liefert ein Zugriffstoken; dieses
   wird über die "Authorize"-Schaltfläche der Swagger-UI hinterlegt. Anschließend liefert
   `GET /permission?user_id=...&function=...` eine Allow/Deny-Entscheidung gemäß der Kaskade aus Abschnitt 4.3.
2. **Token-Erneuerung und Logout (FA-2, FA-3):** `POST /oauth2/user/token` mit `grant_type=refresh_token` und
   dem zuvor erhaltenen Refresh-Token liefert ein neues Tokenpaar; das alte Refresh-Token ist danach gemäß
   Abschnitt 4.2.4 nicht mehr verwendbar. `POST /oauth2/user/logout` widerruft das aktuelle Token, eine
   erneute Anfrage mit demselben Access-Token wird anschließend mit `401 Unauthorized` abgelehnt.
3. **Registrierung und E-Mail-Verifikation (FA-5):** `POST /user` legt ein deaktiviertes Konto an; der
   Registrierungscode wird (bei `MAIL_ENABLED=false`) aus dem Log abgelesen und an
   `POST /user/validate_email` übergeben, wonach ein Login mit diesem Konto möglich ist.
4. **Desktop- und Desktopgruppen-Anlage (FA-10, FA-11):** `POST /desktop` legt mit dem Administrator-Token
   einen Desktop samt Untergruppe an; `POST /desktop-group/{id}/user-group/{id}` verknüpft diese Gruppe mit
   einer zuvor über `POST /user-group` angelegten Nutzergruppe.
5. **Anwendungsfreischaltung (FA-9):** `POST /user/{id}/request-application` löst — ebenfalls sichtbar im
   Log, solange `MAIL_ENABLED=false` ist — die in Abschnitt 4.4.4 beschriebene Benachrichtigung aus.

*Abbildung 4.7: Screenshot der Swagger-UI mit Beispielanfrage/-antwort für den Password-Grant (`POST
/oauth2/user/token`), aufgenommen gegen die unter 4.7.1 beschriebene lokale Demonstrationsumgebung.*

*Abbildung 4.8: Screenshot der Swagger-UI für `GET /permission`, mit einer `allow`- und einer
`deny`-Antwort für zwei unterschiedliche Nutzer-/Funktionskombinationen.*

Der Demonstrator belegt damit unmittelbar, dass die in Kapitel 3.5 festgelegten Anwendungsfälle mit dem
neuen Backend ausführbar sind, ohne dass Frontend oder DaaS-Anwendung beteiligt sein müssen — beide
kommunizieren production-seitig über exakt dieselben, hier vorgeführten HTTP-Schnittstellen.

---

## 4.8 Tests & Evaluation (AP6)

### 4.8.1 Automatisierte Qualitätssicherung im Build

Der Build (`mvn verify`) erzwingt zwei automatisierte Qualitätsschranken: die 19 ArchUnit-Regeln aus 4.1.3
und eine Mindest-Testabdeckung von 70 % der Codezeilen über JaCoCo:

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

Unterschreitet die Testabdeckung diesen Schwellenwert, schlägt der Build fehl — Testabdeckung ist damit kein
optionales Qualitätsmerkmal, sondern eine harte Build-Voraussetzung (NFA-9).

### 4.8.2 Testarten und Umfang

Die Testsuite umfasst 30 Testklassen mit insgesamt **247 Testmethoden**, alle erfolgreich (0 Fehlschläge, 0
Fehler, Stand: Abgabeversion, ermittelt aus den Surefire-Reports `target/surefire-reports/*.xml`). Tabelle
4.2 schlüsselt dies nach Testart auf.

*Tabelle 4.2: Testsuite nach Art*

| Testart                                            | Beispiele                                                                                                                                                                                                                                                                                                                        | Umfang    |
|----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| Architektur-Tests                                  | `ArchitectureTest` (Schichtung, Namenskonventionen, Injektionsstil)                                                                                                                                                                                                                                                              | 19 Regeln |
| Service-Unit-Tests                                 | `TokenServiceTest`, `PermissionServiceTest`, `AccountLockoutServiceTest`, `UserServiceTest`, `DesktopServiceTest`, `UserGroupServiceTest`, `AdminServiceTest`, `MailServiceTest`                                                                                                                                                 | 8 Klassen |
| Hilfs-/Infrastruktur-Unit-Tests                    | `TokenIssuerTest`, `ScopeResolverTest`, `JwtUserResolverTest`, `SessionServiceTest`, `TokenCleanupServiceTest`, `TokenRevocationValidatorTest`, `SecurityPropertiesTest`, `GrantRequestTest`, `GlobalExceptionHandlerTest`                                                                                                       | 9 Klassen |
| Controller-Unit-Tests (ohne vollen Spring-Kontext) | `PermissionControllerTest`, `DesktopGroupControllerTest`                                                                                                                                                                                                                                                                         | 2 Klassen |
| Integrationstests (voller Spring-Kontext, H2)      | `TokenControllerIntegrationTest`, `UserControllerIntegrationTest`, `AdminControllerIntegrationTest`, `DesktopControllerIntegrationTest`, `UserGroupControllerIntegrationTest`, `PermissionControllerIntegrationTest`, `OAuth2SessionControllerIntegrationTest`, `AuthHardeningIntegrationTest`, `InputValidationIntegrationTest` | 9 Klassen |
| Anwendungskontext-Test                             | `DesignBackendApplicationTests` (Spring-Kontext startet fehlerfrei)                                                                                                                                                                                                                                                              | 1 Klasse  |

Integrationstests laufen gegen eine In-Memory-H2-Datenbank statt der produktiv eingesetzten MariaDB
(`spring-boot-starter-test`-Testprofil), um ohne externe Infrastruktur und mit kurzer Laufzeit lauffähig zu
sein; die Flyway-Migrationen aus 4.1.5 werden dabei unverändert gegen H2 ausgeführt, sodass Schema und
produktiver Pfad identisch bleiben. `AuthHardeningIntegrationTest` und `InputValidationIntegrationTest`
prüfen gezielt die in 4.2 und 4.6.3 beschriebenen Sicherheits- und Validierungsmaßnahmen End-to-End (z. B.
Kontosperrung nach wiederholten Fehlversuchen, Ablehnung zu schwacher Passwörter) statt nur auf
Service-Ebene.

### 4.8.3 Tatsächlich erreichte Testabdeckung

Der zuletzt erzeugte JaCoCo-Bericht (`target/site/jacoco/index.html`) weist für das Gesamtprojekt folgende
Werte aus (Tabelle 4.3):

*Tabelle 4.3: JaCoCo-Testabdeckung, Gesamtprojekt (91 geprüfte Klassen)*

| Metrik                   | Abgedeckt | Gesamt | Anteil |
|--------------------------|-----------|--------|--------|
| Instruktionen            | 4.441     | 5.120  | 86 %   |
| Verzweigungen (Branches) | 245       | 332    | 73 %   |
| Zeilen                   | 973       | 1.118  | 87 %   |
| Methoden                 | 240       | 264    | 91 %   |
| Klassen                  | 87        | 91     | 96 %   |

Die tatsächliche Zeilenabdeckung von 87 % liegt damit deutlich über dem im Build erzwungenen Minimum von
70 % (4.8.1); die Schranke wirkt somit aktuell nicht limitierend, verhindert aber, dass zukünftige Änderungen
diesen Wert unbemerkt und schleichend absenken. Die niedrigere Verzweigungsabdeckung (73 %) gegenüber der
Zeilenabdeckung (87 %) ist typisch für Code mit vielen fail-closed-Sonderfällen wie der Berechtigungskaskade (4.3) und
der Fehlerbehandlung (4.6.2): Jede zusätzliche `if`-Verzweigung für einen Randfall erhöht die Zahl
der Verzweigungen schneller, als sie durch neue Tests vollständig in beiden Richtungen (wahr/falsch)
abgedeckt wird.

### 4.8.4 Bekannte Einschränkung: Dauersperre nach Ablauf und erneutem Fehlversuch

Ein im Rahmen der Testerstellung (Abschnitt 4.8.2, `AccountLockoutServiceTest`) bewusst nachvollzogenes
Verhalten betrifft die in 4.2.2 beschriebene Kontosperrung: Der Fehlversuchszähler wird laut Javadoc von
`AccountLockoutService` ausschließlich bei erfolgreichem Login zurückgesetzt, nicht durch das Ablaufen der
Sperrzeit selbst. Läuft eine Sperre ab und der nächste Anmeldeversuch schlägt – etwa durch ein weiterhin
falsch erinnertes Passwort – erneut fehl, wird das Konto umgehend für weitere 15 Minuten gesperrt, obwohl der
Zähler zu diesem Zeitpunkt formal bereits bei fünf Fehlversuchen "hängen geblieben" war. Für einen
tatsächlichen Angreifer ist dies unkritisch (es verlängert lediglich die Sperre), kann aber bei einem
Nutzer mit einem schlicht vergessenen Passwort zu einer gefühlten Dauersperre führen, da jeder weitere
Fehlversuch die Sperrzeit erneut vollständig startet. Diese Einschränkung ist bewusst in Kauf genommen (fail-closed über
Benutzerfreundlichkeit gestellt) und wird in Kapitel 5 als mögliche zukünftige Verbesserung (z. B. schrittweiser
Zähler-Abbau statt alles-oder-nichts-Reset) wieder aufgegriffen.

### 4.8.5 Vergleich mit dem alten Backend

Tabelle 4.4 stellt die wichtigsten Qualitätssicherungsmaßnahmen beider Backends gegenüber. Grundlage sind die
tatsächlichen Konfigurationsdateien beider Repositories (`pom.xml` dieses Projekts; `phpunit.xml`,
`phpstan.neon.dist`, `ARCHITECTURE.md` des PHP-Projekts), nicht nur deren Dokumentation.

*Tabelle 4.4: Qualitätssicherung, altes Backend (PHP) vs. neues Backend (Java)*

| Kriterium                       | Altes Backend (PHP)                                                                                                                                      | Neues Backend (Java)                                                                                                               |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Schichtenarchitektur erzwungen? | Teilweise: `PHPat` prüft Modulabhängigkeiten, aber nicht die interne `Model → Repository → Service → Controller`-Schichtung je Modul                     | Ja: ArchUnit prüft zusätzlich die interne Schichtung jedes Fachmoduls sowie Namens- und Injektionskonventionen (19 Regeln)         |
| Testabdeckung als Build-Gate?   | Nein: `phpunit.xml` konfiguriert Coverage-Erfassung (`<coverage includeUncoveredFiles="true"/>`), aber keinen Mindestwert, der den Build scheitern lässt | Ja: JaCoCo erzwingt ≥ 70 % Zeilenabdeckung im Build; tatsächlich erreicht: 87 % (Tabelle 4.3)                                      |
| Anzahl automatisierter Tests    | 69 PHPUnit-Testdateien (`tests/src/{Unit,Integration}`)                                                                                                  | 30 JUnit-5-Testklassen mit 247 Testmethoden                                                                                        |
| Berechtigungskaskade            | 4-stufig, `PermissionCalculationService`, Konfliktfall bei mehreren Treffern pro Stufe nicht dokumentiert                                                | 4-stufig, identische Priorität, zusätzlich dokumentierte und getestete Konfliktauflösung ("explizite Verweigerung gewinnt", 4.3.2) |
| Schema-Verwaltung               | Doctrine Migrations                                                                                                                                      | Flyway, Hibernate nur `validate`                                                                                                   |
| Auth-Mechanismus                | `league/oauth2-server`, Password Grant                                                                                                                   | Eigener RS256-JWT-Aussteller + Spring-Security-Resource-Server, zusätzlich Timing-Schutz und Kontosperrung (4.2.2)                 |
| API-Dokumentation               | Manuell gepflegte `zircote/swagger-php`-Annotationen                                                                                                     | Automatisch aus Code generiert (`springdoc-openapi`, 4.6.5) — kann nicht von der Implementierung abweichen                         |

Die Zahl der Testdateien allein (69 vs. 30) ist dabei kein verlässlicher Qualitätsindikator, da PHPUnit und
JUnit 5 Tests unterschiedlich granular organisieren (eine JUnit-5-Klasse kann mehrere, mit `@Nested`
gruppierte Testmethoden enthalten); aussagekräftiger ist der direkte Vergleich der automatisierten
Qualitätsschranken: Während das alte Backend Architekturregeln bereits besaß, aber keine erzwungene
Testabdeckung, kombiniert das neue Backend beide Mechanismen als harte Build-Voraussetzung. Dies ist die
unmittelbare, messbare Antwort auf die in der Ausschreibung konstatierte mangelnde Qualität des
PHP-Backends und erfüllt NFA-8 und NFA-9 aus Kapitel 3.2 nicht nur konzeptionell, sondern nachweisbar durch
einen aktuell grünen Build.
