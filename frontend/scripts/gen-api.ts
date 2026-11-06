/**
 * OpenAPI 类型生成脚本。
 * 用法：npm run gen:api
 * 后端启动后，从 /v3/api-docs 拉取 schema，生成 src/api/generated.d.ts
 */
import openapiTS, { astToString } from 'openapi-typescript';
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const API_DOCS = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const OUT = resolve('src/api/generated.d.ts');

const ast = await openapiTS(API_DOCS);
const contents = astToString(ast);
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, contents);
console.log('Generated types at', OUT);
