from PIL import Image, ImageDraw, ImageFont
import textwrap, sys

raw = open('/tmp/salida.txt').read().rstrip().split('\n')
lineas = []
for ln in raw:
    if len(ln) <= 104:
        lineas.append(ln)
    else:
        # partir la lista de tipos sin romper la palabra
        corte = ln.rfind(',', 0, 104)
        if corte < 40: corte = 104
        lineas.append(ln[:corte+1])
        lineas.append(' ' * 12 + ln[corte+1:].lstrip())

W, PAD = 1180, 34
LH = 26
H = PAD*2 + LH*len(lineas) + 70

img = Image.new('RGB', (W, H), (13, 17, 23))
d = ImageDraw.Draw(img)

def fuente(size, bold=False):
    rutas = [
        '/usr/share/fonts/truetype/dejavu/DejaVuSansMono%s.ttf' % ('-Bold' if bold else ''),
        '/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf' % ('-Bold' if bold else ''),
    ]
    for r in rutas:
        try: return ImageFont.truetype(r, size)
        except Exception: pass
    return ImageFont.load_default()

mono = fuente(17)
mono_b = fuente(17, True)

# barra de titulo tipo terminal
d.rectangle([0,0,W,44], fill=(22,27,34))
for i,c in enumerate([(255,95,86),(255,189,46),(39,201,63)]):
    d.ellipse([20+i*24, 16, 34+i*24, 30], fill=c)
d.text((110, 13), "prueba_bein1.py  —  v7.38.5  —  beIN 1", font=mono_b, fill=(201,209,217))

y = 44 + PAD
for ln in lineas:
    if ln.startswith('==='):
        d.text((PAD, y), '─'*76, font=mono, fill=(48,54,61)); y += LH; continue
    color = (201,209,217)
    if '✓' in ln: color = (63,185,80)
    elif '✗' in ln: color = (248,81,73)
    elif ln.startswith('T1') or ln.startswith('T2') or ln.startswith('T3'): color = (88,166,255)
    elif 'TODO OK' in ln: color = (63,185,80)
    elif ln.startswith('PRUEBA'): color = (210,153,255)
    elif ln.startswith('Lo que esto NO') or ln.startswith('solo la da'): color = (139,148,158)
    d.text((PAD, y), ln, font=mono, fill=color)
    y += LH

img.save('/home/user/recuerdos/docs/prueba-bein1.png')
print("PNG:", img.size)
