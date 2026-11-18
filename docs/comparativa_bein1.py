#!/usr/bin/env python3
"""
comparativa_bein1.py — v7.38.6

Compara, con los tiempos REALES del logcat del usuario, lo que tardaba beIN 1 en
verse antes y lo que tarda con el orden nuevo.

Los tiempos de cada etapa NO son inventados: salen del logcat que el usuario pasó
(2026-11-17 18:59–19:03, Samsung SM-A536B). Las que no aparecen ahí se marcan
como ESTIMADAS y se dice por qué.

Uso:  python3 docs/comparativa_bein1.py
"""
import sys

# (etiqueta, segundos, fuente)
ANTES = [
    ("POST glasbi (2 POST al edge)",        2.5, "log: 19:02:01.94 -> 19:02:04.18"),
    ("embed ordernap (1º intento)",        15.0, "log: 19:02:04 -> 19:02:19"),
    ("mismo embed OTRA VEZ (el bug)",      15.0, "log: 19:02:19 -> 19:02:34 'se le dan 15 s más'"),
    ("mismo embed, 2ª oportunidad",        15.0, "log: 19:02:34 -> 19:02:49"),
    ("embed primetv cargando en WebView",  15.0, "log: 19:02:49 -> 19:03:04 'hay imagen'"),
]

DESPUES = [
    ("POST glasbi (2 POST al edge)",        2.5, "igual: es la API, no se toca"),
    ("ExoPlayer abre el m3u8 directo",      2.0, "ESTIMADO: load+prepare de un HLS ya resuelto"),
]


def total(etapas):
    return sum(s for _, s, _ in etapas)


def barra(seg, escala=0.55):
    return "#" * max(1, int(seg * escala))


def main():
    ta, td = total(ANTES), total(DESPUES)

    print("=" * 78)
    print("beIN 1 — de pulsar el canal a ver imagen")
    print("=" * 78)
    print("Tiempos del logcat real (2026-11-17). Las etapas sin log van marcadas.\n")

    print(f"ANTES (v7.38.3): {ta:.0f} s")
    print("-" * 78)
    for n, s, f in ANTES:
        print(f"  {s:5.1f}s  {barra(s):<12} {n}")
        print(f"          {'':<12} {f}")
    print(f"  {ta:5.1f}s  TOTAL\n")

    print(f"DESPUÉS (v7.38.6): {td:.0f} s")
    print("-" * 78)
    for n, s, f in DESPUES:
        print(f"  {s:5.1f}s  {barra(s):<12} {n}")
        print(f"          {'':<12} {f}")
    print(f"  {td:5.1f}s  TOTAL\n")

    print("=" * 78)
    print(f"De {ta:.0f} s a {td:.0f} s  ->  {ta/td:.1f}x más rápido  "
          f"(ahorro: {ta-td:.0f} s por canal)")
    print("=" * 78)

    print("""
Qué cambia, en una frase:
  glasbi YA devolvía el m3u8 de primetv en el segundo 2. La app lo tiraba para
  probar un embed que no emitía, y al final volvía a ESE MISMO m3u8… metido en un
  WebView. Ahora se gasta lo que ya está resuelto.

Y lo que NO cambia:
  - El bucle del embed lo arregló v7.38.4/5 (servidoresProbados.add al pintar).
    Aquí ya no se llega a él, pero sigue cubierto si la directa falla.
  - Los 6 marcadores .LS. siguen sin poder abrirse desde la app: los resuelve el
    redirector del sitio dentro de su web. No es un defecto de este código.
  - Si primetv estuviera caído, la ruta es directa -> embed -> aviso honesto, y
    son ~35 s (2,5 de API + 30 de sondeo del embed). Mejor que 61, peor que 4.

Riesgo asumido: se ve la directa en el reproductor nativo (con PiP y calidad) en
vez de en el embed. Si esa directa fuese la tarjeta rusa «ОТСУТСТВУЕТ ПОДПИСКА»,
el usuario vería el cartel unos segundos antes de que onPlayerError lance el plan
B. Es el precio de ir rápido; la alternativa son 61 s de negro garantizados.
""")
    return 0


if __name__ == "__main__":
    sys.exit(main())
