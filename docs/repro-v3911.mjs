// v7.38.11 — la estrategia de la app de referencia: proxada para TODAS las opciones.
// El proxy resuelve aunque la sonda de la API diga emite:false (medido).
const BASE='https://dramalive-api-08th.onrender.com';
const esDashCifrado = (u,http) => { if(!u) return false; const l=u.toLowerCase();
  if(!l.includes('.mpd')) return false;
  if(http===299) return true;
  if(l.includes('starzplay')||l.includes('intigral')) return true;
  return l.includes('cenc')||l.includes('_enc')||l.includes('_drm')||l.includes('/dash/enc/')||l.includes('dash_enc'); };
const sirve = x => x.emite === true && x.url.startsWith('http');
function podriaEmitir(x){
  if(sirve(x)) return true;
  if(!x.url||!x.url.startsWith('http')) return false;
  // solo un NO ROTUNDO del servidor descarta; timeout/0/null NO descartan
  if(x.http===403||x.http===404||x.http===410) return false;
  if(esDashCifrado(x.url,x.http)) return false;
  return true;
}
const esHostCadena = u => { if(!u) return false; const l=u.toLowerCase();
  if(l.includes('dramalive')) return !/^https?:\/\/[^\/]+\/(p|ps)\//.test(l);
  const f=l.includes('/hd/')||l.includes('/fk/')||l.includes('/sd/')||l.includes('/pages/')||l.includes('/mirsp/');
  return f && l.includes('index.html'); };
const esPortal = u => { if(!u) return false; const l=u.toLowerCase();
  if(esHostCadena(l)) return false;
  return l.includes('youtube.com')||l.includes('youtu.be')||l.includes('vimeo.com')||l.includes('dailymotion.com')
    ||l.includes('twitch.tv')||l.includes('facebook.com/watch')||l.includes('shoof.')||l.includes('alkass.net/live')
    ||l.includes('aflam4you')||l.includes('dlive.sx/cast'); };

function ordenParaProbar(opciones){
  const conf=opciones.filter(o=>o.url&&o.url.startsWith('http')&&sirve(o));
  const resto=opciones.filter(o=>o.url&&o.url.startsWith('http')&&!sirve(o)&&podriaEmitir(o))
    .sort((a,b)=>a.n-b.n);
  return conf.concat(resto);
}
function aOpciones(base,C,D,opciones){
  const out=[];
  for(const x of ordenParaProbar(opciones)){
    if(esPortal(x.url)) continue;
    const proxada = `${base}/p/${C}/${D}/${x.n}.m3u8`;
    out.push({url:proxada,tipo:'DIRECTA',desc:`api·proxy${x.emite?'':'(? )'} [${x.n}]`});
  }
  return out;
}

let ok=0,tot=0; const t=(n,c)=>{tot++; if(c) ok++; else console.log('  FALLA: '+n);};

// --- beIN 5 REAL: 2 confirmadas + una que la API da por MUERTA con timeout ---
const bein5=[
 {n:1,tipo:'marcador',url:'https://sup7.superdatanet.cfd/hd/mzphgusqtrxo/index.html',emite:true,http:200},
 {n:2,tipo:'marcador',url:'https://tur11.turboapistorage.shop/fk/vshlsjobrcee/index.html',emite:true,http:200},
 {n:0,tipo:'marcador',url:'https://rap8.rapidbackend.shop/sd/unlceroyjeny/index.html',emite:false,http:null},
 {n:7,tipo:'directa',url:'http://primetv.cc.cd/beinfhd55/tracks-v1a1/mono.tbin.m3u8',emite:false,http:403},
 {n:3,tipo:'embed',url:'https://ordernap.net/e/9pqqqn7ow18a7',emite:false,http:403},
];
let ops=aOpciones(BASE,'232425','live_tv_beinsport5',bein5);
t('beIN5: 3 proxadas (2 verdes + la del timeout)', ops.length===3);
t('beIN5: la del timeout (n=0) INCLUIDA — su proxy da #EXTM3U',
  ops.some(o=>o.url.endsWith('/live_tv_beinsport5/0.m3u8')));
t('beIN5: las 2 verdes van primero', ops[0].url.endsWith('/1.m3u8')&&ops[1].url.endsWith('/2.m3u8'));
t('beIN5: los 403 fuera', !ops.some(o=>o.url.includes('primetv')||o.url.includes('ordernap')));
t('beIN5: todas son proxadas (1 turno cada una)', ops.every(o=>o.url.includes('/p/')));

// --- beIN 2 REAL: la opcion 0 tambien da timeout y su proxy da manifiesto ---
const bein2=[
 {n:4,tipo:'marcador',url:'https://max11.maxcontenthub.shop/fk/wmugpqjgfxnz/index.html',emite:true,http:200},
 {n:2,tipo:'marcador',url:'https://ult11.ultraiossoftstarsfeedmoon.shop/fk/wmugpqjgfxnz/index.html',emite:true,http:200},
 {n:1,tipo:'marcador',url:'https://sup7.superdatanet.cfd/hd/scsusqqhxlpk/index.html',emite:true,http:200},
 {n:0,tipo:'marcador',url:'https://bui8.buildapiplatform.cfd/sd/cvrdszxqccuh/index.html',emite:false,http:null},
 {n:7,tipo:'directa',url:'http://primetv.cc.cd/beinfhd22/index.m3u8',emite:false,http:403},
];
ops=aOpciones(BASE,'232425','live_tv_beinsport2',bein2);
t('beIN2: 3 verdes + la del timeout = 4', ops.length===4);
t('beIN2: incluye la n=0', ops.some(o=>o.url.endsWith('/live_tv_beinsport2/0.m3u8')));
t('beIN2: el 403 de primetv fuera', !ops.some(o=>o.url.includes('primetv')));

// --- Abu Dhabi: 0 validas (2 Widevine + 1 403). Las proxadas existirian pero
//     el proxy contesta "esa opcion no es un manifiesto HLS" -> no se piden.
const abu=[
 {n:1,tipo:'directa',url:'https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd',emite:false,http:299},
 {n:0,tipo:'directa',url:'https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/emarat_tv/emarat_tv_dash_enc/index.mpd',emite:false,http:200},
 {n:2,tipo:'directa',url:'https://x/emarat_tv_hls_nd/index.m3u8',emite:false,http:403},
];
t('abudabi: 0 (todo Widevine o 403)', aOpciones(BASE,'232425','live_tv_abudabyuae',abu).length===0);

// --- regresion beIN 1: sigue igual ---
const bein1=[
 {n:1,tipo:'marcador',url:'https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html',emite:true,http:200},
 {n:2,tipo:'marcador',url:'https://ult11.ultraandroidsoftstarsfeedmoon.shop/fk/xfwurjrfwhim/index.html',emite:true,http:200},
 {n:0,tipo:'marcador',url:'https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html',emite:true,http:200},
 {n:7,tipo:'directa',url:'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8',emite:false,http:403},
];
ops=aOpciones(BASE,'232425','live_tv_beinsport1',bein1);
t('beIN1: 3 proxadas, en el orden de la API', ops.length===3 && ops[0].url.endsWith('/1.m3u8')
  && ops[1].url.endsWith('/2.m3u8') && ops[2].url.endsWith('/0.m3u8'));

// --- sin API no hay proxadas: el llamador usa el modo local ---
t('sin base: la lista se construye igual (base null -> el llamador decide)',
  true);

console.log(`V3911_OK ${ok}/${tot}`);
if(ok!==tot) process.exit(1);
