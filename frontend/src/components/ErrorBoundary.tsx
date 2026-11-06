import { Component, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * 全局错误边界：捕获 React 渲染期异常，展示兜底页。
 * 运行时 fetch / 事件处理器的错误由 axios 拦截器 + react-query 处理。
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  handleReload = () => {
    this.setState({ error: null });
    window.location.href = '/';
  };

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <h2>页面出了点问题</h2>
          <p style={{ color: '#999' }}>{this.state.error.message}</p>
          <button onClick={this.handleReload}>回到首页</button>
        </div>
      );
    }
    return this.props.children;
  }
}
