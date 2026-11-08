# Anhang

Tabelle A.1 listet alle Endpunkte des neuen Backends in Version 2.0.0 auf, wie sie sich aus den Controller-Klassen ergeben. „admin“ bedeutet, dass ein Token mit dem Scope admin erforderlich ist; „angemeldet“ bedeutet, dass ein beliebiges gültiges Token genügt.

*Tabelle A.1: Endpunkte des neuen Backends (Version 2.0.0)*

| **Endpunkt** | **Zugriff** | **Zweck** |
|---|---|---|
| POST /oauth2/user/token | öffentlich | Token per Password oder Refresh Grant |
| POST /oauth2/user/logout | angemeldet | Widerruf des aktuellen Tokens |
| GET /oauth2/user/session | angemeldet | Session-Informationen (ID, Name, Rolle) |
| POST /user | öffentlich | Selbstregistrierung |
| POST /user/validate_email | öffentlich | E-Mail-Verifikation |
| POST /user/resend_code | öffentlich | neuen Registrierungscode anfordern |
| GET /users | admin | Nutzer auflisten |
| PATCH /user/{userId} | admin | Nutzerdaten ändern |
| PATCH /user/{userId}/enable | admin | Konto aktivieren |
| PATCH /user/{userId}/disable | admin | Konto deaktivieren |
| POST /user/request-application/{application}/{isoCode} | angemeldet | Anwendungsfreischaltung anfragen |
| GET /admins | admin | Administratoren auflisten |
| POST /admin | admin | Administrator anlegen |
| GET /user_groups | admin | Nutzergruppen auflisten |
| POST /user_group | admin | Nutzergruppe anlegen |
| PATCH /user_group/{id} | admin | Nutzergruppe ändern |
| GET /desktops | admin | Desktops auflisten |
| GET /desktop/{id} | admin | Desktop lesen |
| POST /desktop | admin | Desktop anlegen (optional mit Untergruppen) |
| GET /desktop_groups | admin | Desktop-Gruppen auflisten |
| POST /desktop_group | admin | Desktop-Gruppe anlegen |
| POST /desktop_group/{id}/user_group/{userGroupId} | admin | Nutzergruppe mit Desktop-Gruppe verknüpfen |
| GET /permissions/{functionName}/{userId} | admin | Berechtigung eines Nutzers prüfen |
| POST /permissions_info | angemeldet | Berechtigung des aufrufenden Nutzers prüfen |
| GET /actuator/health | öffentlich | Health-Check |
