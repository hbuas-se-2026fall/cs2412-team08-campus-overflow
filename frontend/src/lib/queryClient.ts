import { QueryClient } from '@tanstack/react-query';

/**
 * 全局 QueryClient 配置。
 * - 失败重试 1 次（避免表单提交类 mutation 重试）
 * - 后台重新验证 30 秒
 * - 缓存 5 分钟
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});
