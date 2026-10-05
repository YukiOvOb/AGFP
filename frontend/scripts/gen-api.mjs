import { writeFile, readFile } from 'node:fs/promises';
import openapiTS, { astToString } from 'openapi-typescript';
const source = process.env.OPENAPI_URL ?? 'http://127.0.0.1:8081/api/v1/openapi.json';
const schema = process.env.OPENAPI_FILE
  ? JSON.parse(await readFile(process.env.OPENAPI_FILE, 'utf8'))
  : await (async () => {
      const response = await fetch(source);
      if (!response.ok) throw new Error(`OpenAPI endpoint returned ${response.status}`);
      return response.json();
    })();
const output = astToString(await openapiTS(schema));
const destination = new URL('../src/api/schema.d.ts', import.meta.url);
if (process.argv.includes('--check')) {
  if ((await readFile(destination, 'utf8')) !== output)
    throw new Error('API types are stale. Run pnpm gen:api and commit schema.d.ts.');
} else {
  await writeFile(destination, output);
}
