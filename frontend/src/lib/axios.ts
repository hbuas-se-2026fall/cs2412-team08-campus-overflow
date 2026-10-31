import axios from 'axios';

/**
 * 全局 axios 实例。
 * - baseURL: /api（Vite dev 代理到后端 8080）
 * - withCredentials: 携带 Session Cookie
 * - 请求拦截器: 自动注入 X-XSRF-TOKEN（非 GET 请求）
 * - 响应拦截器: 统一把 RFC 9457 Problem Details 转成 Error
 */
export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 15_000,
});

// 请求拦截：CSRF 双提交
http.interceptors.request.use((config) => {
  if (config.method && config.method !== 'get') {
    const token = document.cookie
      .split('; ')
      .find((c) => c.startsWith('XSRF-TOKEN='))
      ?.split('=')[1];
    if (token) config.headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
  }
  return config;
});

// 响应拦截：统一错误格式
http.interceptors.response.use(
  (res) => res.data,
  (error) => {
    const status = error.response?.status ?? 0;
    const data = error.response?.data;
    const code = data?.code ?? 'SYS-000';
    const message = data?.detail ?? error.message ?? '网络异常';
    const err = new Error(message) as Error & { status: number; code: string };
    err.status = status;
    err.code = code;
    return Promise.reject(err);
  },
);
