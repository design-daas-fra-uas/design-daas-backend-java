"""Shared SVG helpers for the thesis diagrams (style of make_diagrams.py)."""
import sys
from xml.sax.saxutils import escape

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
        if not title:
            return
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

    def arrow(self, pts, dashed=False, both=False, label=None, lx=None, ly=None, anchor='middle'):
        d = 'M' + ' L'.join('%g,%g' % p for p in pts)
        self.add('<path d="%s" fill="none" stroke="%s" stroke-width="2.5"%s marker-end="url(#a)"%s/>' % (
            d, LINE, ' stroke-dasharray="9 6"' if dashed else '', ' marker-start="url(#s)"' if both else ''))
        if label:
            for k, line in enumerate(label.split('\n')):
                self.text(lx, ly + 18 * k, line, 14, italic=True, color=SUB, anchor=anchor)

    def entity(self, x, y, w, kind, name, attrs, stereo=None):
        """Entity box with a header band and one attribute per line; returns (x, y, w, h)."""
        stroke, fill = C[kind]
        head = 40 if not stereo else 54
        h = head + 10 + 21 * len(attrs)
        self.add('<rect x="%g" y="%g" width="%g" height="%g" rx="8" fill="#ffffff" stroke="%s" stroke-width="2.2"/>'
              % (x, y, w, h, stroke))
        self.add('<path d="M%g,%g h%g v%g h%g z" fill="%s"/>' % (x + 1.1, y + 1.1, w - 2.2, head - 1.1, -(w - 2.2), fill))
        self.add('<line x1="%g" y1="%g" x2="%g" y2="%g" stroke="%s" stroke-width="1.5"/>'
              % (x, y + head, x + w, y + head, stroke))
        if stereo:
            self.text(x + w / 2, y + 20, stereo, 12, italic=True, color=SUB)
            self.text(x + w / 2, y + 42, name, 15, bold=True)
        else:
            self.text(x + w / 2, y + 26, name, 15, bold=True)
        for k, a in enumerate(attrs):
            self.add('<text x="%g" y="%g" font-family="%s" font-size="12.5" fill="%s">%s</text>'
                  % (x + 12, y + head + 22 + 21 * k, FONT, INK, escape(a)))
        return x, y, w, h

    def er_end(self, px, py, dx, dy, kind):
        """Crow's-foot symbol at (px, py); (dx, dy) points from the entity border along the line."""
        nx, ny = -dy, dx
        parts = []
        if kind in ('many', 'zero_many'):
            qx, qy = px + dx * 16, py + dy * 16
            for f in (-1, 0, 1):
                parts.append((qx, qy, px + nx * 9 * f, py + ny * 9 * f))
        if kind in ('one', 'zero_one'):
            bx, by = px + dx * 12, py + dy * 12
            parts.append((bx + nx * 9, by + ny * 9, bx - nx * 9, by - ny * 9))
        if kind == 'one':
            bx, by = px + dx * 18, py + dy * 18
            parts.append((bx + nx * 9, by + ny * 9, bx - nx * 9, by - ny * 9))
        for x1, y1, x2, y2 in parts:
            self.add('<line x1="%g" y1="%g" x2="%g" y2="%g" stroke="%s" stroke-width="2.2"/>' % (x1, y1, x2, y2, LINE))
        if kind in ('zero_many', 'zero_one'):
            cx, cy = px + dx * 25, py + dy * 25
            self.add('<circle cx="%g" cy="%g" r="5" fill="#ffffff" stroke="%s" stroke-width="2"/>' % (cx, cy, LINE))

    def rel(self, pts, start, end, label=None, lx=None, ly=None, anchor='middle'):
        """Relation line through pts with crow's-foot ends; start/end are cardinality kinds."""
        d = 'M' + ' L'.join('%g,%g' % p for p in pts)
        self.add('<path d="%s" fill="none" stroke="%s" stroke-width="2.2"/>' % (d, LINE))

        def unit(a, b):
            vx, vy = b[0] - a[0], b[1] - a[1]
            n = (vx * vx + vy * vy) ** 0.5
            return vx / n, vy / n
        self.er_end(pts[0][0], pts[0][1], *unit(pts[0], pts[1]), start)
        self.er_end(pts[-1][0], pts[-1][1], *unit(pts[-1], pts[-2]), end)
        if label:
            self.text(lx, ly, label, 13, italic=True, color=SUB, anchor=anchor)

    def save(self, name, scale=2):
        defs = ('<marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" '
                'orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="%s"/></marker>'
                '<marker id="s" viewBox="0 0 10 10" refX="1" refY="5" markerWidth="7" markerHeight="7" '
                'orient="auto"><path d="M10,0 L0,5 L10,10 z" fill="%s"/></marker>') % (LINE, LINE)
        svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">'
               '<defs>%s</defs><rect width="%d" height="%d" fill="#ffffff"/>%s</svg>') % (
            self.w * scale, self.h * scale, self.w, self.h, defs, self.w, self.h, ''.join(self.parts))
        open('%s/%s.svg' % (sys.argv[1], name), 'w', encoding='utf-8').write(svg)


