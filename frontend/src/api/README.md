# API 层

本目录存放**基于后端 OpenAPI 契约自动生成**的 API 客户端与 TS 类型。

## 生成方式

后端启动后访问 `http://localhost:8080/v3/api-docs` 获取 OpenAPI JSON，用
[openapi-typescript](https://www.npmjs.com/package/openapi-typescript) 生成类型：

```bash
npm run gen:api
```

脚本见 `scripts/gen-api.ts`。生成产物：
- `generated.d.ts`：所有路径与请求/响应类型（请勿手改）
- `client.ts`：基于 `src/lib/axios.ts` 的实例封装，按路径导出函数

## 手写层

- `qa.ts`、`identity.ts` 等：在生成类型之上的业务语义封装，供页面调用
