// Réplica de sirve()/podriaEmitir()/queEmiten()/aOpciones()/planDeAtaque (v7.38.9)
const sirve = x => x.emite === true && x.url.startsWith('http');
function podriaEmitir(x){
  if(sirve(x)) return true;
  if(!x.url || !x.url.startsWith('http')) return false;
  if(x.http===403||x.http===404||x.http===410) return false;
  if(x.http>=200 && x.http<300) return true;
  const l=x.url.toLowerCase();
  return l.includes('.mpd')||l.includes('.m3u8');
}
function queEmiten(opciones){
  const conf=opciones.filter(sirve);
  const dud=opciones.filter(x=>!sirve(x) && podriaEmitir(x));
  return conf.concat(dud);
}
const BASE='https://dramalive-api-08th.onrender.com';
function aOpciones(base,C,D,opciones){
  const out=[];
  for(const x of queEmiten(opciones)){
    if(esPortal(x.url)) continue;
    const confirmada = sirve(x);
    const proxada = confirmada ? `${base}/p/${C}/${D}/${x.n}.m3u8` : null;
    if(proxada) out.push({url:proxada,tipo:'DIRECTA',desc:`api·proxy [${x.n}]`});
    let t = (x.tipo==='directa') ? 'DIRECTA' : (x.tipo==='marcador'||x.tipo==='embed') ? 'EMBEBIDA':'PAGINA';
    if(t==='DIRECTA' && proxada) continue;
    out.push({url:x.url,tipo:t,desc:`api·${t.toLowerCase()} [${x.n}]`});
  }
  return out;
}
const clave = u => { if(u==null) return ""; let s=u.trim().toLowerCase().replace(/^https?:\/\//,"").replace(/^www\./,""); while(s.endsWith("/")) s=s.slice(0,-1); return s; };
const nuevo = (set,k)=>{ if(set.has(k)) return false; set.add(k); return true; };
const esPortal = u => { if(!u) return false; const l=u.toLowerCase();
  return l.includes('youtube.com')||l.includes('youtu.be')||l.includes('vimeo.com')||l.includes('dailymotion.com')||l.includes('twitch.tv')||l.includes('facebook.com/watch'); };
const PESO={'EMBEBIDA':0,'DIRECTA':1,'PAGINA':2,'MARCADOR':3};
function ordenarSinRepetir(t){const o=[],v=new Set();for(const p of [0,1,2,3])for(const x of t){if(PESO[x.tipo]!==p)continue;if(!nuevo(v,clave(x.url)))continue;o.push(x);}return o;}
function planDeAtaque(rec,api,drama){
  const out=[],v=new Set();
  for(const o of (rec||[])){if(!o||!o.url||o.tipo==='MARCADOR')continue;if(!nuevo(v,clave(o.url)))continue;out.push(o);}
  for(const o of (api||[])){if(!o||!o.url||o.tipo==='MARCADOR')continue;if(!nuevo(v,clave(o.url)))continue;if(esPortal(o.url))continue;out.push(o);}
  const cola=[];
  for(const o of (drama||[])){if(!o||!o.url)continue;if(!nuevo(v,clave(o.url)))continue;if(esPortal(o.url))continue;cola.push(o);}
  out.push(...ordenarSinRepetir(cola));
  return out;
}

// datos REALES medidos 2026-11-18
const abudabi=[
 {n:1,tipo:'directa',url:'https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd',emite:false,http:299},
 {n:0,tipo:'directa',url:'https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/emarat_tv/emarat_tv_dash_enc/index.mpd',emite:false,http:200},
 {n:2,tipo:'directa',url:'https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv_enc/emarat_tv/emarat_tv_hls_nd/index.m3u8',emote:false,emite:false,http:403},
];
const news=[
 {n:0,tipo:'marcador',url:'https://max10.maxpcioscontent.shop/pages/vhxivhzkeuix/index.html',emite:true,http:200},
 {n:3,tipo:'otra',url:'https://www.youtube.com/@beINSPORTS/live',emite:false,http:200},
 {n:1,tipo:'embed',url:'https://ordernap.net/e/b11wbtzmon53m',emite:false,http:403},
];
const bein1=[
 {n:1,tipo:'marcador',url:'https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html',emite:true,http:200},
 {n:7,tipo:'directa',url:'http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8',emite:false,http:403},
];
const bein5=[
 {n:1,tipo:'marcador',url:'https://sup7.superdatanet.cfd/hd/mzphgusqtrxo/index.html',emite:true,http:200},
 {n:2,tipo:'marcador',url:'https://tur11.turboapistorage.shop/fk/vshlsjobrcee/index.html',emite:true,http:200},
 {n:0,tipo:'marcador',url:'https://rap8.rapidbackend.shop/sd/unlceroyjeny/index.html',emite:false,http:null},
];

let ok=0,tot=0; const t=(n,c)=>{tot++; if(c) ok++; else console.log('  FALLA: '+n);};

// 1) Abu Dhabi: antes 0 opciones (negro), ahora 2
let ops=aOpciones(BASE,'232425','live_tv_abudabyuae',abudabi);
t('abudabi: rescata 2 opciones (antes 0)', ops.length===2);
t('abudabi: las dos son .mpd', ops.every(o=>o.url.endsWith('.mpd')));
t('abudabi: ninguna proxada', !ops.some(o=>o.url.includes('/p/')));
t('abudabi: sin el 403', !ops.some(o=>o.url.includes('hls_nd')));
const planAbu=planDeAtaque(null,ops,null);
t('abudabi: plan no vacio', planAbu.length===2);

// 2) beIN 1 sigue igual que funcionaba
ops=aOpciones(BASE,'232425','live_tv_beinsport1',bein1);
t('bein1: proxada + pagina = 2', ops.length===2);
t('bein1: la proxada es la 1a', ops[0].url.endsWith('/1.m3u8') && ops[0].tipo==='DIRECTA');
t('bein1: sin el 403 de primetv', !ops.some(o=>o.url.includes('primetv')));

// 3) beIN 5: dos confirmadas -> 4 entradas
ops=aOpciones(BASE,'232425','live_tv_beinsport5',bein5);
t('bein5: 2 confirmadas -> 4', ops.length===4);
t('bein5: orden intercalado', ops.map(o=>o.tipo).join(',')==='DIRECTA,EMBEBIDA,DIRECTA,EMBEBIDA');
t('bein5: el timeout no entra (http null, no es mpd/m3u8)', !ops.some(o=>o.url.includes('rapidbackend')));

// 4) beIN News: YouTube fuera, el bueno dentro
ops=aOpciones(BASE,'232425','live_tv_beinsportnews',news);
const planNews=planDeAtaque(null,ops,null);
t('news: sin youtube en el plan', !planNews.some(o=>o.url.includes('youtube')));
t('news: la proxada del que emite va primero', planNews[0].url.endsWith('/0.m3u8'));
t('news: y su pagina detras', planNews[1].url.includes('maxpcioscontent'));
t('news: proxada + pagina del bueno, YouTube fuera', ops.length===2);

// 5) sin regresion: el 403 puro sigue fuera en todos
t('nada de 403 en ningun plan', !planNews.some(o=>o.url.includes('ordernap')));

console.log(`V389_OK ${ok}/${tot}`);
if(ok!==tot) process.exit(1);
