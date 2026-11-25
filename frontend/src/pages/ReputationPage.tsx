import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import type { LedgerEntry, ReputationProfile } from '../types';

const PAGE_SIZE = 20;
const REF_LABELS: Record<string, string> = { ANSWER: '回答', QUESTION: '问题', BOUNTY: '悬赏' };

export function ReputationPage() {
  const { id } = useParams();
  const userId = Number(id);
  const { me } = useAuth();
  const [profile, setProfile] = useState<ReputationProfile | null>(null);
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loadedFor, setLoadedFor] = useState(userId);
  const [error, setError] = useState('');
  const [ledgerNote, setLedgerNote] = useState('');

  const isSelf = me?.id === userId;
  const canReadLedger = isSelf || me?.role === 'ADMIN';

  if (userId !== loadedFor) {
    setLoadedFor(userId);
    setPage(1);
  }

  useEffect(() => {
    setProfile(null);
    setError('');
    api.reputation(userId).then(setProfile).catch((e) => setError(e.message));
  }, [userId]);

  useEffect(() => {
    if (!canReadLedger) {
      setEntries([]);
      setTotal(0);
      setLedgerNote(me ? '声誉流水仅本人或管理员可见。' : '登录后可以查看自己的声誉流水。');
      return;
    }
    setLedgerNote('');
    api.ledger(userId, page)
      .then((r) => {
        setEntries(r.items);
        setTotal(r.total);
      })
      .catch((e) => {
        setEntries([]);
        setTotal(0);
        setLedgerNote(e.message);
      });
  }, [userId, page, canReadLedger, me]);

  if (error) {
    return (
      <div className="form-page">
        <h1>声誉档案</h1>
        <p className="error">{error}</p>
        <Link to="/leaderboard">返回排行榜</Link>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="form-page">
        <h1>声誉档案</h1>
        <p className="muted">加载中…</p>
      </div>
    );
  }

  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="form-page">
      <h1>{profile.displayName} 的声誉档案</h1>

      <div className="stat-grid">
        <div className="stat">
          <span className="stat-value">{profile.reputation}</span>
          <span className="stat-label">声誉</span>
        </div>
        <div className="stat">
          <span className="stat-value">{profile.availablePoints ?? '—'}</span>
          <span className="stat-label">可用悬赏积分{profile.availablePoints == null ? '（仅本人可见）' : ''}</span>
        </div>
        <div className="stat">
          <span className="stat-value">{profile.frozenPoints ?? '—'}</span>
          <span className="stat-label">冻结积分{profile.frozenPoints == null ? '（仅本人可见）' : ''}</span>
        </div>
        <div className="stat">
          <span className="stat-value">{profile.badges.length}</span>
          <span className="stat-label">徽章</span>
        </div>
      </div>

      <h2>徽章墙</h2>
      {profile.badges.length === 0 ? (
        <p className="muted">还没有获得徽章。</p>
      ) : (
        <div className="badges">
          {profile.badges.map((b) => (
            <div className="badge-card" key={b.code}>
              <span className="badge-name">{b.name}</span>
              <span className="muted">{b.description}</span>
              <span className="muted">{new Date(b.awardedAt).toLocaleDateString('zh-CN')}</span>
            </div>
          ))}
        </div>
      )}

      <h2>声誉流水</h2>
      {ledgerNote ? <p className="muted">{ledgerNote}</p> : null}
      {entries.length === 0 && !ledgerNote ? <p className="muted">暂无流水记录。</p> : null}
      {entries.length > 0 ? (
        <table className="board">
          <thead>
            <tr><th>时间</th><th>事项</th><th>变动</th><th>关联</th></tr>
          </thead>
          <tbody>
            {entries.map((e) => (
              <tr key={e.id}>
                <td>{new Date(e.createdAt).toLocaleString('zh-CN')}</td>
                <td>{e.reasonLabel}</td>
                <td className={e.delta >= 0 ? 'delta-pos' : 'delta-neg'}>{e.delta > 0 ? `+${e.delta}` : e.delta}</td>
                <td className="muted">{REF_LABELS[e.refType] ?? e.refType} #{e.refId}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {entries.length > 0 && pages > 1 ? (
        <div className="tabs pager">
          <button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button>
          <span className="muted">第 {page} / {pages} 页，共 {total} 条</span>
          <button disabled={page >= pages} onClick={() => setPage(page + 1)}>下一页</button>
        </div>
      ) : null}

      <p className="muted">
        声誉口径：回答被采纳 +15，被赞 +10，被踩 −2，赞改踩 −12；每日正向投票所得上限 200（Asia/Shanghai）。相关排名见
        <Link to="/leaderboard">排行榜</Link>。
      </p>
    </div>
  );
}
