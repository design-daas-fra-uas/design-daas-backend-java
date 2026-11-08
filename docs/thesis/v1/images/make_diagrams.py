"""Generate thesis diagrams as SVG in the style of the existing figures."""
import sys
from xml.sax.saxutils import escape

OUT = sys.argv[1]
FONT = 'DejaVu Sans'
INK, LINE, SUB = '#0f172a', '#334155', '#334155'
C = {  # stroke, fill
    'blue': ('#2563eb', '#dbeafe'), 'green': ('#16a34a', '#dcfce7'), 'orange': ('#d97706', '#fef3c7'),
    'purple': ('#7c3aed', '#ede9fe'), 'pink': ('#db2777', '#fce7f3'), 'red': ('#dc2626', '#fee2e2'),
    'teal': ('#0d9488', '#ccfbf1'), 'gray': ('#64748b', '#f1f5f9'), 'slate': ('#94a3b8', '#f8fafc'),
}


class Svg:
    def __init__(self, w, h):
        self.w, self.h, self.parts = w, h, []

    def add(self, s):
        self.parts.append(s)

    def text(self, x, y, s, size=15, bold=False, italic=False, color=INK, anchor='middle'):
        self.add('<text x="%g" y="%g" font-family="%s" font-size="%g"%s%s fill="%s" text-anchor="%s">%s</text>' % (
            x, y, FONT, size, ' font-weight="bold"' if bold else '', ' font-style="italic"' if italic else '',
            color, anchor, escape(s)))

    def box(self, x, y, w, h, kind, title, sub=None, dashed=False, title_size=16):
        stroke, fill = C[kind]
        self.add('<rect x="%g" y="%g" width="%g" height="%g" rx="10" fill="%s" stroke="%s" stroke-width="2.5"%s/>' % (
            x, y, w, h, fill, stroke, ' stroke-dasharray="10 6"' if dashed else ''))
        subs = sub if isinstance(sub, list) else ([sub] if sub else [])
        total = title_size + len(subs) * 20
        ty = y + h / 2 - total / 2 + title_size - 2
        self.text(x + w / 2, ty, title, title_size, bold=True)
        for k, s in enumerate(subs):
            self.text(x + w / 2, ty + 22 * (k + 1), s, 13.5, color=SUB)

    def cylinder(self, x, y, w, h, kind, title, sub=None):
        stroke, fill = C[kind]
        ry = 14
        self.add('<path d="M%g,%g v%g a%g,%g 0 0 0 %g,0 v%g" fill="%s" stroke="%s" stroke-width="2.5"/>' % (
            x, y + ry, h - 2 * ry, w / 2, ry, w, -(h - 2 * ry), fill, stroke))
        self.add('<ellipse cx="%g" cy="%g" rx="%g" ry="%g" fill="%s" stroke="%s" stroke-width="2.5"/>' % (
            x + w / 2, y + ry, w / 2, ry, fill, stroke))
        self.text(x + w / 2, y + h / 2 + 8, title, 16, bold=True)
        if sub:
            self.text(x + w / 2, y + h / 2 + 30, sub, 13.5, color=SUB)

    def arrow(self, pts, color=LINE, dashed=False, label=None, lx=None, ly=None, lcolor=None, anchor='start', head=True):
        d = 'M' + ' L'.join('%g,%g' % p for p in pts)
        mid = {'#334155': 'a', '#dc2626': 'r', '#16a34a': 'g'}.get(color, 'a')
        self.add('<path d="%s" fill="none" stroke="%s" stroke-width="2.5"%s%s/>' % (
            d, color, ' stroke-dasharray="9 6"' if dashed else '', ' marker-end="url(#%s)"' % mid if head else ''))
        if label:
            self.text(lx, ly, label, 14, italic=lcolor is None, bold=lcolor is not None,
                      color=lcolor or SUB, anchor=anchor)

    def save(self, name, scale=2):
        defs = ''.join(
            '<marker id="%s" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
            '<path d="M0,0 L10,5 L0,10 z" fill="%s"/></marker>' % (k, c)
            for k, c in (('a', LINE), ('r', '#dc2626'), ('g', '#16a34a')))
        svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
               '<defs>%s</defs><rect width="%d" height="%d" fill="#ffffff"/>%s</svg>') % (
            self.w * scale, self.h * scale, self.w, self.h, defs, self.w, self.h, ''.join(self.parts))
        open('%s/%s.svg' % (OUT, name), 'w', encoding='utf-8').write(svg)


# ---------------------------------------------------------------- Abb. 3.3 (redraw) target state
s = Svg(1400, 780)
tops = [(120, 'blue', 'Frontend', '(React, Anbindung angepasst)'),
        (517, 'purple', 'DaaS-Anwendung', '(Python, Anbindung angepasst)'),
        (914, 'orange', 'Admin', '(Swagger UI, SCOPE_admin)')]
for x, k, t, sub in tops:
    s.box(x, 72, 352, 74, k, t, sub)
s.arrow([(296, 146), (296, 266)], label='REST, gleiche Endpunkte und Feldformate', lx=310, ly=210)
s.arrow([(693, 146), (693, 266)], label='REST, gleicher Permissions-Vertrag', lx=707, ly=210)
s.arrow([(1090, 146), (1090, 266)])
s.box(85, 268, 1215, 254, 'slate', '', dashed=True)
s.text(116, 303, 'Neues Backend: Java 21 / Spring Boot', 18, bold=True, anchor='start')
for x, k, t in ((142, 'purple', 'auth'), (433, 'orange', 'permission'), (724, 'blue', 'user'), (1015, 'pink', 'desktop')):
    s.box(x, 333, 229, 62, k, t)
s.box(142, 421, 1102, 70, 'gray', 'Querschnitt: shared, config, bootstrap', 'Schichtung je Modul durch ArchUnit geprüft', dashed=True)
s.arrow([(428, 522), (428, 606)])
s.arrow([(957, 522), (957, 614)])
s.cylinder(287, 608, 282, 100, 'teal', 'MariaDB', 'Schema über Flyway migriert')
s.box(755, 617, 405, 84, 'green', 'SMTP-Relay', 'Registrierungs- und Benachrichtigungs-E-Mails')
s.save('abb-3-3')

# ---------------------------------------------------------------- Abb. 4.1 password grant checks
s = Svg(1400, 1130)
cx, bw, bh = 400, 480, 66
L = cx - bw / 2
steps = [
    ('blue', 'POST /oauth2/user/token', 'grant_type=password, username, password, scope'),
    ('orange', 'Benutzername und Passwort angegeben?', None),
    ('orange', 'Nutzer mit dieser E-Mail vorhanden?', None),
    ('orange', 'Konto gesperrt?', 'AccountLockoutService.assertNotLocked'),
    ('orange', 'Passwort korrekt?', 'BCrypt-Vergleich mit gespeichertem Hash'),
    ('orange', 'Konto aktiviert?', None),
    ('green', 'Fehlversuchszähler zurücksetzen', 'AccountLockoutService.resetOnSuccess'),
    ('purple', 'Scope gegen Rolle prüfen', 'ScopeResolver: Nutzer erhalten nie den Scope admin'),
    ('green', 'Tokens ausstellen', 'RS256-JWT (mit jti) und opaker Refresh-Token'),
    ('green', '200 OK', 'access_token, refresh_token, token_type, expires_in, scope'),
]
ys = [30 + 110 * i for i in range(len(steps))]
for (k, t, sub), y in zip(steps, ys):
    s.box(L, y, bw, bh, k, t, sub)
ok_label = {1: 'ja', 2: 'ja', 3: 'nein', 4: 'ja', 5: 'ja'}
for i in range(len(steps) - 1):
    s.arrow([(cx, ys[i] + bh), (cx, ys[i + 1] - 2)],
            label=ok_label.get(i), lx=cx + 10, ly=ys[i] + bh + 27, lcolor='#16a34a' if i in ok_label else None)
fails = {
    1: ('400 invalid_request', 'Parameter fehlen'),
    2: ('401 invalid_grant', ['vorher BCrypt-Vergleich gegen Dummy-Hash,', 'damit die Antwortzeit nichts verrät']),
    3: ('429 account_locked', 'Sperre ist noch aktiv'),
    4: ('401 invalid_grant', ['Fehlversuch wird gezählt;', 'ab 5 Fehlversuchen 15 Minuten Sperre']),
    5: ('401 invalid_grant', 'Konto ist deaktiviert'),
}
for i, (t, sub) in fails.items():
    y = ys[i]
    h = bh if not isinstance(sub, list) else 80
    s.box(790, y + bh / 2 - h / 2, 560, h, 'red', t, sub)
    s.arrow([(L + bw, y + bh / 2), (788, y + bh / 2)], color='#dc2626',
            label='ja' if i == 3 else 'nein', lx=L + bw + 14, ly=y + bh / 2 - 9, lcolor='#dc2626')
s.save('abb-4-1')

# ---------------------------------------------------------------- Abb. 4.2 token lifecycle
s = Svg(1400, 1000)
lanes = [(210, 'blue', 'Client', 'Frontend oder DaaS-Anwendung'),
         (700, 'orange', 'Backend', 'auth-Modul und Resource Server'),
         (1190, 'teal', 'Datenbank', 'access_tokens, refresh_tokens')]
phases = [(120, 'Anmeldung'), (300, 'Geschützte Anfrage'), (520, 'Erneuerung'), (700, 'Widerruf'), (840, 'Aufräumen')]
ends = [p[0] for p in phases[1:]] + [960]
for k, ((y, name), e) in enumerate(zip(phases, ends)):
    s.add('<rect x="20" y="%g" width="1360" height="%g" rx="8" fill="%s"/>' % (y, e - y - 8, '#f8fafc' if k % 2 == 0 else '#f1f5f9'))
    s.text(32, y + 24, '%d  %s' % (k + 1, name), 14, bold=True, color='#475569', anchor='start')
for x, k, t, sub in lanes:
    s.box(x - 160, 24, 320, 70, k, t, sub)
    s.add('<line x1="%g" y1="94" x2="%g" y2="955" stroke="#94a3b8" stroke-width="2" stroke-dasharray="6 6"/>' % (x, x))
X = {l[2]: l[0] for l in lanes}


def msg(a, b, y, label, dashed=False):
    xa, xb = X[a], X[b]
    sgn = 1 if xb > xa else -1
    s.arrow([(xa, y), (xb - sgn * 4, y)], dashed=dashed)
    s.text((xa + xb) / 2, y - 9, label, 14)


def note(lane, y, label):
    x = X[lane]
    s.box(x - 175, y, 350, 36, 'gray', label, title_size=13.5)


msg('Client', 'Backend', 165, 'POST /oauth2/user/token (Password Grant)')
msg('Backend', 'Datenbank', 205, 'jti und Refresh-Token speichern')
msg('Backend', 'Client', 250, 'Zugriffstoken (1 h) + Refresh-Token (30 Tage)', dashed=True)
msg('Client', 'Backend', 345, 'API-Aufruf mit Authorization: Bearer …')
note('Backend', 362, 'Signatur, Ablaufzeit und Aussteller prüfen')
msg('Backend', 'Datenbank', 430, 'jti widerrufen oder unbekannt?')
msg('Datenbank', 'Backend', 465, 'nein', dashed=True)
msg('Backend', 'Client', 500, 'Antwort (sonst 401 Unauthorized)', dashed=True)
msg('Client', 'Backend', 565, 'POST /oauth2/user/token (Refresh Grant)')
msg('Backend', 'Datenbank', 610, 'alten Refresh-Token widerrufen, neue Tokens speichern')
msg('Backend', 'Client', 655, 'neues Token-Paar', dashed=True)
msg('Client', 'Backend', 745, 'POST /oauth2/user/logout')
msg('Backend', 'Datenbank', 790, 'revoked = true (auch bei Passwortänderung oder Deaktivierung)')
note('Backend', 852, 'TokenCleanupService, täglich 03:00 Uhr')
msg('Backend', 'Datenbank', 935, 'abgelaufene und widerrufene Zeilen löschen')
s.save('abb-4-2')

# ---------------------------------------------------------------- Abb. 4.3 deployment view
s = Svg(1400, 800)
s.box(120, 30, 400, 80, 'blue', 'Browser mit Frontend', 'React-Anwendung')
s.box(880, 30, 400, 80, 'purple', 'DaaS-Anwendung', 'Python, prüft Berechtigungen')
s.box(260, 185, 880, 80, 'gray', 'Reverse Proxy / Load Balancer',
      'TLS-Terminierung, X-Forwarded-For, Health-Check über GET /actuator/health')
s.arrow([(320, 110), (320, 183)], label='HTTPS', lx=332, ly=154)
s.arrow([(1080, 110), (1080, 183)], label='HTTPS', lx=1092, ly=154)
s.box(110, 330, 1180, 260, 'slate', '', dashed=True)
s.text(135, 362, 'Docker-Host', 17, bold=True, anchor='start')
s.add('<rect x="186" y="398" width="520" height="160" rx="10" fill="#fef3c7" stroke="#d97706" stroke-width="2" opacity="0.6"/>')
s.box(170, 384, 520, 160, 'orange', 'design-daas-backend (Container)',
      ['Spring Boot, Port 8080, Profil prod', 'Konfiguration über Umgebungsvariablen',
       'eine oder mehrere Instanzen'])
s.arrow([(430, 265), (430, 382)], label='HTTP :8080', lx=442, ly=304)
s.box(800, 384, 440, 92, 'pink', 'Volume /app/keys', 'rsa-jwk.json (Signaturschlüssel)')
s.arrow([(690, 430), (798, 430)], dashed=True, head=False)
s.text(1020, 506, 'muss Neustarts überdauern und', 13.5, color=SUB)
s.text(1020, 526, 'für alle Instanzen identisch sein', 13.5, color=SUB)
s.arrow([(330, 544), (330, 652)], label='JDBC', lx=342, ly=622)
s.arrow([(560, 544), (560, 600), (900, 600), (900, 662)], label='SMTP', lx=912, ly=640)
s.cylinder(170, 655, 340, 110, 'teal', 'MariaDB', 'Schema über Flyway')
s.box(740, 665, 400, 90, 'green', 'SMTP-Relay', 'nur bei MAIL_ENABLED=true')
s.save('abb-4-3')
print('ok')
