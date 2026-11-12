"""Generate the own chapter-4 diagrams as SVG (data model, design-daas integration).

Usage: python make_diagrams_ch4.py <out-dir>
Render the SVGs to PNG at 2x scale (e.g. with @resvg/resvg-js).
Figures 4.2-4.4 (password grant, token lifecycle, deployment) are in make_diagrams.py.
"""
from diagram_lib import Svg, SUB, LINE, C

# ------------------------------------------------ Abb. 4.1: data model of the new backend
# Verified against src/main/resources/db/migration/V1-V5 and the JPA entities of design-daas-backend-java
s = Svg(1500, 1010)
NOTE = '#b45309'  # differences to the PHP model


def note(x, y, text, anchor='start'):
    s.text(x, y, text, 12.5, italic=True, color=NOTE, anchor=anchor)


s.entity(40, 20, 150, 'blue', 'Admin', [])
s.entity(230, 20, 150, 'blue', 'User', [])
bu = s.entity(40, 130, 340, 'blue', 'BaseUser', [
    'id (PK)', 'role (Diskriminator: admin | user)', 'name', 'email (eindeutig)', 'password (BCrypt-Hash)',
    'enabled', 'registration_code, …_timeout', 'registration_used_moment', 'failed_login_attempts',
    'locked_until'], stereo='«abstract» users')
for cx in (115, 305):
    s.add('<path d="M%g,60 L%g,108" stroke="%s" stroke-width="2.2"/>' % (cx, cx, LINE))
    s.add('<path d="M%g,130 L%g,108 L%g,108 z" fill="#ffffff" stroke="%s" stroke-width="2.2"/>'
          % (cx, cx - 10, cx + 10, LINE))
note(395, 70, 'ohne guid (von keinem Client genutzt)')
note(395, 88, 'neu: Kontosperre (Abschnitt 4.2.2)')

ug = s.entity(40, 480, 340, 'purple', 'UserGroup', ['id (PK)', 'name (eindeutig)', 'description'])
dg = s.entity(40, 680, 340, 'pink', 'DesktopGroup', ['id (PK)', 'description (eindeutig)'])
dk = s.entity(40, 860, 340, 'pink', 'Desktop', ['id (PK)', 'description'])
s.rel([(210, bu[1] + bu[3]), (210, 480)], 'zero_many', 'zero_many', 'n:m', 225, 455, 'start')
s.rel([(210, ug[1] + ug[3]), (210, 680)], 'zero_many', 'zero_many', 'n:m', 225, 640, 'start')
s.rel([(210, dg[1] + dg[3]), (210, 860)], 'zero_many', 'zero_many', 'n:m', 225, 830, 'start')

acc = []
for k, (name, y) in enumerate([('EndpointUserAccess', 130), ('EndpointUserGroupAccess', 285),
                               ('EndpointGroupUserAccess', 440), ('EndpointGroupUserGroupAccess', 595)]):
    acc.append(s.entity(560, y, 330, 'orange', name, ['id (PK)', 'allow_access (boolean)'],
                        stereo='Stufe %d der Kaskade' % (k + 1)))
note(725, 735, 'je Paar höchstens eine Regel (Migration V5)', 'middle')

ep = s.entity(1110, 150, 330, 'green', 'Endpoint', ['id (PK)', 'function_name (eindeutig)', 'description'])
eg = s.entity(1110, 520, 330, 'green', 'EndpointGroup', ['id (PK)', 'name (eindeutig)', 'description'])
s.rel([(1275, ep[1] + ep[3]), (1275, 520)], 'zero_many', 'zero_many')
s.text(1262, 372, 'n:m (endpoint_group_members)', 13, italic=True, color=SUB, anchor='end')
note(1262, 392, 'PHP: höchstens eine Gruppe je Endpunkt', 'end')
note(1262, 410, 'PHP: zusätzlich URL und HTTP-Methode', 'end')

# subject side
s.rel([(380, 180), (560, 180)], 'one', 'zero_many')
s.rel([(380, 380), (480, 380), (480, 495), (560, 495)], 'one', 'zero_many')
s.rel([(380, 520), (450, 520), (450, 340), (560, 340)], 'one', 'zero_many')
s.add('<rect x="442" y="373" width="16" height="14" fill="#ffffff"/>')  # hop: BU->S3 passes UG->S2
s.add('<path d="M442,380 A8,8 0 0 1 458,380" fill="none" stroke="%s" stroke-width="2.2"/>' % LINE)
s.rel([(380, 570), (420, 570), (420, 650), (560, 650)], 'one', 'zero_many')
# target side
s.rel([(1110, 183), (890, 183)], 'one', 'zero_many')
s.rel([(1110, 235), (1000, 235), (1000, 340), (890, 340)], 'one', 'zero_many')
s.rel([(1110, 560), (1000, 560), (1000, 495), (890, 495)], 'one', 'zero_many')
s.rel([(1110, 600), (1030, 600), (1030, 650), (890, 650)], 'one', 'zero_many')

# token tables (new): linked to users by user_id with ON DELETE CASCADE
at = s.entity(560, 790, 400, 'teal', 'AccessToken', [
    'id (PK)', 'jti (eindeutig)', 'user_id (FK auf users, ON DELETE CASCADE)', 'scope, issued_at, expires_at',
    'revoked, revoked_at'], stereo='neu: access_tokens')
rt = s.entity(1000, 790, 440, 'teal', 'RefreshToken', [
    'id (PK)', 'token_value (eindeutig, 256 Bit)', 'user_id (FK auf users, ON DELETE CASCADE)',
    'scope, created_at, expires_at', 'revoked, revoked_at'], stereo='neu: refresh_tokens')
s.save('abb-4-1')

# ------------------------------------------------ Abb. 4.7: integration into the design-daas installation
s = Svg(1500, 820)
s.box(40, 40, 230, 70, 'blue', 'Browser', 'Nutzer, Administratoren')
s.box(310, 110, 1160, 690, 'slate', '', dashed=True)
s.text(330, 785, 'DaaS-Host (Debian), installiert mit make install aus installer/daas.env', 15, bold=True,
       anchor='start')

# column 1: nginx and frontend
s.box(340, 170, 330, 96, 'gray', 'nginx (TLS)', ['Port 8443: Frontend und /auth/', 'Port 443: DaaS-Anwendung'])
s.arrow([(155, 110), (155, 218), (340, 218)], label='HTTPS', lx=170, ly=170, anchor='start')
s.box(340, 360, 330, 80, 'green', 'Frontend', 'Container, 127.0.0.1:5001')
s.arrow([(505, 266), (505, 360)], label='/', lx=518, ly=318, anchor='start')

# column 2: DaaS application and the workloads it controls
s.box(760, 170, 300, 96, 'purple', 'DaaS-Anwendung', ['daas.service (Python)', '127.0.0.1:5000'])
s.arrow([(670, 218), (760, 218)], label=':443', lx=715, ly=208)
s.box(340, 600, 330, 120, 'gray', '', dashed=True)
s.text(358, 626, 'Docker-Standardnetz', 14, bold=True, anchor='start')
s.box(360, 638, 290, 66, 'blue', 'Benutzer-Container', 'Anwendungen der Nutzer', title_size=15)
s.box(760, 610, 300, 100, 'gray', 'Proxmox VE', ['virtuelle Maschinen (KVM),', 'Apache Guacamole'])
s.arrow([(910, 266), (910, 610)])
s.arrow([(910, 540), (505, 540), (505, 600)])
s.text(922, 500, 'steuert', 14, italic=True, color=SUB, anchor='start')

# column 3: auth service (compose project)
s.box(1130, 170, 320, 380, 'orange', '', dashed=True)
s.text(1146, 196, 'Compose-Projekt daas-auth', 14, bold=True, anchor='start')
s.text(1146, 214, '(daas-auth.service)', 13, italic=True, color=SUB, anchor='start')
s.box(1150, 230, 280, 90, 'orange', 'Backend', ['Container daas-auth', '127.0.0.1:8080'])
s.cylinder(1175, 365, 230, 96, 'teal', 'MariaDB', 'Container daas-auth-db')
s.arrow([(1290, 320), (1290, 365)])
s.text(1146, 490, 'eigenes Netz; Volumes für', 13, italic=True, color=SUB, anchor='start')
s.text(1146, 508, 'Datenbank und RSA-Schlüssel', 13, italic=True, color=SUB, anchor='start')

s.arrow([(1060, 250), (1150, 250)])
s.text(1122, 300, 'Token, Sitzung,', 13, italic=True, color=SUB, anchor='end')
s.text(1122, 317, 'Berechtigung', 13, italic=True, color=SUB, anchor='end')
s.arrow([(600, 170), (600, 140), (1400, 140), (1400, 230)], label='/auth/ zum Backend', lx=925, ly=132)

# blocked path: user containers cannot reach the auth service
red = C['red'][0]
s.add('<path d="M650,640 L1130,520" fill="none" stroke="%s" stroke-width="2.4" stroke-dasharray="8 6"/>' % red)
s.add('<path d="M1000,546 L1020,566 M1020,546 L1000,566" stroke="%s" stroke-width="3"/>' % red)
s.text(1175, 590, 'nicht erreichbar', 13.5, bold=True, color=red, anchor='start')
s.text(1175, 608, '(geprüft von check.bash)', 13, italic=True, color=red, anchor='start')
s.save('abb-4-7')
