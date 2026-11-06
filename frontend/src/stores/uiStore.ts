import { create } from 'zustand';

/**
 * 全局 UI 状态（非服务端数据）：侧边栏、主题、全局 loading 等。
 * 服务端数据（问题列表、详情、通知）一律走 react-query，不放这里。
 */
interface UIState {
  /** 全局 loading（页面级用 react-query 的 isLoading，这里只放请求级） */
  globalLoading: boolean;
  setGlobalLoading: (v: boolean) => void;
  /** 通知未读数（由 Header 轮询写入，跨组件读取） */
  unreadCount: number;
  setUnreadCount: (n: number) => void;
}

export const useUIStore = create<UIState>((set) => ({
  globalLoading: false,
  setGlobalLoading: (v) => set({ globalLoading: v }),
  unreadCount: 0,
  setUnreadCount: (n) => set({ unreadCount: n }),
}));
