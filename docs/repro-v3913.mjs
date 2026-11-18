// docs/repro-v3913.mjs — «clico un canal y se queda en otro» (v7.39.1)
//
// El reproductor es singleTask: el segundo canal llega a onNewIntent SOBRE la misma
// instancia. Lo que seguía vivo del primero (carrera, glasbi, WebView, vigilantes del
// embed) volvía después y remataba al segundo. Aquí se modela el orden real de los
// eventos del logcat del usuario y se compara: sin generación vs con generación.
let ok = 0, ko = 0;
const comprobar = (n, c, d = '') => { if (c) { ok++; console.log(`  ✓ ${n}${d ? ' · ' + d : ''}`); }
  else { ko++; console.log(`  ✗ ${n}${d ? ' · ' + d : ''}`); } };

// ── el guardián (espejo de GeneracionDelCanal.java) ──────────────────────────
class Generacion {
  constructor() { this.actual = 0; }
  abrirCanal() { return ++this.actual; }
  vigente(g) { return g === this.actual; }
  decidir(g) { return g === this.actual ? 'APLICAR' : 'DESCARTAR'; }
}

// ── el reproductor, con y sin guardián ───────────────────────────────────────
// Orden real de los hechos (el del logcat): el usuario toca A, a los 300 ms toca B,
// y lo de A sigue corriendo. Lo que importa es QUIÉN llega al final.
function simular(conGuardian) {
  const gen = new Generacion();
  const p = { enPantalla: null, imagen: false, cambiosDeFuente: 0, avisosSinStream: 0 };
  const log = [];

  const deA = gen.abrirCanal();                       // onCreate del canal A
  const tareas = [];
  const deB = gen.abrirCanal();                       // onNewIntent del canal B
  p.enPantalla = 'B';

  // lo de A, repartido en el tiempo, y lo de B
  tareas.push({ nombre: 'carrera de A',  t: 1200,  gen: deA, efecto: () => ({ url: 'A/proxy/0.m3u8' }) });
  tareas.push({ nombre: 'glasbi de A',   t: 900,   gen: deA, efecto: () => ({ url: 'A/directa.m3u8' }) });
  tareas.push({ nombre: 'resolución de B', t: 1800, gen: deB, efecto: () => ({ url: 'B/ok.m3u8' }) });
  tareas.push({ nombre: 'vigilante del embed de A', t: 15300, gen: deA, efecto: () => ({ cambiar: true }) });

  tareas.sort((a, b) => a.t - b.t);

  for (const t of tareas) {
    const aplica = !conGuardian || gen.decidir(t.gen) === 'APLICAR';
    if (!aplica) { log.push(`t=${t.t}ms  ${t.nombre}: DESCARTADA (generación ${t.gen} ≠ ${gen.actual})`); continue; }
    const e = t.efecto();
    if (e.url) {
      p.enPantalla = t.gen === deA ? 'A' : 'B';
      p.imagen = true;
      p.cambiosDeFuente++;
      log.push(`t=${t.t}ms  ${t.nombre}: fuente -> ${e.url} (pantalla ${p.enPantalla})`);
    }
    if (e.cambiar) {
      p.cambiosDeFuente++;
      log.push(`t=${t.t}ms  ${t.nombre}: CAMBIA el servidor del canal en pantalla`);
    }
  }
  return { p, log };
}

console.log('\n1) el guardián, en corto');
{
  const g = new Generacion();
  const a = g.abrirCanal();
  comprobar('al nacer, lo que vuelve es del canal actual', g.vigente(a));
  const b = g.abrirCanal();
  comprobar('al abrir otro canal, lo del anterior caduca', !g.vigente(a));
  comprobar('el nuevo manda', g.vigente(b) && g.decidir(b) === 'APLICAR');
  comprobar('un número que no existe no entra', !g.vigente(999) && !g.vigente(-1));
  const g2 = new Generacion(); const v1 = g2.abrirCanal(); const v2 = g2.abrirCanal();
  comprobar('reabrir el MISMO canal también invalida', !g2.vigente(v1) && g2.vigente(v2));
}

console.log('\n2) SIN guardián (lo que pasaba)');
const sin = simular(false);
sin.log.forEach(l => console.log('     ' + l));
comprobar('las tareas del canal viejo llegan y TOCAN el reproductor', sin.p.cambiosDeFuente === 4,
  `${sin.p.cambiosDeFuente} cambios`);
comprobar('a los 15,3 s el vigilante de A cambia el servidor del canal B',
  sin.log.some(l => l.includes('vigilante del embed de A') && l.includes('CAMBIA')));

console.log('\n3) CON guardián (v7.39.1)');
const con = simular(true);
con.log.forEach(l => console.log('     ' + l));
comprobar('la pantalla se queda en el canal que el usuario tocó', con.p.enPantalla === 'B');
comprobar('ninguna tarea del canal viejo toca el reproductor',
  con.log.every(l => !l.includes('PISA') && !(l.includes('de A') && l.includes('->'))),
  'solo la de B');
comprobar('el canal nuevo sí abre', con.p.imagen === true);
comprobar('se descarta lo del canal viejo, y se dice en el log',
  con.log.filter(l => l.includes('DESCARTADA')).length === 3);
comprobar('solo queda el cambio de fuente del canal nuevo', con.p.cambiosDeFuente === 1,
  `${con.p.cambiosDeFuente} cambio`);

console.log('\n4) el caso del logcat: 4 respuestas viejas escalonadas');
{
  const g = new Generacion(); const viejo = g.abrirCanal(); const nuevo = g.abrirCanal();
  let aplicadas = 0; for (let i = 0; i < 4; i++) if (g.decidir(viejo) === 'APLICAR') aplicadas++;
  comprobar('las cuatro se tiran', aplicadas === 0);
  comprobar('y el canal nuevo queda vivo', g.vigente(nuevo));
}

console.log(`\nV3913_OK ${ok}/${ok + ko}`);
process.exit(ko === 0 ? 0 : 1);
