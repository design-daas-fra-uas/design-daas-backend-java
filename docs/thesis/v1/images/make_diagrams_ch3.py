"""Generate the own chapter-3 diagrams as SVG.

Usage: python make_diagrams_ch3.py <out-dir>
Render the SVGs to PNG at 2x scale (e.g. with @resvg/resvg-js).
"""
from diagram_lib import Svg, SUB, LINE, C, FONT, INK

# ------------------------------------------------ Abb. 3.2: domain model of the old PHP backend
# Verified against the Doctrine mappings in design-daas-application-backend/src/**/Model/*.php
s = Svg(1500, 980)


def entity(*args, **kwargs):
    return s.entity(*args, **kwargs)


def rel(*args, **kwargs):
    return s.rel(*args, **kwargs)


# users (single table inheritance)
entity(60, 20, 150, 'blue', 'Admin', [])
entity(250, 20, 150, 'blue', 'User', [])
bu = entity(60, 130, 340, 'blue', 'BaseUser', [
    'id (PK)', 'guid (UUID v4)', 'name, email', 'password (Hash)', 'enabled',
    'registrationCode, registrationCodeTimeout', 'role (Diskriminator: admin | user)'], stereo='«abstract»')
for cx in (135, 325):  # inheritance arrows with hollow triangles
    s.add('<path d="M%g,60 L%g,108" stroke="%s" stroke-width="2.2"/>' % (cx, cx, LINE))
    s.add('<path d="M%g,130 L%g,108 L%g,108 z" fill="#ffffff" stroke="%s" stroke-width="2.2"/>'
          % (cx, cx - 10, cx + 10, LINE))
s.text(415, 100, 'Single Table Inheritance', 13, italic=True, color=SUB, anchor='start')
s.text(415, 118, '(Tabelle users)', 13, italic=True, color=SUB, anchor='start')

ug = entity(60, 470, 340, 'purple', 'UserGroup', ['id (PK)', 'description'])
dg = entity(60, 650, 340, 'pink', 'DesktopGroup', ['id (PK)', 'description'])
dk = entity(60, 830, 340, 'pink', 'Desktop', ['id (PK)', 'description'])
rel([(230, bu[1] + bu[3]), (230, 470)], 'zero_many', 'zero_many', 'n:m (users_to_user_groups)', 245, 412, 'start')
rel([(230, 563), (230, 650)], 'zero_many', 'zero_many', 'n:m', 245, 613, 'start')
rel([(230, 743), (230, 830)], 'zero_many', 'zero_many', 'n:m (desktops_to_desktop_groups)', 245, 793, 'start')

# access rules, in cascade order
acc = []
for k, (name, y) in enumerate([('EndpointUserAccess', 130), ('EndpointUserGroupAccess', 300),
                               ('EndpointGroupUserAccess', 470), ('EndpointGroupUserGroupAccess', 640)]):
    acc.append(entity(560, y, 330, 'orange', name, ['id (PK)', 'relation: allow | deny'],
                      stereo='Stufe %d der Kaskade' % (k + 1)))
s.text(725, 800, 'je Paar höchstens ein Eintrag (UniqueConstraint)', 13, italic=True, color=SUB)

# endpoints
ep = entity(1110, 180, 330, 'green', 'Endpoint', ['id (PK)', 'functionName', 'endpointUrl', 'method'])
eg = entity(1110, 520, 330, 'green', 'EndpointGroup', ['uniqueGroupName (PK)'])
rel([(1275, 520), (1275, 304)], 'zero_one', 'zero_many', '0..1 : n', 1290, 420, 'start')

# subject side: BaseUser / UserGroup -> rules
rel([(400, 180), (560, 180)], 'one', 'zero_many')
rel([(400, 320), (480, 320), (480, 535), (560, 535)], 'one', 'zero_many')
rel([(400, 500), (450, 500), (450, 365), (560, 365)], 'one', 'zero_many')
# line hop where this connector crosses the BaseUser -> Stufe 3 connector (x = 480)
s.add('<rect x="472" y="358" width="16" height="14" fill="#ffffff"/>')
s.add('<path d="M472,365 A8,8 0 0 1 488,365" fill="none" stroke="%s" stroke-width="2.2"/>' % LINE)
rel([(400, 540), (430, 540), (430, 705), (560, 705)], 'one', 'zero_many')
# target side: rules -> Endpoint / EndpointGroup
rel([(1110, 225), (890, 225)], 'one', 'zero_many')
rel([(1110, 270), (1000, 270), (1000, 365), (890, 365)], 'one', 'zero_many')
rel([(1110, 560), (1000, 560), (1000, 535), (890, 535)], 'one', 'zero_many')
rel([(1110, 590), (1000, 590), (1000, 705), (890, 705)], 'one', 'zero_many')
s.save('abb-3-2')

# ------------------------------------------------ Abb. 3.1: request processing in the old PHP backend
s = Svg(740, 790)
steps = [
    ('blue', 'HTTP-Anfrage', None, 40),
    ('green', 'Bootstrap', '(Fehlerbehandlung, Doctrine, DI-Container)', 50),
    ('green', 'Pre-Route-Middleware', '(Basis-URL, CORS-Header)', 50),
    ('green', 'Symfony Router', '(Routen aus #[Route]-Attributen)', 50),
    ('green', 'Post-Route-Middleware', ['InvalidUserInputHandlerMiddleware,', 'OAuth2AccessControlMiddleware,',
                                        'SwaggerSchemaMiddleware'], 92),
    ('orange', 'Controller', None, 40),
    ('orange', 'Service', None, 40),
    ('orange', 'Repository', None, 40),
    ('purple', 'Doctrine EntityManager', None, 40),
]
y = 30
for i, (kind, title, sub, h) in enumerate(steps):
    s.box(40, y, 360, h, kind, title, sub, title_size=14)
    if title == 'Controller':
        s.arrow([(400, y + h / 2), (480, y + h / 2)])
        s.box(480, y, 220, h, 'blue', 'JSON-Antwort (PSR-7)', title_size=14)
    if i < len(steps) - 1:
        s.arrow([(220, y + h), (220, y + h + 26)])
    y += h + 26
s.arrow([(220, y - 26), (220, y)])
s.cylinder(135, y, 170, 76, 'teal', 'MariaDB')
s.save('abb-3-1')

# ------------------------------------------------ Abb. 3.4: AF-1 login and permission check (sequence)
s = Svg(1300, 800)
lanes = {'user': 150, 'front': 480, 'back': 810, 'daas': 1140}
heads = [('user', 'blue', 'Nutzer (Browser)'), ('front', 'green', 'Frontend'),
         ('back', 'orange', 'Backend'), ('daas', 'pink', 'DaaS-Anwendung')]
for k, kind, title in heads:
    s.box(lanes[k] - 100, 30, 200, 48, kind, title, title_size=15)
    s.add('<path d="M%g,78 L%g,770" stroke="#94a3b8" stroke-width="1.5" stroke-dasharray="6 6"/>' % (
        lanes[k], lanes[k]))


def msg(a, b, y, label, dashed=False):
    xa, xb = lanes[a], lanes[b]
    s.arrow([(xa, y), (xb + (-6 if xb > xa else 6), y)], dashed=dashed)
    # white box behind the label so lifelines do not cut through it
    w = len(label) * 7.8 + 10
    if abs(xa - xb) > 330:  # only messages that pass another lifeline
        s.add('<rect x="%g" y="%g" width="%g" height="18" fill="#ffffff"/>' % ((xa + xb) / 2 - w / 2, y - 24, w))
    s.text((xa + xb) / 2, y - 9, label, 13.5)


def self_call(lane, y, label):
    x = lanes[lane]
    s.add('<path d="M%g,%g L%g,%g L%g,%g L%g,%g" fill="none" stroke="%s" stroke-width="2.5" '
          'marker-end="url(#a)"/>' % (x, y, x + 45, y, x + 45, y + 28, x + 6, y + 28, LINE))
    s.text(x + 58, y + 19, label, 13.5, anchor='start')


# optional block: endpoint check switched on in design-daas
stroke, fill = C['orange']
s.add('<rect x="590" y="476" width="690" height="176" rx="8" fill="%s" fill-opacity="0.35" stroke="%s" '
      'stroke-width="1.8" stroke-dasharray="8 5"/>' % (fill, stroke))
s.text(602, 496, 'nur bei eingeschalteter Endpunktprüfung (DAAS_AUTH_CHECK_ENDPOINTS)', 13, italic=True,
       color=SUB, anchor='start')

msg('user', 'front', 130, 'Benutzername + Passwort')
msg('front', 'back', 180, 'POST /oauth2/user/token (Password Grant)')
msg('back', 'front', 230, 'Zugriffstoken + Refresh-Token', dashed=True)
msg('user', 'front', 290, 'Aktion auslösen (z. B. Desktop starten)')
msg('front', 'daas', 340, 'Anfrage mit Bearer-Token')
msg('daas', 'back', 390, 'GET /oauth2/user/session (Token des Nutzers)')
msg('back', 'daas', 440, 'Nutzer-ID, Name, Rolle', dashed=True)
msg('daas', 'back', 540, 'GET /permissions/{functionName}/{userId}')
self_call('back', 560, 'vierstufige Kaskade')
msg('back', 'daas', 630, 'allow / deny', dashed=True)
msg('daas', 'front', 710, 'Ergebnis der Aktion', dashed=True)
s.save('abb-3-4')
