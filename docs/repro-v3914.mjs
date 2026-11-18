// docs/repro-v3914.mjs — el embed encima del vídeo (v7.39.2)
//
// Logcat del usuario, beIN Sports 1, 2026-11-18 13:49:
//   13:49:21.681  servidores del canal: 9 · útiles 7
//   13:49:21.715  descartada (UnknownHostException, 1 ms): http://.LS.V2...description_HD_/s
//   13:49:23.184  carrera: ninguna responde (6 probadas)   <- 4 de las 6 eran marcadores
//   13:49:23.191  sin stream nativo; se prueba el embed -> ordernap.net
//   13:49:24.229  carrera ganada (manifiesto) -> /p/232425/live_tv_beinsport1/2.m3u8
//   13:49:24.244  el vídeo bueno va al reproductor... y el embed SIGUE puesto
//   13:49:53.249  plan B: probando -> PAGINA → http://.LS.V2daddy_91/s   <- encima del vídeo
//   13:49:55+     /p/.../2.m3u8 responde 200 una y otra vez (el vídeo SÍ funcionaba)
let ok = 0, ko = 0;
const comprobar = (n, c, d = '') => { if (c) { ok++; console.log(`  ✓ ${n}${d ? ' · ' + d : ''}`); }
  else { ko++; console.log(`  ✗ ${n}${d ? ' · ' + d : ''}`); } };

const esMarcadorSinClasificar = u => {
  if (!u) return false;
  if (u.includes('.LS.') || u.includes('/.ls')) return true;
  return /^https?:\/\/\.[^/]*$/.test(u) || /^https?:\/\/\.[^/]*\/.*$/.test(u);
};
const esPortal = u => { if (!u) return false; const l = u.toLowerCase();
  return l.includes('youtube.com') || l.includes('vimeo.com') || l.includes('dailymotion.com')
    || l.includes('twitch.tv') || l.includes('shoof.') || l.includes('alkass.net/live'); };
const esDashCifrado = u => { if (!u) return false; const l = u.toLowerCase();
  if (!l.includes('.mpd')) return false;
  return l.includes('cenc') || l.includes('_enc') || l.includes('starzplay') || l.includes('intigral'); };
const esCandidata = u => { const l = (u || '').toLowerCase();
  if (!l.startsWith('http')) return false;
  if (l.includes('.ls.') || l.includes('/.ls')) return false;   // v7.39.2
  if (esPortal(u)) return false; if (esDashCifrado(u)) return false; return true; };

console.log('\n1) los marcadores del logcat no son servidores');
const MARCADORES = [
  'http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_SD_/s',
  'http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_HD_/s',
  'http://.LS.V2live_tv_custom_handler_live_tv_beinsport1_description_4K_/s',
  'http://.LS.V2LOAD_BALANCERlive_tv_beinsport1/s',
  'http://.LS.V2daddy_91/s',
];
comprobar('los cinco se reconocen', MARCADORES.every(esMarcadorSinClasificar));
comprobar('ninguno entra en la carrera', MARCADORES.every(u => !esCandidata(u)));
comprobar('las urls de verdad sí entran', [
  'https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/2.m3u8',
  'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8',
  'https://ordernap.net/e/z7iiiknxnuqjk',
].every(esCandidata));

console.log('\n2) la carrera del logcat: 6 opciones -> cuántas quedan');
const PLAN = [
  'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8',
  'https://ordernap.net/e/z7iiiknxnuqjk',
  ...MARCADORES.slice(0, 4),
];
const antes = PLAN.filter(u => (u || '').toLowerCase().startsWith('http') && !esPortal(u)).length;
const ahora = PLAN.filter(esCandidata).length;
console.log(`     antes: ${antes} hilos (4 morían en 1 ms con UnknownHostException)`);
console.log(`     ahora: ${ahora} hilos`);
comprobar('se abren 2 hilos, no 6', ahora === 2, `${antes} -> ${ahora}`);
comprobar('se ahorra el 67% de los hilos', (antes - ahora) / antes > 0.6);

console.log('\n3) la secuencia completa, sin y con guardián de fuente puesta');
function secuencia(conGuardian) {
  // modelo de la v7.39.2: si YA hay imagen confirmada, la ganadora se ignora;
  // si hay un embed puesto pero aún sin imagen, la ganadora ENTRA y RETIRA el embed.
  const e = { embedPuesto: false, imagenConfirmada: false, embedEncima: false,
              planBsiguiente: false, videoNativo: false, log: [] };

  // 13:49:23.19 — la primera pasada no encontró nada: se pone el embed
  e.embedPuesto = true;
  e.log.push('13:49:23  embed de ordernap puesto en la ventana (aún sin imagen)');

  // 13:49:24.23 — llega la ganadora de la segunda pasada
  const ganadora = 'https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/2.m3u8';
  if (conGuardian && e.imagenConfirmada) {
    e.log.push('13:49:24  ganadora ignorada (ya hay imagen confirmada)');
  } else {
    e.videoNativo = true;
    e.log.push('13:49:24  vídeo bueno al reproductor: ' + ganadora.split('/').pop());
    if (e.embedPuesto) {
      if (conGuardian) {
        e.embedPuesto = false;                       // retirarEmbedSiEstaba()
        e.log.push('13:49:24  🧽 el vídeo nativo manda: se retira el embed');
      } else {
        e.embedEncima = true;
        e.log.push('13:49:24  ⚠️ el embed SIGUE encima del vídeo');
      }
    }
  }

  // 13:49:53 — el vigilante de 30 s del embed
  if (!conGuardian && e.embedPuesto) {
    e.planBsiguiente = true;
    e.log.push('13:49:53  el vigilante de 30 s pasa al siguiente «servidor»: http://.LS.V2daddy_91/s');
  } else if (conGuardian) {
    e.log.push('13:49:53  sin vigilante armado: nada que saltar');
  }
  return e;
}
const sin = secuencia(false);
console.log('   SIN guardián:');
sin.log.forEach(l => console.log('     ' + l));
comprobar('el embed se queda ENCIMA del vídeo', sin.embedEncima === true);
comprobar('y a los 30 s salta a un marcador', sin.planBsiguiente === true);

const con = secuencia(true);
console.log('   CON guardián (v7.39.2):');
con.log.forEach(l => console.log('     ' + l));
comprobar('el embed se retira al llegar el vídeo', con.embedEncima === false && con.embedPuesto === false);
comprobar('no salta a ningún marcador', con.planBsiguiente === false);
comprobar('y el vídeo bueno entra', con.videoNativo === true);

console.log('\n4) el apunte de «recordado» ya no cruza de canal');
{
  // el apunte se hace 3 s después; si el canal cambió, la generación lo tira
  let generacion = 1;
  const apunte = (genDelApunte) => genDelApunte === generacion;
  const genDelApunte = generacion;
  generacion = 2;                       // el usuario cambió de canal
  comprobar('el apunte del canal viejo se descarta', !apunte(genDelApunte));
  comprobar('el del canal actual se acepta', apunte(generacion));
}

console.log(`\nV3914_OK ${ok}/${ok + ko}`);
process.exit(ko === 0 ? 0 : 1);
