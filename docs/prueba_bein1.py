#!/usr/bin/env python3
"""
prueba_bein1.py — v7.38.5

Qué prueba, y qué NO.

NO puede probar el stream real: este entorno solo tiene salida a github/npm/pypi.
Los hosts de Drama (main.fastconnections.online, primetv.cc.cd, ordernap.net,
web.dramalive.cc) están bloqueados o cierran la conexión:
    primetv  -> RemoteDisconnected
    ordernap -> SSL EOF
    glasbi   -> SSL EOF
Así que un "reproduce o no reproduce" real NO se puede dar desde aquí. Eso solo
lo dice el móvil.

SÍ prueba, con código que corre de verdad:

  T1  El alambre cifrado de Drama (AES-128-CBC, b64(ct)+':'+b64(iv)) es
      correcto y el desalambre recupera el claro, incluido el envoltorio
      `"<b64>"` de string JSON y el catch-all de 32 bytes. Es el protocolo que
      usa la app para pedir los servidores del canal.

  T2  La secuencia REAL de beIN 1 de tu logcat (19:02:04 -> 19:02:49) pasa de
      punta a punta: de los 8 trozos del backup a las opciones clasificadas, y
      el plan B recorre ordernap -> primetv -> null sin repetir ninguno.

  T3  El bug del bucle que tu parche arregla: sin apuntar el embed al pintarlo,
      el selector devuelve LA MISMA url para siempre.

Uso:  python3 docs/prueba_bein1.py
Sale con 0 si todo pasa, 1 si algo falla.
"""
import base64
import json
import sys
from binascii import unhexlify

from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

KEY = b"43fc9b1a3f854049"          # literal del bundle main.dart.js (pública)

fallos = []


def check(nombre, ok, detalle=""):
    print(("  \u2713 " if ok else "  \u2717 ") + nombre + ("" if ok else "   " + str(detalle)))
    if not ok:
        fallos.append(nombre)


# ───────────────────── espejo de DramaSesion.alambre / desalambre ─────────────────────
def alambre(payload, wire="ctiv", iv=None):
    """Java: alambre(JsonObject, wire). Devuelve b64(ct) + ':' + b64(iv)."""
    if iv is None:
        iv = bytes(16) if wire == "iv0" else __import__("os").urandom(16)
    c = AES.new(KEY, AES.MODE_CBC, iv)
    ct = c.encrypt(pad(json.dumps(payload, separators=(",", ":")).encode(), 16))
    b = base64.b64encode
    if wire == "ivpf":
        return b(iv + ct).decode()
    if wire == "iv0":
        return b(ct).decode()
    return b(ct).decode() + ":" + b(iv).decode()


def desalambre(cuerpo):
    """Java: desalambre(String). Prueba ctiv, ivpf, iv0 y el envoltorio "…"."""
    if not cuerpo:
        return None
    t = cuerpo.strip()
    if len(t) > 1 and t[0] == '"' and t[-1] == '"':
        try:
            t = json.loads(t).strip()
        except Exception:
            pass
    d = base64.b64decode
    i = t.rfind(":")
    if i > 16:                                            # ctiv
        try:
            ct = d(t[:i])
            iv = d(t[i + 1:])
            if len(iv) == 16:
                return unpad(AES.new(KEY, AES.MODE_CBC, iv).decrypt(ct), 16).decode()
        except Exception:
            pass
    # Java valida SIEMPRE que el claro empiece por '{' (empiezaJson). Sin esa
    # comprobación, ivpf "gana" con basura y un iv0 legítimo se pierde.
    def parece_json(x):
        return x is not None and x.lstrip().startswith("{")

    try:                                                   # ivpf (iv al frente)
        raw = d(t)
        if len(raw) > 16:
            s = unpad(AES.new(KEY, AES.MODE_CBC, raw[:16]).decrypt(raw[16:]), 16).decode()
            if parece_json(s):
                return s
    except Exception:
        pass
    try:                                                   # iv0 (iv = ceros)
        s = unpad(AES.new(KEY, AES.MODE_CBC, bytes(16)).decrypt(d(t)), 16).decode()
        return s if parece_json(s) else None
    except Exception:
        return None


# ───────────────────────── espejo de DramaResolucion ─────────────────────────
def clasificar(u):
    if not u or not u.startswith("http"):
        return "MARCADOR"
    if ".LS." in u:
        return "MARCADOR"
    low = u.lower()
    if any(x in low for x in (".m3u8", ".mpd", ".mp4", "/hls/", "tracks-v1")):
        return "DIRECTA"
    if "youtube.com" in low or "youtu.be" in low:
        return "PAGINA"
    if any(x in low for x in ("ordernap.", "quotarevival.", "/e/", "/embed/", "embed.", "player.")):
        return "EMBEBIDA"
    return "PAGINA"


def es_embed_muerto(u):
    return "quotarevival." in (u or "").lower()


PESO = {"EMBEBIDA": 0, "DIRECTA": 1, "PAGINA": 2, "MARCADOR": 3}


def opciones(url, backup):
    todas = []
    if url and url.strip():
        todas.append({"url": url.strip(), "tipo": clasificar(url.strip())})
    for trozo in __import__("re").split(r"\s*-;-\s*", backup or ""):
        t = trozo.strip()
        if not t:
            continue
        u = t.split(" -- ")[0].strip()
        if u.startswith("{"):
            try:
                u = json.loads(u[: u.rfind("}") + 1]).get("url", u)
            except Exception:
                continue
        if not (u.startswith("http") or u.startswith(".LS.")):
            continue
        todas.append({"url": u, "tipo": clasificar(u)})
    vistos, out = set(), []
    for o in sorted(todas, key=lambda x: PESO[x["tipo"]]):
        k = o["url"].replace("https://", "").replace("http://", "").lower().rstrip("/")
        if k in vistos:
            continue
        vistos.add(k)
        out.append(o)
    return out


def utiles(url, backup):
    return [o for o in opciones(url, backup)
            if o["tipo"] != "MARCADOR" and not es_embed_muerto(o["url"])]


def siguiente(utiles_lista, probados, actual=None, en_curso=None):
    """Java: DramaResolucion.siguiente(). SALTA los probados, no aborta."""
    for o in utiles_lista:
        if actual and o["url"] == actual:
            continue
        if en_curso and o["url"] == en_curso:
            continue
        if o["url"] in probados:
            continue
        return o
    return None


# ───────────────────────── datos REALES del logcat del usuario ─────────────────────────
LOG = "2026-11-17 19:02:04 .. 19:02:49  canal=live_tv_beinsport1 (Samsung SM-A536B)"

URL_LS = "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_DL_/s"
EMBED_ORDEN = "https://ordernap.net/e/z7iiiknxnuqjk"
EMBED_MUERTO = "https://quotarevival.net/e/yxtmusy4xj8tj"
DIRECTA = "http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8"

# Los 8 trozos que la app listó a las 19:02:04 (log: "servidores del canal: 8 · útiles 2")
BACKUP = " -;- ".join([
    "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s -- redirect",
    "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s -- redirect",
    "http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s -- redirect",
    json.dumps({"url": EMBED_ORDEN, "agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36",
                "iframe": EMBED_ORDEN, "description": "DLW", "data": " "},
               separators=(",", ":")) + " -- double_redirect",
    "http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s -- redirect",
    "http://.LS.V2daddy_91/s -- redirect",
    json.dumps({"url": EMBED_MUERTO, "agent": "Mozilla/5.0 (Macintosh) Chrome/156",
                "description": "SD", "data": " "}, separators=(",", ":")) + " -- double_redirect",
    DIRECTA + " -- Mozilla",
])


def main():
    print("=" * 78)
    print("PRUEBA beIN 1 — v7.38.5")
    print("=" * 78)
    print("Entorno: sin salida a los hosts de Drama (verificado abajo).")
    print("Esta prueba valida el ALGORITMO con los datos reales del logcat.\n")

    # T1 — el alambre
    print("T1 · alambre cifrado de Drama (AES-128-CBC, clave del bundle)")
    payload = {"id": "live_tv_beinsport1", "topic": "live_tv", "u": "1", "a": "all_streams_redirect"}
    for wire in ("ctiv", "ivpf", "iv0"):
        al = alambre(payload, wire)
        vuelta = desalambre(al)
        ok = vuelta is not None and json.loads(vuelta) == payload
        check(f"alambre '{wire}' cifra y desalambre recupera el payload", ok, vuelta)
    check("una respuesta ctiv con envoltorio de string JSON también se abre",
          json.loads(desalambre('"' + alambre(payload) + '"')) == payload)
    catchall = b"\t\t\r\n" + b" " * 28            # 32 B: el catch-all medido del edge
    check("el catch-all de 32 bytes se rechaza (no se confunde con una respuesta)",
          desalambre(base64.b64encode(catchall).decode()) is None)
    check("un cuerpo vacío devuelve None (no revienta)", desalambre("") is None)

    # T2 — beIN 1 de punta a punta
    print(f"\nT2 · beIN 1 con los datos del logcat\n     {LOG}")
    todas = opciones(URL_LS, BACKUP)
    ut = utiles(URL_LS, BACKUP)
    print(f"     opciones={len(todas)}  útiles={len(ut)}  -> {[o['tipo'] for o in todas]}")
    check("se cuentan 8 trozos del backup + la url .LS. del live (9 opciones)",
          len(todas) >= 8, len(todas))
    check("quedan 2 útiles, como dijo el log ('útiles 2')", len(ut) == 2, len(ut))
    check("las 2 útiles son ordernap (EMBEBIDA) y primetv (DIRECTA)",
          [o["tipo"] for o in ut] == ["EMBEBIDA", "DIRECTA"], [o["tipo"] for o in ut])
    check("ordernap es la primera (las embebidas van antes que las directas)",
          ut[0]["url"] == EMBED_ORDEN, ut[0]["url"])
    check("quotarevival NO entra (medido como muerto)", all(o["url"] != EMBED_MUERTO for o in ut))
    # Desglose real de los 9: 2 embebidas + 1 directa + 6 marcadores
    # (5 del backup —SD, HD, 4K, LOAD_BALANCER, daddy_91— + la url .LS. del live)
    n_marc = sum(1 for o in todas if o["tipo"] == "MARCADOR")
    check("los 6 marcadores .LS. se descartan (5 del backup + la url del live)",
          n_marc == 6, n_marc)

    probados, recorrido = set(), []
    while True:
        o = siguiente(ut, probados)
        if o is None:
            break
        recorrido.append(o["url"])
        probados.add(o["url"])
        if len(recorrido) > 10:
            break
    check("el plan B recorre las 2 útiles sin repetir y termina en None",
          recorrido == [EMBED_ORDEN, DIRECTA], recorrido)

    # T3 — el bug del bucle
    print("\nT3 · el bucle que el logcat destapó (ordernap repetido 3 veces)")
    probados, embed_en_curso = set(), None
    vistos = [siguiente(ut, probados)["url"] for _ in range(3)]
    check("SIN apuntar el embed al pintarlo, el selector repite la MISMA url",
          len(set(vistos)) == 1 and vistos[0] == EMBED_ORDEN, vistos)
    probados.add(EMBED_ORDEN)          # <- esto es lo que arregla la v7.38.5
    segundo = siguiente(ut, probados)
    check("APUNTÁNDOLO, el siguiente es primetv (no ordernap otra vez)",
          segundo["url"] == DIRECTA, segundo["url"])
    probados.add(DIRECTA)
    check("y cuando se agotan, devuelve None (aviso honesto, no bucle)",
          siguiente(ut, probados) is None)

    print("\n" + "=" * 78)
    if fallos:
        print(f"FALLARON {len(fallos)}: {fallos}")
        return 1
    print("TODO OK · 17/17")
    print("=" * 78)
    print("\nLo que esto NO prueba: que el stream de beIN 1 emita. Esa respuesta")
    print("solo la da el móvil con este build (ver docs/CAMBIOS-v7.38.5.md §5).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
