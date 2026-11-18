// Réplica fiel de ApiDrama.urlPlaylist/urlStream/reescribir (v7.38.8)
const recortar2 = s => { if (s==null) return null; let t=s.trim(); while(t.endsWith('/')) t=t.slice(0,-1); return t===''?null:t; };
const limpio = s => { if(s==null) return null; const t=s.trim(); if(!t) return null;
  let o=''; for(const ch of t) if(/[A-Za-z0-9_.-]/.test(ch)) o+=ch; return o===''?null:o; };
function urlPlaylist(base,codigo,idLive,n){ if(n<0) return null;
  const b=recortar2(base),c=limpio(codigo),d=limpio(idLive); if(!b||!c||!d) return null;
  return `${b}/p/${c}/${d}/${n}.m3u8`; }
function urlStream(base,codigo,idLive,n){ if(n<0) return null;
  const b=recortar2(base),c=limpio(codigo),d=limpio(idLive); if(!b||!c||!d) return null;
  return `${b}/stream/${c}/${d}?n=${n}`; }
function reescribir(m3u8,base,codigo,idLive,n){
  const b=recortar2(base),c=limpio(codigo),d=limpio(idLive);
  if(!b||!c||!d||n<0) return m3u8;
  const prefijo=`${b}/ps/${c}/${d}/${n}/`;
  const out=[];
  for(const linea of m3u8.split('\n')){
    const l=linea.replace(/\r/g,'').trim();
    if(!l||l.startsWith('#')) out.push(reescribirUri(l,prefijo));
    else if(l.startsWith('http')) out.push(l);
    else { const rel=l.startsWith('/')?l.slice(1):l;
      out.push(rel.startsWith('ps/')? `${b}/${rel}` : `${prefijo}${rel}`); }
  }
  return out.join('\n');
}
function reescribirUri(linea,prefijo){
  if(!linea.startsWith('#')) return linea;
  const i=linea.indexOf('URI="'); if(i<0) return linea;
  const ini=i+5, fin=linea.indexOf('"',ini); if(fin<0) return linea;
  const uri=linea.slice(ini,fin); if(uri.startsWith('http')) return linea;
  const rel=uri.startsWith('/')?uri.slice(1):uri;
  const abs=rel.startsWith('ps/') ? prefijo.slice(0,prefijo.indexOf('/ps/')+1)+rel : prefijo+rel;
  return linea.slice(0,ini)+abs+linea.slice(fin);
}

// ---- pruebas ----
let ok=0, tot=0;
const t=(nom,cond)=>{ tot++; if(cond){ok++;} else console.log('  FALLA: '+nom); };
const BASE='https://dramalive-api-08th.onrender.com/';
const C='232425', D='live_tv_beinsport1';

t('playlist n=0', urlPlaylist(BASE,C,D,0)==='https://dramalive-api-08th.onrender.com/p/232425/live_tv_beinsport1/0.m3u8');
t('playlist recorta / final', urlPlaylist(BASE+'//',C,D,3).endsWith('/3.m3u8'));
t('playlist n=-1 -> null', urlPlaylist(BASE,C,D,-1)===null);
t('playlist sin base -> null', urlPlaylist(null,C,D,0)===null);
t('playlist sin id -> null', urlPlaylist(BASE,C,'',0)===null);
t('limpia espacios/slash en id', urlPlaylist(BASE,C,' live_tv_x/ ',1)==='https://dramalive-api-08th.onrender.com/p/232425/live_tv_x/1.m3u8');
t('stream con ?n=', urlStream(BASE,C,D,2)==='https://dramalive-api-08th.onrender.com/stream/232425/live_tv_beinsport1?n=2');

const REAL = `#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:1794925067
#EXTINF:10.000000,
/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925067.js
#EXTINF:10.000000,
/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925068.js`;
const r=reescribir(REAL,BASE,C,D,0);
t('conserva #EXTM3U', r.startsWith('#EXTM3U'));
t('segmento /ps/ -> absoluto', r.includes('https://dramalive-api-08th.onrender.com/ps/232425/live_tv_beinsport1/0/k9x_1794919732-1794925067.js'));
t('no deja rutas /ps/ sueltas', !/^\/ps\//m.test(r));
t('2 segmentos', r.split('\n').filter(l=>l.startsWith('https://')).length===2);
t('TARGETDURATION intacto', r.includes('#EXT-X-TARGETDURATION:10'));

const REL=`#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="/ps/232425/live_tv_beinsport1/0/k.key"
#EXTINF:10,
abc.js
#EXTINF:10,
https://otro.cdn/ya/absoluto.ts`;
const r2=reescribir(REL,BASE,C,D,0);
t('clave reescrita', r2.includes('URI="https://dramalive-api-08th.onrender.com/ps/232425/live_tv_beinsport1/0/k.key"'));
t('segmento relativo desnudo', r2.includes('https://dramalive-api-08th.onrender.com/ps/232425/live_tv_beinsport1/0/abc.js'));
t('absoluto se respeta', r2.includes('https://otro.cdn/ya/absoluto.ts'));
t('sin base devuelve igual', reescribir(REL,null,C,D,0)===REL);
t('n negativo devuelve igual', reescribir(REL,BASE,C,D,-1)===REL);
t('basura no explota', reescribir('','',null,'',0)==='');
t('no altera URI absoluta', reescribir('#EXT-X-KEY:METHOD=AES-128,URI="https://k/x"',BASE,C,D,0)
  ==='#EXT-X-KEY:METHOD=AES-128,URI="https://k/x"');

console.log(`PROXY_OK ${ok}/${tot}`);
if(ok!==tot) process.exit(1);
