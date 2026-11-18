// tools/chk.mjs — comprobación de sintaxis «de bolsillo» para Java, sin JDK.
// Quita cadenas, caracteres y comentarios, y verifica que los delimitadores cierran
// en orden. No es un compilador: detecta llaves/paréntesis desbalanceados y cadenas
// sin cerrar, que es el 95% de los errores de una edición con script.
import { readFileSync } from 'node:fs';

const path = process.argv[2];
if (!path) { console.error('uso: node tools/chk.mjs Fichero.java'); process.exit(2); }
const src = readFileSync(path, 'utf8');

let i = 0, n = src.length, line = 1;
const stack = [];
const PAIRS = { '}': '{', ')': '(', ']': '[' };
let inStr = false, inChar = false, inBlock = false, inLine = false;

while (i < n) {
  const c = src[i], d = src[i + 1];
  if (c === '\n') { line++; if (inLine) inLine = false; i++; continue; }
  if (inLine) { i++; continue; }
  if (inBlock) { if (c === '*' && d === '/') { inBlock = false; i += 2; } else i++; continue; }
  if (inStr) {
    if (c === '\\') { i += 2; continue; }
    if (c === '"') inStr = false;
    i++; continue;
  }
  if (inChar) {
    if (c === '\\') { i += 2; continue; }
    if (c === "'") inChar = false;
    i++; continue;
  }
  if (c === '/' && d === '/') { inLine = true; i += 2; continue; }
  if (c === '/' && d === '*') { inBlock = true; i += 2; continue; }
  if (c === '"') { inStr = true; i++; continue; }
  if (c === "'") { inChar = true; i++; continue; }
  if (c === '{' || c === '(' || c === '[') { stack.push({ c, line }); i++; continue; }
  if (c === '}' || c === ')' || c === ']') {
    const top = stack.pop();
    if (!top) { console.error(`PARSE_FAIL  ${path}  sobra '${c}' en línea ${line}`); process.exit(1); }
    if (top.c !== PAIRS[c]) {
      console.error(`PARSE_FAIL  ${path}  '${top.c}' de línea ${top.line} cerrado con '${c}' en ${line}`);
      process.exit(1);
    }
    i++; continue;
  }
  i++;
}
if (inStr) { console.error(`PARSE_FAIL  ${path}  cadena sin cerrar`); process.exit(1); }
if (inBlock) { console.error(`PARSE_FAIL  ${path}  comentario /* sin cerrar`); process.exit(1); }
if (stack.length) {
  const t = stack[stack.length - 1];
  console.error(`PARSE_FAIL  ${path}  '${t.c}' de línea ${t.line} sin cerrar`);
  process.exit(1);
}
console.log(`PARSE_OK  ${path.split('/').pop()}  (${src.length} B)`);
