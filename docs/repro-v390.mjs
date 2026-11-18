// v7.38.10: no rescatar DASH cifrado (Widevine) + host de la cadena -> ExoPlayer directo
const sirve = x => x.emite === true && x.url.startsWith('http');
const esDashCifrado = (u,http) => { if(!u) return false; const l=u.toLowerCase();
  if(!l.includes('.mpd')) return false;
  if(http===299) return true;
  if(l.includes('starzplay')||l.includes('intigral')) return true;
  return l.includes('cenc')||l.includes('_enc')||l.includes('_drm')||l.includes('/dash/enc/')||l.includes('dash_enc'); };
function podriaEmitir(x){
  if(sirve(x)) return true;
  if(!x.url||!x.url.startsWith('http')) return false;
  if(x.http===403||x.http===404||x.http===410) return false;
  if(esDashCifrado(x.url,x.http)) return false;
  return x.http>=200 && x.http<300;
}
const esHostCadena = u => { if(!u) return false; const l=u.toLowerCase();
  if(l.includes('dramalive')) return !/^https?:\/\/[^\/]+\/(p|ps)\//.test(l);
  const forma = l.includes('/hd/')||l.includes('/fk/')||l.includes('/sd/')||l.includes('/pages/')||l.includes('/mirsp/');
  return forma && l.includes('index.html'); };
const esPortal = u => { if(!u) return false; const l=u.toLowerCase();
  if(esHostCadena(l)) return false;
  return l.includes('youtube.com')||l.includes('youtu.be')||l.includes('vimeo.com')||l.includes('dailymotion.com')
    ||l.includes('twitch.tv')||l.includes('facebook.com/watch')||l.includes('shoof.')||l.includes('alkass.net/live')
    ||l.includes('aflam4you')||l.includes('dlive.sx/cast'); };

let ok=0,tot=0; const t=(n,c)=>{tot++; if(c) ok++; else console.log('  FALLA: '+n);};

const dashCifrados=[
 'https://admn-live-cdn-lb.starzplayarabia.com/out/v1/admn_tv/emarat_tv/emarat_tv_dash_enc/index.mpd',
 'https://otte.live.fly.ww.aiv-cdn.net/gru-nitro/live/clients/dash/enc/ud6bnhthpj/out/v1/2639a2f4480f4269953de466d5f46463/cenc.mpd',
 'https://otte.cache.aiv-cdn.net/bom-nitro/live/clients/dash/enc/72sjo8hygl/out/v1/3079be34d72a4985852d299a02406a0c/cenc.mpd',
];
t('los 3 DASH reales se reconocen como cifrados',
  dashCifrados.every(u=>esDashCifrado(u,200)));
t('el manifest.mpd de intigral (http 299) tambien', esDashCifrado('https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd',299));
t('un .m3u8 normal no es cifrado', !esDashCifrado('https://x/a/index.m3u8'));
t('un .mpd limpio no se marca cifrado', !esDashCifrado('https://x/limpio/manifest.mpd'));

const abu=[
 {n:1,tipo:'directa',url:'https://uselector.cdn.intigral-ott.net/ADE/ADE.isml/manifest.mpd',emite:false,http:299},
 {n:0,tipo:'directa',url:dashCifrados[0],emite:false,http:200},
 {n:2,tipo:'directa',url:'https://x/emarat_tv_hls_nd/index.m3u8',emite:false,http:403},
];
t('abudabi: ya NO se rescata el Widevine (en la 7.38.9 eran 2)', abu.filter(podriaEmitir).length===0);

const max1=[
 {n:0,tipo:'marcador',url:'https://max10.maxwordsandroidios.shop/pages/vhxivhzkeuix/index.html',emite:true,http:200},
 {n:2,tipo:'directa',url:dashCifrados[1],emite:false,http:200},
 {n:3,tipo:'directa',url:dashCifrados[2],emite:false,http:200},
 {n:5,tipo:'embed',url:'https://tinslime.net/e/xt',emite:false,http:403},
 {n:1,tipo:'embed',url:'https://ordernap.net/e/b1',emite:false,http:403},
 {n:4,tipo:'marcador',url:'https://tossequip.net/e/7i',emite:false,http:403},
];
const utilesMax = max1.filter(podriaEmitir);
t('beIN Max 1: solo la sana (las 2 Widevine ya no gastan turno)', utilesMax.length===1);
t('beIN Max 1: y es la de /pages/', utilesMax[0].url.includes('/pages/'));

t('el /pages/ de beIN Max 1 va directo a ExoPlayer', esHostCadena(max1[0].url));
t('tambien /hd/ (beIN 1 HD)', esHostCadena('https://sup7.superdatanet.cfd/hd/azmyqsojuqbm/index.html'));
t('tambien /fk/ (beIN 2 4K)', esHostCadena('https://ult11.ultraiossoftstarsfeedmoon.shop/fk/wmugpqjgfxnz/index.html'));
t('tambien /sd/ (beIN 2 SD)', esHostCadena('https://bui8.buildapiplatform.cfd/sd/cvrdszxqccuh/index.html'));
t('tambien /mirsp/ (Alkass 2)', esHostCadena('https://dat3.datahostingapiportal.monster/mirsp/mrktygbmlhes/index.html'));
t('un embed NO es host de la cadena', !esHostCadena('https://ordernap.net/e/abc'));
t('la proxada de la API NO es host de la cadena', !esHostCadena('https://dramalive-api-08th.onrender.com/p/232425/x/0.m3u8'));

t('shoof.alkass.net es portal', esPortal('https://shoof.alkass.net/live?ch=two'));
t('aflam4you es portal', esPortal('https://new.aflam4you.net/x'));
t('dlive.sx/cast es portal', esPortal('https://dlive.sx/cast/stream-91.php'));
t('un index.html de la cadena NUNCA es portal', !esPortal(max1[0].url));
t('ordernap sigue siendo embed valido', !esPortal('https://ordernap.net/e/abc'));

console.log(`V390_OK ${ok}/${tot}`);
if(ok!==tot) process.exit(1);
