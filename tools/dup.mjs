// tools/dup.mjs — busca MÉTODOS DUPLICADOS en una clase Java.
// Un duplicado de firma en la misma clase no compila, y al editar con scripts se cuela
// fácil (pasó con embedEnCurso/opcionesDeLaApi en PlayerActivity).
//
// Requiere un grupo de modificadores explícito delante del nombre
// (public|private|protected|static|final|synchronized|abstract|native|default),
// para no confundir llamadas heredadas como `runOnUiThread(` con declaraciones.
import { readFileSync } from 'node:fs';

const path = process.argv[2];
if (!path) { console.error('uso: node tools/dup.mjs Fichero.java'); process.exit(2); }
const src = readFileSync(path, 'utf8');

// Todo lo que hay entre 'class X {' y el final: nos basta con mirar todo el fichero,
// porque las clases internas también son «la misma clase» a efectos de duplicado local.
const re = /(?:^|\n)[ \t]*((?:(?:public|private|protected|static|final|synchronized|abstract|native|default|strictfp)\s+)+)(?:<[^>]+>\s+)?([A-Za-z_$][\w$<>\[\],.?\s]*?)\s+([A-Za-z_$][\w$]*)\s*\(([^)]*)\)\s*(?:throws [\w.,\s]+)?\{/g;

const vistos = new Map();
let m, dup = 0, total = 0;
while ((m = re.exec(src)) !== null) {
  const nombre = m[3];
  const params = m[4].replace(/\s+/g, ' ').trim();
  // la clave es nombre + número/forma de parámetros, que es lo que distingue sobrecargas
  const clave = `${nombre}(${params})`;
  const linea = src.slice(0, m.index).split('\n').length;
  total++;
  if (vistos.has(clave)) {
    dup++;
    console.error(`DUP  ${nombre}(${params})  líneas ${vistos.get(clave)} y ${linea}`);
  } else {
    vistos.set(clave, linea);
  }
}
if (dup > 0) { console.error(`DUP_FAIL  ${path.split('/').pop()}  ${dup} duplicado(s)`); process.exit(1); }
console.log(`NO_DUP_OK  ${path.split('/').pop()} (${total} firmas)`);
