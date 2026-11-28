# 多阶段构建：node 编译静态资源 → nginx 只托管产物
FROM node:20-alpine AS build
ARG NPM_REGISTRY=https://registry.npmmirror.com
WORKDIR /src
# 先装依赖（有 lockfile，用 npm ci 保证可复现），再拷源码
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --registry="${NPM_REGISTRY}" --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
