# 4 Realisierung (Entwurf)

> Status: Ausführlicher Entwurf, Grundlage für Diskussion/Ausbau. Ziel-Umfang laut Vorlage: 30–40 Seiten
> für ein Masterthesis-Kapitel 4; dieser Entwurf ist bewusst ausführlicher gehalten (Code-Erklärungen und
> Begründungen), damit er direkt als Textgrundlage dient. Alle Codeausschnitte sind **kurz** (Vorlage-Regel:
> kein vollständiger Quellcode-Abdruck) und stammen wortwörtlich aus `../../src/main/java/de/frauas/design/backend`
> dieses Repositories (Stand: aktueller Code) bzw. aus `../design-daas-application-backend/` (altes
> PHP-Backend, zum Vergleich). `[TODO]`/`[Bild: ...]` markieren noch zu erstellende Abbildungen.

---

## 4.1 Architekturentscheidungen

### 4.1.1 Ausgangslage und Entscheidungsrahmen

Die in Kapitel 3 abgeleiteten Anforderungen verlangen zweierlei gleichzeitig: Erstens muss das neue Backend
**verhaltenskompatibel** zum alten PHP-Backend sein (NFA-2, NFA-3), da Frontend und DaaS-Anwendung nicht
Teil dieser Arbeit sind und unverändert weiterlaufen müssen. Zweitens soll die Neuimplementierung gerade die
Mängel des alten Backends beheben – mangelnde Wartbarkeit und fehlende Dokumentation (vgl. Ausschreibung,
Kapitel 1). Diese beiden Ziele bestimmen die zentralen Architekturentscheidungen dieses Kapitels.

### 4.1.2 Modulare Gliederung nach fachlicher Domäne

Das alte PHP-Backend ist bereits vertikal in Module gegliedert (`Main`, `Util`, `OAuth2`, `User`, `Desktop`,
`Permissions`; siehe `ARCHITECTURE.md` des PHP-Repos), jedes mit der internen Schichtung
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

Zusätzliche Regeln in derselben Testklasse (`ArchitectureTest`) erzwingen Namenskonventionen (`@Service`-Klassen müssen
auf `Service`/`ServiceImpl` enden, `@Repository`-Klassen auf `Repository` usw.),
Konstruktorinjektion statt Feldinjektion in Services, ein Verbot von `java.util.logging`/`System.out`, und
dass `shared` von keinem Fachmodul abhängen darf (Vermeidung zirkulärer Kopplung). Ein Verstoß gegen eine
dieser Regeln lässt `mvn test` fehlschlagen – die Architektur ist damit nicht nur eine Absicht, sondern ein
Build-Gate. Dies ist die direkte technische Antwort auf die in der Ausschreibung genannte mangelnde Qualität
des Vorgängersystems.

### 4.1.4 Zustandslose Authentifizierung statt vollem Autorisierungsserver

Das alte Backend verwendet `league/oauth2-server` mit einem Password-Grant. Für das neue Backend wurde
geprüft, ob ein vollständiger `spring-security-oauth2-authorization-server` eingesetzt werden soll. Da nur
zwei Grant-Typen benötigt werden (`password`, `refresh_token`) und `POST /oauth2/user/token` Zugangsdaten
direkt per Formularparameter entgegennimmt – wie es der alte Vertrag mit dem Frontend vorsieht –, wurde
stattdessen eine schlanke Eigenimplementierung gewählt: ein selbst gebauter RS256-JWT-Aussteller (`TokenIssuer`) in
Kombination mit Spring Securitys OAuth2-Resource-Server-Validierung (`JwtDecoder`). Diese
Entscheidung vermeidet den Konfigurationsaufwand eines vollständigen Autorisierungsservers für einen Vertrag,
der ohnehin nur zwei Grant-Typen abdeckt (vgl. `../ARC42.md`, Abschnitt 9, ADR-Tabelle).

### 4.1.5 Schema ausschließlich über Flyway

Hibernate wird im Modus `ddl-auto: validate` betrieben; es erzeugt zu keinem Zeitpunkt Schema automatisch.
Alle Schemaänderungen laufen über versionierte Flyway-Migrationen (`V1__init.sql` … `V4__*.sql`). Diese
Entscheidung macht jede Schemaänderung explizit review- und nachvollziehbar und vermeidet
Hibernate-Auto-DDL-Überraschungen, die zwischen Entwicklungs-, Test- und Produktivumgebung unterschiedlich
ausfallen könnten – ein Qualitätsziel, das direkt auf NFA-10 (Kapitel 3.2) einzahlt.

[TODO: Architekturbild (Bild 4.1): Paketdiagramm der 4 Fachmodule + Querschnittscode, analog zum Diagramm
in 4.1.2, aber als selbst gezeichnete Grafik statt Textbaum.]

---

## 4.2 Authentifizierung

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
hinausgehen:

```java
var userOpt = userRepository.findByEmail(username);
if(userOpt.

isEmpty()){
        // always run BCrypt to prevent timing-based user enumeration
        passwordEncoder.

matches(password, DUMMY_HASH);
    throw new

InvalidCredentialsException();
}
```

Ist der Benutzername unbekannt, wird trotzdem ein BCrypt-Vergleich gegen einen konstanten Dummy-Hash
ausgeführt. Ohne diese Zeile würde eine Anfrage mit unbekanntem Benutzernamen messbar schneller
fehlschlagen als eine mit bekanntem Benutzernamen und falschem Passwort (BCrypt ist absichtlich langsam),
wodurch ein Angreifer per Zeitmessung gültige Benutzernamen enumerieren könnte. Diese Maßnahme war im alten
PHP-Backend nicht dokumentiert und ist eine bewusste Verbesserung gegenüber dem Ist-Zustand (NFA-1).

Anschließend prüft `AccountLockoutService.assertNotLocked`, ob das Konto gerade gesperrt ist, bevor das
Passwort überhaupt verglichen wird. Schlägt der Passwortvergleich fehl, zählt
`AccountLockoutService.recordFailedAttempt` die Fehlversuche und sperrt das Konto nach 5 aufeinanderfolgenden
Fehlversuchen für 15 Minuten:

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

Bemerkenswert ist die im Code dokumentierte Randbedingung: Der Fehlversuchszähler wird **nur** bei
erfolgreichem Login zurückgesetzt (`resetOnSuccess`), nicht automatisch nach Ablauf der Sperrzeit. Läuft die
Sperre ab und der nächste Versuch schlägt erneut fehl, wird das Konto sofort wieder für die volle Dauer
gesperrt, da der Zähler nie heruntergezählt wurde. Dieses Verhalten ist bewusst (fail-closed), sollte aber im
Betrieb beobachtet werden, da es bei wiederholt falschen, aber nicht böswilligen Versuchen (z. B. vergessenes
Passwort) zu einer de-facto-Dauersperre führen kann [TODO: ggf. als bekannte Einschränkung in Kapitel 5
aufgreifen].

Erst nach bestandener Lockout- und Passwortprüfung wird der angeforderte Scope gegen die Rolle des Nutzers
validiert (`ScopeResolver.resolveAuthorizedScope`), um Scope-Eskalation zu verhindern (ein regulärer Nutzer
könnte sonst `scope=admin` anfragen). Erst danach wird ein Token ausgestellt.

### 4.2.3 Tokenausstellung: RS256-JWT und opaker Refresh-Token

`TokenIssuer.issueAccessToken` baut ein RS256-signiertes JWT mit eindeutiger `jti` (JWT-ID) und persistiert
diese `jti` zusätzlich in der Tabelle `access_tokens`:

```java
JwtClaimsSet claims = JwtClaimsSet.builder()
        .id(jti).issuer(issuer).subject(String.valueOf(user.getId()))
        .issuedAt(now).expiresAt(now.plusSeconds(accessTokenTtlSeconds))
        .claim("email", user.getEmail()).claim("name", user.getName()).claim("scope", scope)
        .build();
JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
```

Die Wahl von **RS256** (asymmetrisch, privater Schlüssel signiert, öffentlicher Schlüssel validiert) statt
eines symmetrischen Verfahrens wie HS256 bedeutet, dass der öffentliche Schlüssel verteilt werden könnte,
ohne das Signier-Geheimnis preiszugeben – relevant, falls künftig ein separater Dienst nur Tokens validieren,
aber nicht ausstellen soll. Das Schlüsselpaar wird beim ersten Start erzeugt und unter `OAUTH_KEYS_DIR`
persistiert; ein Verlust dieser Datei entwertet alle zuvor ausgestellten Tokens (dokumentiertes Risiko, siehe
`../ARC42.md` Abschnitt 11).

Da JWTs normalerweise zustandslos und damit serverseitig nicht widerrufbar sind, speichert das Backend
zusätzlich jede ausgestellte `jti` in der Datenbank (`access_tokens`) mit einem `revoked`-Flag – ein bewusster
Kompromiss zwischen Zustandslosigkeit (NFA-7) und Widerrufbarkeit (NFA-6, Logout). Der Refresh-Token selbst
ist dagegen **kein** JWT, sondern ein zufälliger 256-Bit-Hex-String (`SecureRandom`), da er nur als
Datenbankschlüssel dient und nicht selbst Ansprüche transportieren muss:

```java
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
bereits als widerrufen markiert ist.

### 4.2.5 Logout und Session-Introspektion

`POST /oauth2/user/logout` ruft `TokenRevocationService.logout` auf, das sowohl die `jti` des aktuellen
Access-Tokens als auch — sofern mitgegeben — den Refresh-Token als widerrufen markiert.
`GET /oauth2/user/session` erlaubt dem Frontend, den aktuellen Anmeldestatus zu prüfen, ohne eigene
Token-Parsing-Logik implementieren zu müssen (`JwtUserResolver` extrahiert die Nutzer-ID aus dem validierten
JWT-Principal).

### 4.2.6 Ratenbegrenzung sensibler Endpunkte

Unabhängig von Kontosperren begrenzt `RateLimitingFilter` die Anzahl Anfragen pro Client-IP für
`/oauth2/user/token`, `/user` (Registrierung), `/user/validate_email` und `/user/resend_code`. Der Filter ist
mit `@Order(-200)` so positioniert, dass er **vor** der Spring-Security-Filterkette (`-100`) läuft – überhöhte
Anfragen werden also bereits abgewiesen, bevor überhaupt Authentifizierungslogik ausgeführt wird:

```java

@Component
@Order(-200)
public class RateLimitingFilter extends OncePerRequestFilter {
    ...
            if(rateLimiter.tryConsume(clientIp,path,limit))

    {
        filterChain.doFilter(request, response);
        return;
    }
    response.setStatus(429);
    ...
}
```

Die Client-IP wird über `ClientIpResolver` ermittelt, der `X-Forwarded-For` nur dann auswertet, wenn die
Anfrage von einer konfigurierten, vertrauenswürdigen Proxy-Adresse stammt (`RATE_LIMIT_TRUSTED_PROXIES`) –
andernfalls könnte ein Angreifer die Ratenbegrenzung durch Fälschen dieses Headers umgehen.

[TODO: Sequenzdiagramm (Bild 4.2): Password-Grant-Ablauf inkl. Lockout-Prüfung, Scope-Validierung,
Tokenausstellung, selbst erstellen.]

---

## 4.3 Autorisierung / Berechtigungskaskade

### 4.3.1 Portierung der PHP-Logik

Das alte Backend entscheidet über `PermissionCalculationService::getPermission($functionName, $userId)` nach
einer vierstufigen Prioritätsliste (siehe `ARCHITECTURE.md` des PHP-Repos, Abschnitt 8): direkte
Nutzer-Endpunkt-Regel, Nutzergruppen-Endpunkt-Regel, Nutzer-Endpunktgruppen-Regel,
Nutzergruppen-Endpunktgruppen-Regel, mit Rückfall auf `DENY`. Diese exakte Semantik musste erhalten bleiben,
da die Python-DaaS-Anwendung ihre Berechtigungsprüfungen unverändert gegen das neue Backend stellt (NFA-2/NFA-3). Die
neue `PermissionService.checkPermission`-Methode bildet die vier Stufen 1:1 nach:

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
keine Stufe ein Ergebnis, greift der Default `deny` (fail-closed, NFA-1).

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
    ...
    return Optional.of(allow);
}
```

Diese "explizite Verweigerung gewinnt"-Regel ist eine bewusste, zusätzlich zum ursprünglichen Verhalten
dokumentierte Entscheidung (siehe Javadoc der Klasse) und verhindert, dass eine einzelne großzügige
Gruppenregel eine andernorts bewusst gesetzte Sperre aushebelt – ein sicherheitsrelevantes Detail, das in der
schlecht dokumentierten PHP-Implementierung laut Ausschreibung nicht nachvollziehbar war und daher im Rahmen
von AP2 eigens analysiert und hier explizit spezifiziert wurde.

### 4.3.3 Fail-closed bei fehlerhafter Eingabe

Unbekannte Nutzer-IDs, `null`-Werte oder leere Funktionsnamen werden **vor** jeder Datenbankabfrage abgefangen
und führen direkt zu `deny`:

```java
if(userId ==null||functionName ==null||functionName.

isBlank()){
        return PermissionResultDto.

deny(userId !=null?userId:UNRESOLVED_USER_ID, UNKNOWN_USER_NAME);
}
```

Dies verhindert zugleich unnötige Datenbanklast bei offensichtlich ungültigen Anfragen und schließt die Lücke,
dass ein Programmfehler beim Aufrufer (z. B. vergessene Nutzer-ID) niemals versehentlich zu einem impliziten
`allow` führen kann.

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

[TODO: Tabelle/Bild 4.3: Gegenüberstellung PHP- vs. Java-Kaskade (gleiche 4 Stufen, gleiche Priorität) als
eigene Grafik/Tabelle zur Veranschaulichung der Verhaltenskompatibilität.]

---

## 4.4 Benutzerverwaltung

### 4.4.1 Registrierung mit E-Mail-Verifikation

`UserService.createUser` legt einen zunächst **deaktivierten** Nutzer an, generiert einen 8-stelligen
hexadezimalen Registrierungscode mit 24 Stunden Gültigkeit und versendet ihn per E-Mail:

```java
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
Konto, das gar nicht existiert).

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
```

`resendRegistrationCode` ist absichtlich **wortkarg**: Für unbekannte, bereits verifizierte oder
Admin-Konten passiert nach außen sichtbar nichts (kein Fehler, keine Bestätigung) — dies verhindert, dass der
Endpunkt zum Enumerieren registrierter E-Mail-Adressen missbraucht werden kann, ähnlich der Timing-Maßnahme in
4.2.2.

### 4.4.2 Admin-initiierte Verwaltung

`AdminController`/`AdminService` erlauben das Anlegen weiterer Admin-Konten (nur mit `SCOPE_admin`),
`UserController`/`UserService` das Auflisten, Teil-Update (`PATCH`), Aktivieren und Deaktivieren regulärer
Nutzer. Ändert sich das Passwort eines Nutzers oder wird sein Konto deaktiviert, veröffentlicht der Service
ein `UserSessionsInvalidatedEvent`:

```java
if(request.getPassword() !=null){
        ...
        eventPublisher.

publishEvent(new UserSessionsInvalidatedEvent(id));
        }
```

Dieses Event entkoppelt die Nutzerverwaltung von der Token-Widerrufslogik: `UserService` muss nicht wissen, *wie*
Sessions invalidiert werden (das ist Aufgabe eines Listeners im `auth`-Modul) — ein Beispiel für die in
4.1 beschriebene strikte Schichtentrennung, die über reine Methodenaufrufe hinaus auch auf Ereignisebene
eingehalten wird.

### 4.4.3 User-Groups als Bindeglied zum Berechtigungs- und Desktop-Modul

`UserGroup` ist das Bindeglied zwischen Benutzerverwaltung, Berechtigungskaskade (Stufen 2 und 4, Abschnitt
4.3) und Desktop-Inventar (Abschnitt 4.5): Eine Gruppe kann sowohl Berechtigungsregeln als auch
Desktop-Group-Zuordnungen tragen. `UserGroupService` stellt CRUD-Operationen für Gruppen bereit; die
eigentliche Verknüpfungslogik zu Desktop-Groups liegt bewusst im `desktop`-Modul (siehe 4.5), um zyklische
Abhängigkeiten zwischen `user` und `desktop` zu vermeiden (durchgesetzt durch dieselbe ArchUnit-Regel aus
4.1.3, die Abhängigkeitsrichtungen zwischen Paketen prüft).

[TODO: Bild 4.4: Entity-Beziehungsdiagramm User/Admin/UserGroup/Desktop/DesktopGroup, eigene Darstellung (nicht die Textgrafik aus ARCHITECTURE.md des PHP-Repos übernehmen).]

---

## 4.5 Desktop-Inventar

### 4.5.1 Reines Inventar, keine Bereitstellung

`DesktopService` verwaltet ausschließlich Metadaten zu virtuellen Desktops und Desktop-Groups; die tatsächliche
Bereitstellung von Containern/VMs erfolgt – wie in Kapitel 2.1 und 3.1 dargestellt – durch die
DaaS-Anwendung, nicht durch dieses Backend. `createDesktop` erlaubt optional das kaskadierte Anlegen von
Untergruppen in einem Aufruf:

```java

@Transactional
public void createDesktop(DesktopCreateRequest request) {
    Set<String> subGroupDescriptions = new HashSet<>();
    if (request.getGroups() != null) {
        for (var sgReq : request.getGroups()) {
            String description = sgReq.getDescription();
            if (!subGroupDescriptions.add(description) || desktopGroupRepository.existsByDescription(description)) {
                throw new DesktopGroupAlreadyExistsException(description);
            }
        }
    }
    ...
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
if(ug.getDesktopGroups().

contains(dg)){
        throw new

UserGroupAlreadyAssociatedException(desktopGroupId, userGroupId);
}

linkUserGroupAndDesktopGroup(ug, dg);
```

Die Verknüpfung wird **beidseitig** gepflegt (`userGroup.getDesktopGroups().add(...)` und
`desktopGroup.getUserGroups().add(...)`), da beide Entitäten eine n:m-Beziehung über JPA abbilden und die
In-Memory-Konsistenz der bidirektionalen Assoziation sonst von der jeweiligen Abfragerichtung abhinge.

[TODO: Bild 4.5: n:m-Beziehungen Desktop ↔ DesktopGroup ↔ UserGroup als eigenes Diagramm.]

---

## 4.6 Querschnittsthemen

### 4.6.1 Sicherheitsfilterkette

`SecurityConfig` konfiguriert eine zustandslose Filterkette: CSRF ist deaktiviert (keine Cookies/Sessions),
`SessionCreationPolicy.STATELESS` ist gesetzt, und bis auf eine explizite Positivliste öffentlicher Endpunkte
erfordert jede Anfrage ein gültiges JWT:

```java
auth.requestMatchers(HttpMethod.POST, "/user").

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
...
        auth.

anyRequest().

authenticated();
```

Diese "deny-by-default, erlaubt nur explizit gelistete Endpunkte"-Struktur ist die Anwendung desselben
fail-closed-Prinzips wie in der Berechtigungskaskade (4.3), diesmal auf Ebene der HTTP-Schicht statt der
fachlichen Autorisierung.

### 4.6.2 Fehlerbehandlung

`GlobalExceptionHandler` sowie domänenspezifische Handler (`TokenExceptionHandler`,
`SessionExceptionHandler`) übersetzen typisierte Exceptions (`InvalidCredentialsException`,
`AccountLockedException`, `UserNotFoundException`, `WeakPasswordException` u. a.) in ein einheitliches
`ErrorResponse`-JSON-Format mit passendem HTTP-Statuscode. Dies ersetzt die im alten PHP-Backend über
Middleware (`InvalidUserInputHandlerMiddleware`) realisierte, laut Ausschreibung schlecht dokumentierte
Fehlerbehandlung durch eine zentrale, typsichere Lösung.

### 4.6.3 CORS, Protokollierung und PII-Maskierung

`CorsConfig` erlaubt standardmäßig nur den in `app.frontend.url` konfigurierten Origin. Da JWTs
personenbezogene Daten (E-Mail, Name) als Claims enthalten, maskiert `LogMasking.maskEmail` E-Mail-Adressen
in allen Log-Zeilen, die Nutzeraktionen protokollieren (siehe z. B. Abschnitt 4.2.2); dies betrifft sämtliche
`service`-Klassen, die mit Nutzerdaten arbeiten.

### 4.6.4 Geplante Aufräumarbeiten

`TokenCleanupService` entfernt abgelaufene/widerrufene Zeilen aus `access_tokens`/`refresh_tokens` zeitgesteuert
über Spring Scheduling, um unbegrenztes Tabellenwachstum zu vermeiden – ein Aspekt, der im alten Backend über
den separaten Yacron-Cron-Dienst gelöst war und hier in den Anwendungsprozess selbst integriert wurde (ein
Betriebsartefakt weniger).

---

## 4.7 Demonstrator (AP5)

[TODO: Diesen Abschnitt nach Fertigstellung von AP5 ausfüllen. Vorschlag für Inhalt:]

- Swagger-UI-gestützte Live-Demonstration der Kernabläufe aus Kapitel 3.5 (Login → Berechtigungsprüfung,
  Registrierung → Verifikation, Desktop-/Gruppenanlage).
- Screenshots der Swagger-UI (`/swagger-ui.html`) mit Beispielanfragen/-antworten.
- Kurzbeschreibung, wie der Demonstrator gegen eine lokale MariaDB-/Mailhog-Umgebung betrieben wird (`docker-compose`,
  `../../.env.example`).

## 4.8 Tests & Evaluation (AP6)

### 4.8.1 Automatisierte Qualitätssicherung im Build

Der Build (`mvn verify`) erzwingt zwei automatisierte Qualitätsschranken: die ArchUnit-Regeln aus 4.1.3 und
eine Mindest-Testabdeckung von 70 % der Codezeilen über JaCoCo:

```xml

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
```

Unterschreitet die Testabdeckung diesen Schwellenwert, schlägt der Build fehl — Testabdeckung ist damit kein
optionales Qualitätsmerkmal, sondern eine harte Build-Voraussetzung (NFA-9).

### 4.8.2 Testarten

[TODO: tatsächliche Testsuite auswerten (Anzahl Unit-/Integrationstests je Modul, z. B. via
`mvn test -Dtest=... ` oder Testverzeichnis-Auswertung) und hier mit konkreten Zahlen ergänzen, z. B.:]

- Unit-Tests je Service (`TokenServiceTest`, `PermissionServiceTest`, `UserServiceTest`, ...)
- Architektur-Tests (`ArchitectureTest`, siehe 4.1.3)
- [TODO: Integrationstests mit H2/Testcontainers? Prüfen und ergänzen.]

### 4.8.3 Vergleich mit dem alten Backend

[TODO: Dieser Abschnitt sollte eine strukturierte Gegenüberstellung liefern, z. B. eine Tabelle:]

| Kriterium                       | Altes Backend (PHP)                                | Neues Backend (Java)                                                       |
|---------------------------------|----------------------------------------------------|----------------------------------------------------------------------------|
| Schichtenarchitektur erzwungen? | Nein (nur Konvention)                              | Ja, ArchUnit im Build                                                      |
| Testabdeckung erzwungen?        | [TODO: prüfen, ob PHPUnit-Coverage-Gate existiert] | Ja, ≥70 % (JaCoCo)                                                         |
| Berechtigungskaskade            | 4-stufig, `PermissionCalculationService`           | 4-stufig, identische Priorität, zusätzlich dokumentierte Konfliktauflösung |
| Schema-Verwaltung               | Doctrine Migrations                                | Flyway, Hibernate nur `validate`                                           |
| Auth-Mechanismus                | `league/oauth2-server`, Password Grant             | Eigener RS256-JWT-Aussteller + Spring Resource Server                      |

[TODO: Werte für PHP-Testabdeckung (falls vorhanden, z. B. via `phpunit.xml`/CI-Reports) recherchieren,
um diese Tabelle mit echten Zahlen zu füllen statt Platzhaltern.]

---

## Offene Punkte zu diesem Kapitel

- [ ] 5 Abbildungen/Diagramme selbst erstellen (4.1 Paketdiagramm, 4.2 Sequenzdiagramm Login,
  4.3 PHP-vs-Java-Kaskaden-Vergleich, 4.4 ER-Diagramm, 4.5 n:m-Beziehungen) — keine Fremdgrafiken.
- [ ] 4.7 Demonstrator ausformulieren, sobald AP5 abgeschlossen ist (Screenshots, Ablaufbeschreibung).
- [ ] 4.8 mit echten Testzahlen (Testanzahl, tatsächliche Zeilenabdeckung laut JaCoCo-Report) befüllen,
  aktuell nur die Build-Schwelle (70 %) bekannt, nicht der tatsächliche Ist-Wert.
- [ ] Prüfen, ob das PHP-Backend ein vergleichbares Coverage-/Qualitäts-Gate besitzt, um Tabelle in 4.8.3
  fair und belegt auszufüllen.
- [ ] Lockout-Sonderfall (4.2.2, Dauersperre nach Ablauf+erneutem Fehlversuch) ggf. als Erkenntnis für
  Kapitel 5 (Ausblick/kritische Reflexion) vormerken.
