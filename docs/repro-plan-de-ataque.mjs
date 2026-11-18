// Réplica de DramaResolucion.planDeAtaque + ordenarSinRepetir + ApiDrama.aOpciones
const clave = u => u == null ? '' : u.replace(/^https?:\/\//,'').replace(/\/+$/,'').toLowerCase();
const PESO = {EMBEBIDA:0, DIRECTA:1, PAGINA:2, MARCADOR:3};
// OJO: en JS Set.add devuelve el Set (siempre truthy), en Java devuelve boolean.
// Este ayudante reproduce el comportamiento de Java, que es el que se prueba.
const nuevo = (set, k) => { if (set.has(k)) return false; set.add(k); return true; };
function ordenarSinRepetir(todas){
  const orden=[], vistas=new Set();
  for(const p of [0,1,2,3]) for(const o of todas){
    if(PESO[o.tipo]!==p) continue; if(!nuevo(vistas, clave(o.url))) continue;
    orden.push(o);
  }
  return orden;
}
function planDeAtaque(recordados, deLaApi, utilesDeDrama){
  const out=[], vistos=new Set();
  for(const o of (recordados||[])){ if(!o||!o.url||o.tipo==='MARCADOR') continue;
    if(!nuevo(vistos, clave(o.url))) continue; out.push(o); }
  for(const o of (deLaApi||[])){ if(!o||!o.url||o.tipo==='MARCADOR') continue;
    if(!nuevo(vistos, clave(o.url))) continue; out.push(o); }
  const cola=[];
  for(const o of (utilesDeDrama||[])){ if(!o||!o.url) continue;
    if(!nuevo(vistos, clave(o.url))) continue; cola.push(o); }
  out.push(...ordenarSinRepetir(cola));
  return out;
}
function aOpciones(base, codigo, idLive, opciones){
  const out=[];
  const queEmiten = opciones.filter(o=>o.emite && o.url.startsWith('http'));
  for(const x of queEmiten){
    const proxada = `${base}/p/${codigo}/${idLive}/${x.n}.m3u8`;
    out.push({url:proxada, tipo:'DIRECTA', desc:`api·proxy [${x.n}]`});
    let t = (x.tipo==='directa') ? 'DIRECTA' : (x.tipo==='marcador'||x.tipo==='embed') ? 'EMBEBIDA':'PAGINA';
    if(t==='DIRECTA') continue;
    out.push({url:x.url, tipo:t, desc:`api·${t.toLowerCase()} [${x.n}]`});
  }
  return out;
}

const BASE='https://dramalive-api-08th.onrender.com', C='232425', D='live_tv_beinsport1';
const EMITEN=[
 {n:1,tipo:'marcador',url:'https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html',emite:true},
 {n:2,tipo:'marcador',url:'https://ult11.ultraandroidsoftstarsfeedmoon.shop/fk/xfwurjrfwhim/index.html',emite:true},
 {n:0,tipo:'marcador',url:'https://spe6.speedsuperai.cfd/pages/alkoaqoohqze/index.html',emite:true},
];
const NO_EMITEN=[
 {n:7,tipo:'directa',url:'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8',emite:false},
 {n:6,tipo:'embed',url:'https://quotarevival.net/e/yxtmusy4xj8tj',emite:false},
 {n:3,tipo:'embed',url:'https://ordernap.net/e/z7iiiknxnuqjk',emite:false},
 {n:5,tipo:'marcador',url:'https://tossequip.net/e/86xmv29g1aw01',emite:false},
];
const api = aOpciones(BASE,C,D,EMITEN.concat(NO_EMITEN));
let ok=0,tot=0; const t=(n,c)=>{tot++; if(c) ok++; else console.log('  FALLA: '+n);};

t('aOpciones: 3 emiten -> 6 entradas (proxy + pagina)', api.length===6);
t('aOpciones: nunca entra lo que no emite',
  !api.some(o=>o.url.includes('primetv')||o.url.includes('ordernap')||o.url.includes('quotarevival')||o.url.includes('tossequip')));
t('aOpciones: la 1a es la proxada HD', api[0].url===`${BASE}/p/232425/live_tv_beinsport1/1.m3u8` && api[0].tipo==='DIRECTA');
t('aOpciones: la 2a es la pagina HD', api[1].url===EMITEN[0].url && api[1].tipo==='EMBEBIDA');

// Drama entregaba basura (.LS. crudos + las 403) => utiles de Drama = las 2 embebidas muertas
const drama=[
 {url:'https://ordernap.net/e/z7iiiknxnuqjk',tipo:'EMBEBIDA'},
 {url:'https://quotarevival.net/e/yxtmusy4xj8tj',tipo:'EMBEBIDA'},
];

// A) sin memoria
let p = planDeAtaque(null, api, drama);
t('A: proxada HD primero', p[0].url.endsWith('/1.m3u8'));
// El orden de la API es INTERCALADO (proxada, página, proxada, página…) y se respeta
// tal cual: es el orden en que la API probó que emiten, con su proxy delante de cada una.
t('A: orden de la API intacto (6)',
  p.slice(0,6).map(o=>o.tipo).join(',')==='DIRECTA,EMBEBIDA,DIRECTA,EMBEBIDA,DIRECTA,EMBEBIDA');
t('A: cada proxada va DELANTE de su página',
  p[0].url.endsWith('/1.m3u8') && p[1].url===EMITEN[0].url
  && p[2].url.endsWith('/2.m3u8') && p[3].url===EMITEN[1].url
  && p[4].url.endsWith('/0.m3u8') && p[5].url===EMITEN[2].url);
t('A: el plan B de Drama va al final', p[6].url.includes('ordernap') && p[7].url.includes('quotarevival'));
t('A: sin repetidos', new Set(p.map(o=>clave(o.url))).size===p.length);
t('A: ninguna proxada se ha colado dos veces', p.filter(o=>o.url.includes('/p/')).length===3);

// B) con memoria: lo recordado manda aunque la API tenga otra cosa
const mem=[{url:EMITEN[2].url,tipo:'EMBEBIDA'}];
p = planDeAtaque(mem, api, drama);
t('B: lo recordado va primero', p[0].url===EMITEN[2].url);
t('B: y no se repite al llegar a la API', p.filter(o=>clave(o.url)===clave(EMITEN[2].url)).length===1);
t('B: 1 recordada + 6 de API - 1 duplicada + 2 de Drama = 8', p.length===8);

// C) sin API -> como la v7.38.7
p = planDeAtaque(null, null, drama);
t('C: sin API solo queda Drama', p.length===2 && p[0].url.includes('ordernap'));
p = planDeAtaque(null, [], []);
t('C: todo vacio no explota', p.length===0);
p = planDeAtaque(null, null, null);
t('C: nulls no explotan', p.length===0);

// D) drama repite una url que la API ya trae: no se duplica
const solape=[{url:EMITEN[0].url,tipo:'EMBEBIDA'}];
p = planDeAtaque(null, api, solape);
t('D: lo de Drama que ya está en la API no se duplica',
  p.filter(o=>clave(o.url)===clave(EMITEN[0].url)).length===1);
t('D: siguen siendo 6', p.length===6);

console.log(`PLAN_OK ${ok}/${tot}`);
if(ok!==tot) process.exit(1);
console.log('\n--- orden con API ---');
api.forEach((o,i)=>console.log(i, o.tipo, o.url));
