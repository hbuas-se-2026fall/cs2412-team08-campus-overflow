import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';

export function ProfilePage() {
  const { me, refresh } = useAuth();
  const [displayName, setDisplayName] = useState(me?.displayName ?? '');
  const [email, setEmail] = useState(me?.email ?? '');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);

  if (!me) return null;

  const save = async () => {
    setSaving(true);
    setError('');
    setNotice('');
    try {
      await api.updateProfile({ displayName: displayName.trim(), email: email.trim() });
      await refresh();
      setNotice('资料已保存');
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="form-page">
      <h1>个人资料</h1>
      <label>账号
        <input value={me.username} readOnly className="readonly" />
      </label>
      <label>昵称（2–20 字，站内展示用）
        <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
      </label>
      <label>邮箱
        <input value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <div className="profile-meta">
        <span>角色：<b>{me.role}</b></span>
        <span>身份：{me.verified ? <b className="verified">已验证</b> : <b className="muted">未验证</b>}</span>
        {me.college && <span>学院：{me.college}</span>}
        {me.className && <span>班级：{me.className}</span>}
      </div>
      {error && <p className="error">{error}</p>}
      {notice && <p className="notice">{notice}</p>}
      <div className="actions">
        <button className="primary" disabled={saving} onClick={save}>{saving ? '保存中…' : '保存'}</button>
      </div>
    </div>
  );
}
