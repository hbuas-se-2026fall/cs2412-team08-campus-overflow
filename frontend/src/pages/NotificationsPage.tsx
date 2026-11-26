import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import type { Notification } from '../types';

export function NotificationsPage() {
  const [items, setItems] = useState<Notification[]>([]);
  const [unread, setUnread] = useState(0);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [size, setSize] = useState(20);

  const load = useCallback(() => {
    api.notifications(page).then((p) => { setItems(p.items); setTotal(p.total); setSize(p.size); }).catch(() => undefined);
    api.unreadCount().then((r) => setUnread(r.count)).catch(() => undefined);
  }, [page]);
  useEffect(() => { load(); }, [load]);

  const pages = Math.max(1, Math.ceil(total / size));

  /** 单条已读：后端 POST /v1/notifications/{id}/read，重复标记是幂等的。 */
  const markRead = (id: number) => api.markRead(id).then(load).catch(() => undefined);

  /** 点标题跳转前先标已读，避免回来还是未读。 */
  const openItem = (n: Notification) => {
    if (!n.read) markRead(n.id);
  };

  return (
    <div className="form-page">
      <h1>通知 {unread > 0 && <span className="badge">{unread}</span>}</h1>
      <button onClick={() => api.readAll().then(load)}>全部标记为已读</button>
      <ul className="plain">
        {items.map((n) => (
          <li key={n.id} className={`notif-item ${n.read ? 'muted' : 'unread'}`}>
            {!n.read && <span className="dot" title="未读" />}
            <Link to={n.link} onClick={() => openItem(n)}>{n.title}</Link>
            <span className="muted"> · {new Date(n.createdAt).toLocaleString('zh-CN')}</span>
            {!n.read && (
              <button className="link mark-read" onClick={() => markRead(n.id)}>标记已读</button>
            )}
          </li>
        ))}
        {items.length === 0 && <li className="muted">暂时没有通知。</li>}
      </ul>
      {pages > 1 && (
        <div className="tabs pager">
          <button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
          <span className="muted">第 {page} / {pages} 页 · 共 {total} 条</span>
          <button disabled={page >= pages} onClick={() => setPage(page + 1)}>下一页</button>
        </div>
      )}
    </div>
  );
}
