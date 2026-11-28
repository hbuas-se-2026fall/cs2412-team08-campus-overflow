import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, SkeletonList } from '../components/States';
import type { Page, QuestionSummary, SearchResult } from '../types';

export function QuestionListPage() {
  const { me } = useAuth();
  const [data, setData] = useState<Page<QuestionSummary | SearchResult> | null>(null);
  const [tags, setTags] = useState<{ name: string; questionCount: number }[]>([]);
  const [keyword, setKeyword] = useState('');
  const [activeTag, setActiveTag] = useState<string | undefined>();
  const [sort, setSort] = useState('newest');
  const [unanswered, setUnanswered] = useState(false);
  const [bounty, setBounty] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    api.popularTags().then(setTags).catch(() => undefined);
  }, []);

  useEffect(() => {
    const searching = keyword.trim() !== '' || activeTag || unanswered || bounty || sort !== 'newest';
    const promise = searching
      ? api.search(keyword.trim(), { tag: activeTag, sort, unanswered, bounty, page })
      : api.questions(page);
    let stale = false;
    setLoading(true);
    promise
      .then((r) => { if (stale) return; setData(r); setError(''); })
      .catch((e) => { if (!stale) setError(e.message); })
      .finally(() => { if (!stale) setLoading(false); });
    return () => { stale = true; };
  }, [keyword, activeTag, sort, unanswered, bounty, page, tick]);

  const totalPages = data ? Math.max(1, Math.ceil(data.total / (data.size || 20))) : 1;
  const switchPage = (next: number) => setPage(next);

  return (
    <div className="layout">
      <section>
        <div className="toolbar">
          <input placeholder="搜索问题（支持中文全文检索）" value={keyword}
                 onChange={(e) => { setKeyword(e.target.value); setPage(1); }} />
          <select value={sort} onChange={(e) => { setSort(e.target.value); setPage(1); }}>
            <option value="newest">最新</option>
            <option value="active">最近活跃</option>
            <option value="hot">得分最高</option>
            <option value="relevance">相关度</option>
          </select>
          <label className="check">
            <input type="checkbox" checked={unanswered} onChange={(e) => { setUnanswered(e.target.checked); setPage(1); }} />
            仅看未回答
          </label>
          <label className="check">
            <input type="checkbox" checked={bounty} onChange={(e) => { setBounty(e.target.checked); setPage(1); }} />
            仅看悬赏
          </label>
          {me && <Link className="primary" to="/ask">我要提问</Link>}
        </div>

        {activeTag && (
          <p className="muted">
            标签筛选：<b>{activeTag}</b> <button onClick={() => { setActiveTag(undefined); setPage(1); }}>清除</button>
          </p>
        )}
        <ErrorBox message={error} onRetry={() => setTick((t) => t + 1)} />

        {loading ? <SkeletonList rows={5} /> : (
        <ul className="qlist">
          {data?.items.map((q) => (
            <li key={q.id}>
              <div className="stats">
                <span><b>{q.score}</b> 票</span>
                <span className={q.accepted ? 'accepted' : ''}><b>{q.answerCount}</b> 回答</span>
                {'viewCount' in q && q.viewCount !== undefined && <span>{q.viewCount} 浏览</span>}
              </div>
              <div className="qmain">
                <Link className="qtitle" to={`/questions/${q.id}`}>{q.title}</Link>
                <p className="excerpt">{q.excerpt}</p>
                <div className="tagrow">
                  {'bountyOpen' in q && q.bountyOpen && <span className="bounty">悬赏</span>}
                  {q.tags.map((t) => <button key={t} className="tag" onClick={() => { setActiveTag(t); setPage(1); }}>{t}</button>)}
                  <span className="muted">
                    {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')}
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ul>
        )}
        {!loading && data && data.items.length === 0 && <p className="muted">没有匹配的问题，换个关键词试试。</p>}
        {!loading && data && data.total > 0 && (
          <div className="pager">
            <button disabled={page <= 1} onClick={() => switchPage(page - 1)}>上一页</button>
            <span className="muted">{page} / {totalPages}，共 {data.total} 条</span>
            <button disabled={page >= totalPages} onClick={() => switchPage(page + 1)}>下一页</button>
          </div>
        )}
      </section>

      <aside>
        <h3>热门标签</h3>
        <div className="tagrow">
          {tags.map((t) => (
            <button key={t.name} className="tag" onClick={() => { setActiveTag(t.name); setPage(1); }}>
              {t.name}<span className="count">{t.questionCount}</span>
            </button>
          ))}
        </div>
      </aside>
    </div>
  );
}
