import { useCallback, useEffect, useState } from 'react';
import { api } from '../api';
import { ErrorBox, SkeletonList } from '../components/States';
import type { AdminUser, Page } from '../types';

const ROLES = ['STUDENT', 'TA', 'TEACHER', 'ADMIN'];
const PAGE_SIZE = 20;

const emptyForm = { username: '', displayName: '', email: '', password: '', role: 'TA', college: '' };

export function AdminUsersPage() {
  const [verified, setVerified] = useState(false);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Page<AdminUser> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [openCreate, setOpenCreate] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState('');
  const [creating, setCreating] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    api.adminUsers(verified, page)
      .then((r) => { setData(r); setError(''); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [verified, page]);

  useEffect(load, [load]);

  const run = (id: number, fn: () => Promise<unknown>, ok: string) => {
    setBusyId(id);
    setNotice('');
    fn()
      .then(() => { setNotice(ok); load(); })
      .catch((e) => setError(e.message))
      .finally(() => setBusyId(null));
  };

  const totalPages = data ? Math.max(1, Math.ceil(data.total / PAGE_SIZE)) : 1;

  const submitCreate = async () => {
    setCreating(true);
    setFormError('');
    try {
      await api.createStaff({
        username: form.username.trim(),
        displayName: form.displayName.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
        college: form.college.trim() || undefined,
      });
      setForm(emptyForm);
      setOpenCreate(false);
      setNotice('教职工账号已创建');
      load();
    } catch (e) {
      setFormError((e as Error).message);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="layout">
      <section>
        <h1>用户管理</h1>
        <div className="toolbar">
          <div className="tabs">
            <button className={!verified ? 'active' : ''} onClick={() => { setVerified(false); setPage(1); }}>
              待认证
            </button>
            <button className={verified ? 'active' : ''} onClick={() => { setVerified(true); setPage(1); }}>
              已认证
            </button>
          </div>
          <div className="spacer" />
          <button className="primary" onClick={() => setOpenCreate((v) => !v)}>
            {openCreate ? '收起' : '创建教职工账号'}
          </button>
        </div>

        {notice && <p className="notice">{notice}</p>}
        <ErrorBox message={error} onRetry={load} />

        {openCreate && (
          <div className="panel">
            <h3>创建教职工账号</h3>
            <div className="grid-form">
              <label>账号
                <input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
              </label>
              <label>姓名
                <input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
              </label>
              <label>邮箱
                <input value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </label>
              <label>初始密码
                <input type="password" value={form.password}
                       onChange={(e) => setForm({ ...form, password: e.target.value })} />
              </label>
              <label>角色
                <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                  {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                </select>
              </label>
              <label>学院
                <input value={form.college} onChange={(e) => setForm({ ...form, college: e.target.value })} />
              </label>
            </div>
            {formError && <p className="error">{formError}</p>}
            <div className="actions">
              <button className="primary" disabled={creating} onClick={submitCreate}>
                {creating ? '创建中…' : '创建'}
              </button>
            </div>
          </div>
        )}

        {loading && <SkeletonList rows={5} />}

        {!loading && data && (
          <>
            <table className="board">
              <thead>
                <tr>
                  <th>账号</th><th>姓名</th><th>角色</th><th>认证</th><th>学院 / 班级</th><th>注册时间</th><th>操作</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((u) => (
                  <tr key={u.id}>
                    <td>{u.maskedUsername}</td>
                    <td>{u.displayName}</td>
                    <td>
                      <select value={u.role} disabled={busyId === u.id}
                              onChange={(e) => run(u.id, () => api.changeUserRole(u.id, e.target.value), '角色已更新')}>
                        {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                      </select>
                    </td>
                    <td>{u.verified ? <span className="verified">已认证</span> : <span className="muted">未认证</span>}</td>
                    <td>{[u.college, u.className].filter(Boolean).join(' / ') || '—'}</td>
                    <td>{new Date(u.createdAt).toLocaleDateString('zh-CN')}</td>
                    <td>
                      <div className="row-actions">
                        {!u.verified && (
                          <button disabled={busyId === u.id}
                                  onClick={() => run(u.id, () => api.verifyUser(u.id), `${u.displayName} 已通过认证`)}>
                            通过认证
                          </button>
                        )}
                        <button className="danger" disabled={busyId === u.id}
                                onClick={() => {
                                  if (confirm(`禁用 ${u.displayName}？其现有会话会立即失效。`)) {
                                    run(u.id, () => api.disableUser(u.id), `${u.displayName} 已禁用`);
                                  }
                                }}>
                          禁用
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {data.items.length === 0 && <p className="muted">没有符合条件的用户。</p>}
            <div className="pager">
              <button disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>上一页</button>
              <span className="muted">{page} / {totalPages}，共 {data.total} 人</span>
              <button disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)}>下一页</button>
            </div>
          </>
        )}
      </section>

      <aside>
        <h3>说明</h3>
        <p className="muted">待认证列表用于完成校园身份认证；禁用账号会同时清理该用户的登录会话。</p>
      </aside>
    </div>
  );
}
