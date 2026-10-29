import { existsSync, readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
if (process.env.DORNA_TEST_DB_URL || process.env.MYSQL_PWD || process.env.DORNA_CI_MYSQL_EXE) {
  throw new Error('CI debe utilizar el PostgreSQL desechable de Testcontainers.');
}
const required = [
  'src/main/resources/db/base de datos.sql',
  'src/main/resources/db/base de datos de prueba.sql'
];
for (const relative of required) {
  if (!existsSync(resolve(root, relative))) throw new Error(`Falta recurso requerido: ${relative}`);
}
const pom = readFileSync(resolve(root, 'pom.xml'), 'utf8');
if (!pom.includes('<artifactId>postgresql</artifactId>')) throw new Error('Falta el controlador PostgreSQL.');
const integration = readFileSync(resolve(root, 'src/test/java/com/empresa/api/PostgreSqlIntegrationTest.java'), 'utf8');
for (const requiredText of ['db/base de datos.sql', 'db/base de datos de prueba.sql', 'DORNA_TEST_DB_DISPOSABLE']) {
  if (!integration.includes(requiredText)) throw new Error(`Inicialización de pruebas incompleta: ${requiredText}`);
}
if (process.argv.includes('--check')) process.exit(0);
const windows = process.platform === 'win32';
const result = spawnSync(windows ? 'powershell.exe' : 'bash', windows
  ? ['-NoProfile', '-Command', '& ./mvnw.cmd -B clean verify; exit $LASTEXITCODE']
  : ['mvnw', '-B', 'clean', 'verify'], { cwd: root, stdio: 'inherit', env: process.env });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
