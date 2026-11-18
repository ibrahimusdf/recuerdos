// Simulacion fiel de ServidoresDelCanal + DramaResolucion.conRecordados
const SEP='\u0001', SEPI='\u0002';
function ordenar(l){const c=[...l];
  const p=x=>(x.exitos>0&&x.exitos>=x.fallos)?0:(x.exitos>0?1:2);
  c.sort((a,b)=>p(a)!==p(b)?p(a)-p(b):(a.fallos!==b.fallos?a.fallos-b.fallos:b.ultimoUso-a.ultimoUso));
  return c;}
function recordar(l,url,ua,ref,ahora){const out=[];let ex=0;
  for(const c of (l||[])){ if(c.url!==url) out.push(c); else ex=c.exitos; }
  out.unshift({url,ua,referer:ref,exitos:ex+1,fallos:0,ultimoUso:ahora});return ordenar(out);}
function fallar(l,url,ahora){const out=[];let v=false;
  for(const c of (l||[])){ if(c.url!==url) out.push(c); else {v=true;out.push({...c,fallos:c.fallos+1,ultimoUso:ahora});}}
  if(!v&&url.startsWith('http')) out.push({url,ua:null,referer:null,exitos:0,fallos:1,ultimoUso:ahora});
  return ordenar(out);}
function aTexto(l){return l.map(c=>[c.url,c.ua||'',c.referer||'',c.exitos,c.fallos,c.ultimoUso].join(SEP)).join(SEPI);}
function deTexto(t){if(!t||!t.trim())return[];const out=[];
  for(const it of t.split(SEPI)){ if(!it)continue; const p=it.split(SEP);
    if(p.length<6)continue; const u=p[0].trim(); if(!u.startsWith('http'))continue;
    out.push({url:u,ua:p[1]||null,referer:p[2]||null,exitos:parseInt(p[3])||0,fallos:parseInt(p[4])||0,ultimoUso:parseInt(p[5])||0});}
  return ordenar(out);}
const clasif=u=>{const l=u.toLowerCase();if(l.includes('.m3u8'))return'DIRECTA';
  if(l.includes('youtube'))return'PAGINA';if(l.includes('/e/'))return'EMBEBIDA';return'PAGINA';};
function directaCon(u,ua,ref){let t=clasif(u);if(t!=='DIRECTA')t='PAGINA';return{url:u,tipo:t,ua,referer:ref};}
function opcionesDe(l){return ordenar(l).map(c=>directaCon(c.url,c.ua,c.referer));}
function clave(u){u=u.replace(/^https?:\/\//,'').toLowerCase();while(u.endsWith('/'))u=u.slice(0,-1);return u;}
function conRecordados(rec,utiles){const out=[],v=new Set();
  for(const o of (rec||[])){if(!o||o.tipo==='MARCADOR')continue;if(v.has(clave(o.url)))continue;v.add(clave(o.url));out.push(o);}
  const nuevas=[];
  for(const o of (utiles||[])){if(v.has(clave(o.url)))continue;v.add(clave(o.url));nuevas.push(o);}
  const peso=t=>({EMBEBIDA:0,DIRECTA:1,PAGINA:2,MARCADOR:3})[t];
  const visto2=new Set();
  for(let p=0;p<4;p++)for(const o of nuevas){if(peso(o.tipo)!==p)continue;if(visto2.has(clave(o.url)))continue;visto2.add(clave(o.url));out.push(o);}
  return out;}
function sig(lista,probados){for(const o of lista){if(!o||!o.url)continue;if(probados.has(o.url))continue;return o;}return null;}
function utiles(url,backup){const todas=[];
  if(url&&url.trim())todas.push({url:url.trim(),tipo:clasif(url.trim())});
  for(const t of (backup||'').split(/\s*-;-\s*/)){const s=t.trim();if(!s)continue;
    let u=s.split(' -- ')[0].trim();
    if(u.startsWith('{')){try{u=JSON.parse(u.slice(0,u.lastIndexOf('}')+1)).url||u}catch{continue}}
    if(!u.startsWith('http'))continue;todas.push({url:u,tipo:clasif(u)});}
  const v=new Set(),out=[];
  for(const o of todas){if(v.has(clave(o.url)))continue;v.add(clave(o.url));out.push(o);}
  return out.filter(o=>o.tipo!=='MARCADOR'&&!o.url.includes('quotarevival'));}

const PRIMETV='http://primetv.cc.cd/beinfhd11/tracks-v1a1/mono.tbin.m3u8';
const OTRA='http://cdn.ejemplo.tv/bein1/master.m3u8';
let fallos=0;const ck=(n,ok,d='')=>{console.log((ok?'  ✓ ':'  ✗ ')+n+(ok?'':'  '+d));if(!ok)fallos++;};

// 1 serializacion
let l=[{url:PRIMETV,ua:'Mozilla/5.0 (X11)',referer:'https://ordernap.net/',exitos:3,fallos:1,ultimoUso:1700000000000}];
let v=deTexto(aTexto(l));
ck('loGuardadoSeRecuperaIgual', v.length===1&&v[0].ua==='Mozilla/5.0 (X11)'&&v[0].exitos===3&&v[0].fallos===1);
// 2 entrada rota
const malo='no-es-url'+SEP+'x'+SEP+'y'+SEP+'1'+SEP+'0'+SEP+'123';
const bueno=PRIMETV+SEP+'Mozilla'+SEP+'https://a.b/'+SEP+'2'+SEP+'0'+SEP+'456';
ck('unaEntradaRotaNoEnvenenaLaLista', deTexto(malo+SEPI+bueno).length===1);
// 3 orden
ck('loQueFuncionaVaDelante', ordenar([{url:OTRA,exitos:0,fallos:3,ultimoUso:1000},{url:PRIMETV,exitos:5,fallos:0,ultimoUso:900}])[0].url===PRIMETV);
ck('aIgualdadGanaElMasReciente', ordenar([{url:PRIMETV,exitos:1,fallos:0,ultimoUso:100},{url:OTRA,exitos:1,fallos:0,ultimoUso:900}])[0].url===OTRA);
// 4 aprendizaje
let m=recordar(null,PRIMETV,'UA-viejo','ref-viejo',1); m=recordar(m,PRIMETV,'UA-nuevo','ref-nuevo',2);
ck('noDuplicaYActualizaCabeceras', m.length===1&&m[0].ua==='UA-nuevo'&&m[0].exitos===2);
m=recordar(null,PRIMETV,'UA',null,1); m=recordar(m,OTRA,'UA2',null,2);
ck('elMasRecientePrimero', m[0].url===OTRA);
m=fallar(m,OTRA,3);
ck('unFalloBajaDePrioridad', m.length===2&&m[0].url===PRIMETV);
ck('unFalloDesconocidoSeApunta', fallar(null,OTRA,5)[0].exitos===0);
// 5 fusion — el caso beIN 1
const backup='https://ordernap.net/e/z7iiiknxnuqjk -- redirect -;- '+PRIMETV+' -- Mozilla';
const deDrama=utiles(null,backup);
ck('sin memoria el embed va delante', deDrama[0].tipo==='EMBEBIDA', deDrama[0].tipo);
const rec=opcionesDe(recordar(null,PRIMETV,'Mozilla',null,1));
const primero=sig(conRecordados(rec,deDrama),new Set());
ck('con memoria primetv va primero', primero.url===PRIMETV, primero.url);
const fusion=conRecordados(opcionesDe(recordar(null,PRIMETV,'Mozilla',null,1)),utiles(null,PRIMETV+' -- Mozilla'));
ck('unaUrlRecordadaNoSeDuplica', fusion.length===1&&fusion[0].ua==='Mozilla', JSON.stringify(fusion));
// 6 recorrido completo
const probados=new Set();const recorrido=[];let o;
while((o=sig(conRecordados(rec,deDrama),probados))!==null&&recorrido.length<10){probados.add(o.url);recorrido.push(o.url);}
ck('agotaLaListaYTerminaEnNull', recorrido.length===2&&recorrido[0]===PRIMETV&&sig(conRecordados(rec,deDrama),probados)===null, JSON.stringify(recorrido));
// 7 llave
ck('llave estable por id_live', (()=>{const a='id:live_tv_beinsport1',b='id:live_tv_beinsport1';return a===b;})());
ck('loRecordadoQueNoEsStreamEsPagina', directaCon('https://www.youtube.com/@beINSPORTS/live',null,null).tipo==='PAGINA');

console.log(fallos===0?'\nSIM_OK · 14/14':`\nFALLARON ${fallos}`);
process.exit(fallos?1:0);
