import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="not-found">
      <h1>404</h1>
      <p className="muted">页面不存在，可能已被删除或链接有误。</p>
      <Link className="primary" to="/">回到问题列表</Link>
    </div>
  );
}
