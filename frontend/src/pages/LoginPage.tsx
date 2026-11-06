import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [form, setForm] = useState({
    username: '',
    password: '',
    displayName: '',
    email: '',
    college: '',
    className: '',
  });
  const [error, setError] = useState('');
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [k]: e.target.value });

  const validate = (): string => {
    if (mode === 'register') {
      if (form.username.length < 4 || form.username.length > 20) return '学号/工号需 4-20 个字符';
      if (form.password.length < 8) return '密码至少 8 位';
      if (form.displayName.length < 2 || form.displayName.length > 20) return '昵称需 2-20 个字符';
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) return '邮箱格式不正确';
    } else {
      if (!form.username) return '请输入学号/工号';
      if (!form.password) return '请输入密码';
    }
    return '';
  };

  const submit = async () => {
    const v = validate();
    if (v) {
      setError(v);
      return;
    }
    try {
      if (mode === 'register') {
        await api.register(form);
      }
      await login(form.username, form.password);
      navigate('/');
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <div className="form-page narrow">
      <div className="tabs">
        <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
          登录
        </button>
        <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
          注册
        </button>
      </div>
      <label>
        学号 / 工号
        <input
          placeholder={mode === 'register' ? '4-20 个字符' : ''}
          value={form.username}
          onChange={set('username')}
        />
      </label>
      <label>
        密码
        <input
          type="password"
          placeholder={mode === 'register' ? '至少 8 位' : ''}
          value={form.password}
          onChange={set('password')}
        />
      </label>
      {mode === 'register' && (
        <>
          <label>
            昵称
            <input
              placeholder="2-20 个字符"
              value={form.displayName}
              onChange={set('displayName')}
            />
          </label>
          <label>
            校园邮箱
            <input placeholder="you@school.edu.cn" value={form.email} onChange={set('email')} />
          </label>
          <label>
            学院
            <input placeholder="选填" value={form.college} onChange={set('college')} />
          </label>
          <label>
            班级
            <input placeholder="选填" value={form.className} onChange={set('className')} />
          </label>
        </>
      )}
      {error && <p className="error">{error}</p>}
      <button className="primary" onClick={submit}>
        {mode === 'login' ? '登录' : '注册并登录'}
      </button>
    </div>
  );
}
