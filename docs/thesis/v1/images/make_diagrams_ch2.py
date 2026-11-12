"""Generate the own chapter-2 diagrams as SVG, in the style of make_diagrams.py.

Usage: python make_diagrams_ch2.py <out-dir>
Render the SVGs to PNG at 2x scale (e.g. with @resvg/resvg-js).
"""
from diagram_lib import Svg, SUB

# ------------------------------------------------ Abb. 2.2: how the three parts of DESIGN work together
s = Svg(1400, 620)
s.box(60, 30, 300, 74, 'blue', 'Browser', 'Nutzer und Administratoren')
s.box(60, 250, 300, 90, 'green', 'Frontend', ['React-Anwendung', 'design-daas-application-frontend'])
s.box(550, 250, 300, 90, 'purple', 'DaaS-Anwendung', ['Python', 'design-daas'])
s.box(1040, 250, 300, 90, 'orange', 'Backend', ['Gegenstand dieser Arbeit', 'Java 21, Spring Boot'])
s.arrow([(210, 104), (210, 250)], label='lädt die Oberfläche', lx=222, ly=170, anchor='start')
s.arrow([(330, 250), (330, 190), (1190, 190), (1190, 250)],
        label='Anmeldung, Nutzer-, Gruppen- und Desktopverwaltung (REST, Bearer-Token)', lx=760, ly=178)
s.arrow([(360, 295), (550, 295)], label='Objekte starten\nund stoppen', lx=455, ly=258)
s.arrow([(850, 295), (1040, 295)], label='Sitzung und\nBerechtigung prüfen', lx=945, ly=258)
s.box(320, 470, 250, 80, 'teal', 'Apache Guacamole', 'Remote-Desktop-Gateway')
s.box(600, 470, 200, 80, 'blue', 'Docker', 'Container')
s.box(830, 470, 180, 80, 'gray', 'Proxmox VE', 'KVM-VMs')
for x in (445, 700, 920):
    s.arrow([(700, 340), (700, 410), (x, 410), (x, 470)])
s.text(712, 395, 'steuert', 14, italic=True, color=SUB, anchor='start')
s.cylinder(1090, 462, 200, 96, 'teal', 'MariaDB', 'Nutzer, Rechte, Token')
s.arrow([(1190, 340), (1190, 462)])
s.arrow([(60, 67), (30, 67), (30, 510), (320, 510)], both=True,
        label='Bildschirmanzeige\n(WebSocket)', lx=45, ly=440, anchor='start')
s.save('abb-2-2')

# ------------------------------------------------ Abb. 2.3: virtual machines vs. containers
s = Svg(1400, 560)
s.box(40, 20, 640, 470, 'slate', '', dashed=True)
s.box(720, 20, 640, 470, 'slate', '', dashed=True)
s.text(360, 55, 'Virtuelle Maschinen', 18, bold=True)
s.text(1040, 55, 'Container', 18, bold=True)
for i, x in enumerate((70, 270, 470)):
    s.box(x, 80, 180, 56, 'orange', 'Anwendung %d' % (i + 1), title_size=15)
    s.box(x, 148, 180, 56, 'pink', 'Bibliotheken', title_size=15)
    s.box(x, 216, 180, 84, 'purple', 'Gast-Betriebs-', 'system mit Kernel', title_size=15)
for i, x in enumerate((750, 950, 1150)):
    s.box(x, 80, 180, 56, 'orange', 'Anwendung %d' % (i + 1), title_size=15)
    s.box(x, 148, 180, 56, 'pink', 'Bibliotheken', title_size=15)
s.box(70, 312, 580, 70, 'blue', 'Hypervisor', 'z. B. KVM / Proxmox VE')
s.box(750, 216, 580, 84, 'green', 'Container-Laufzeit', 'z. B. Docker Engine')
s.box(750, 312, 580, 70, 'blue', 'Host-Betriebssystem', 'gemeinsamer Linux-Kernel')
s.box(70, 394, 580, 70, 'gray', 'Hardware')
s.box(750, 394, 580, 70, 'gray', 'Hardware')
s.text(360, 525, 'starke Isolation, eigener Kernel je VM, Start in Minuten', 14, italic=True, color=SUB)
s.text(1040, 525, 'Isolation über Namespaces und cgroups, Start in Sekunden', 14, italic=True, color=SUB)
s.save('abb-2-3')

# ------------------------------------------------ Abb. 2.4: Apache Guacamole architecture
s = Svg(1400, 420)
s.box(430, 40, 640, 290, 'slate', '', dashed=True)
s.text(460, 75, 'Guacamole-Server', 17, bold=True, anchor='start')
s.box(40, 150, 240, 90, 'blue', 'Browser', ['HTML5, Canvas', 'kein Plugin nötig'])
s.box(470, 150, 250, 90, 'orange', 'guacamole-client', ['Java-Webanwendung', '(z. B. in Apache Tomcat)'])
s.box(840, 150, 200, 90, 'green', 'guacd', 'Protokoll-Übersetzung')
s.arrow([(280, 195), (470, 195)], both=True, label='Guacamole-Protokoll\nüber WebSocket', lx=375, ly=158)
s.arrow([(720, 195), (840, 195)], both=True, label='Guacamole-\nProtokoll', lx=780, ly=158)
targets = [(70, 'VNC-Dienst', 'z. B. Linux-Container'), (180, 'RDP-Dienst', 'z. B. Windows-VM'),
           (290, 'SSH-Dienst', 'Kommandozeile')]
for y, t, sub in targets:
    s.box(1160, y, 200, 80, 'pink', t, sub)
    s.arrow([(1040, 195), (1100, 195), (1100, y + 40), (1160, y + 40)], both=True)
s.text(700, 395, 'Der Browser spricht nie selbst VNC oder RDP: guacd übersetzt die Anzeige in einfache '
       'Zeichenbefehle.', 14, italic=True, color=SUB)
s.save('abb-2-4')
