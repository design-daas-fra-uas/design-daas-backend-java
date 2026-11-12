# Anhang

Tabelle A.1 listet alle Endpunkte des neuen Backends in Version 2.0.0 (Stand:
09.11.2026) auf, wie sie sich aus den Controller-Klassen ergeben. „admin“
bedeutet, dass ein Token mit dem Scope admin erforderlich ist; „angemeldet“
bedeutet, dass ein beliebiges gültiges Token genügt.

*Tabelle A.1: Endpunkte des neuen Backends (Version 2.0.0, Stand: 09.11.2026)*

| **Endpunkt** | **Zugriff** | **Zweck** |
|---|---|---|
| POST /oauth2/user/token | öffentlich | Token per Password oder Refresh Grant |
| POST /oauth2/user/logout | angemeldet | Widerruf des aktuellen Tokens |
| GET /oauth2/user/session | angemeldet | Session-Informationen (ID, Name, Rolle) |
| POST /user | öffentlich | Selbstregistrierung |
| POST /user/validate_email | öffentlich | E-Mail-Verifikation |
| POST /user/resend_code | öffentlich | neuen Registrierungscode anfordern |
| GET /users | admin | Nutzer auflisten |
| GET /user/{userId} | admin | einzelnen Nutzer lesen |
| PATCH /user/{userId} | admin | Nutzerdaten ändern |
| PATCH /user/{userId}/enable | admin | Konto aktivieren |
| PATCH /user/{userId}/disable | admin | Konto deaktivieren |
| DELETE /user/{userId} | admin | Nutzer löschen (mit Benachrichtigung per E-Mail) |
| POST /user/request-application/{application}/{isoCode} | angemeldet | Anwendungsfreischaltung anfragen |
| GET /admins | admin | Administratoren auflisten |
| GET /admin/{adminId} | admin | einzelnen Administrator lesen |
| POST /admin | admin | Administrator anlegen |
| DELETE /admin/{adminId} | admin | Administrator löschen (nicht das eigene und nicht das letzte Konto) |
| GET /user_groups | admin | Nutzergruppen auflisten |
| POST /user_group | admin | Nutzergruppe anlegen |
| PATCH /user_group/{id} | admin | Nutzergruppe ändern |
| DELETE /user_group/{id} | admin | Nutzergruppe löschen |
| GET /desktops | admin | Desktops auflisten |
| GET /desktop/{id} | admin | Desktop lesen |
| POST /desktop | admin | Desktop anlegen (optional mit Untergruppen) |
| DELETE /desktop/{id} | admin | Desktop löschen |
| GET /desktop_groups | admin | Desktop-Gruppen auflisten |
| POST /desktop_group | admin | Desktop-Gruppe anlegen |
| POST /desktop_group/{id}/user_group/{userGroupId} | admin | Nutzergruppe mit Desktop-Gruppe verknüpfen |
| DELETE /desktop_group/{id} | admin | Desktop-Gruppe löschen |
| GET /permissions/{functionName}/{userId} | admin | Berechtigung eines Nutzers prüfen |
| POST /permissions_info | angemeldet | Berechtigung des aufrufenden Nutzers prüfen |
| GET /endpoints | admin | Endpunkte (Funktionen der DaaS-Anwendung) auflisten |
| POST /endpoint | admin | Endpunkt anlegen |
| DELETE /endpoint/{id} | admin | Endpunkt samt zugehöriger Regeln löschen |
| GET /endpoint_groups | admin | Endpunktgruppen auflisten |
| POST /endpoint_group | admin | Endpunktgruppe anlegen |
| PATCH /endpoint_group/{id} | admin | Endpunktgruppe ändern |
| DELETE /endpoint_group/{id} | admin | Endpunktgruppe samt zugehöriger Regeln löschen |
| GET /permission_rules | admin | Berechtigungsregeln aller vier Arten auflisten |
| POST /permission_rule | admin | Regel anlegen (allow oder deny) |
| DELETE /permission_rule/{kind}/{id} | admin | Regel löschen |
| GET /actuator/health | öffentlich | Health-Check |
