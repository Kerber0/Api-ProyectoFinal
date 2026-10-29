// Inventario lexical reproducible. Una referencia no prueba uso en ejecucion;
// su ausencia no autoriza a eliminar una clase ni descarta consumidores externos.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
function javaFiles(folder) {
  return readdirSync(folder, { withFileTypes: true }).flatMap(entry => {
    const path = join(folder, entry.name);
    return entry.isDirectory() ? javaFiles(path) : entry.name.endsWith('.java') ? [path] : [];
  });
}
const paths = [...javaFiles(join(root, 'src/main/java')), ...javaFiles(join(root, 'src/test/java'))];
const files = paths.map(path => ({ path: relative(root, path).replaceAll('\\', '/'), text: readFileSync(path, 'utf8') }));
const dtoFolder = 'src/main/java/com/empresa/api/dto/';
const definitions = files.filter(file => file.path.startsWith(dtoFolder) && /public class \w+DTO\b/.test(file.text));
const dto = definitions.map(file => {
  const name = /public class (\w+DTO)\b/.exec(file.text)[1];
  const references = files.filter(other => other.path !== file.path && new RegExp(`\\b${name}\\b`).test(other.text));
  return {
    tipo: name,
    archivo: file.path,
    referenciasPrincipalesExternas: references.filter(other => other.path.startsWith('src/main/') && !other.path.startsWith(dtoFolder)).map(other => other.path).sort(),
    referenciasPruebas: references.filter(other => other.path.startsWith('src/test/')).map(other => other.path).sort(),
    referenciasDesdeOtrosDto: references.filter(other => other.path.startsWith(dtoFolder)).map(other => other.path).sort()
  };
}).sort((a, b) => a.tipo.localeCompare(b.tipo));
const inventory = {
  metodo: 'Referencias lexicales de nombres Java; no analiza reflexion ni consumidores externos.',
  totalDtoIniciales: dto.length,
  dtoConUsoPrincipalExterno: dto.filter(item => item.referenciasPrincipalesExternas.length).length,
  dtoConUsoEnPruebas: dto.filter(item => item.referenciasPruebas.length).length,
  dtoSinReferenciasJavaExternas: dto.filter(item => !item.referenciasPrincipalesExternas.length && !item.referenciasPruebas.length).length,
  dto
};
const output = join(root, 'docs/INVENTARIO_DTO.json');
const content = JSON.stringify(inventory, null, 2) + '\n';
if (process.argv.includes('--check')) {
  if (readFileSync(output, 'utf8') !== content) {
    throw new Error('docs/INVENTARIO_DTO.json no coincide con el inventario reproducible.');
  }
} else {
  writeFileSync(output, content);
}
console.log(`DTO iniciales: ${dto.length}; uso principal externo: ${inventory.dtoConUsoPrincipalExterno}; usados en pruebas: ${inventory.dtoConUsoEnPruebas}.`);
