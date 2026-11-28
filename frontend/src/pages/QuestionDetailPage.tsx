import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, SkeletonList } from '../components/States';
import { MarkdownPreview } from '../components/MarkdownPreview';
import { highlightCode } from '../lib/highlight';
import type { QuestionDetail, RelatedQuestion } from '../types';

export function QuestionDetailPage() {
  const { id } = useParams();
  const questionId = Number(id);
  const { me } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState<QuestionDetail | null>(null);
  const [related, setRelated] = useState<RelatedQuestion[]>([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [editTab, setEditTab] = useState<'edit' | 'preview'>('edit');
  const [answerTab, setAnswerTab] = useState<'edit' | 'preview'>('edit');
  const [savingAnswer, setSavingAnswer] = useState(false);
  const [savingQuestion, setSavingQuestion] = useState(false);
  const [editingQuestion, setEditingQuestion] = useState(false);
  const [qTitle, setQTitle] = useState('');
  const [qBody, setQBody] = useState('');
  const [qTags, setQTags] = useState('');
  const [qTab, setQTab] = useState<'edit' | 'preview'>('edit');
  const bodyRef = useRef<HTMLDivElement>(null);
  const loadSeq = useRef(0);

  const load = useCallback(() => {
    const seq = ++loadSeq.current;
    setLoading(true);
    api.question(questionId)
      .then((r) => {
        if (seq !== loadSeq.current) return;
        setQ(r);
        setError('');
      })
      .catch((e) => {
        if (seq === loadSeq.current) setError(e.message);
      })
      .finally(() => {
        if (seq === loadSeq.current) setLoading(false);
      });
  }, [questionId]);

  useEffect(() => {
    load();
  }, [load, tick]);

  useEffect(() => {
    let stale = false;
    api.related(questionId)
      .then((r) => { if (!stale) setRelated(r); })
      .catch(() => undefined);
    return () => { stale = true; };
  }, [questionId, tick]);

  useEffect(() => {
    highlightCode(bodyRef.current);
  }, [q]);

  const act = (fn: () => Promise<unknown>) =>
    fn().then(() => { setError(''); load(); }).catch((e) => setError(e.message));

  if (!q) {
    return (
      <div className="layout">
        <article>
          {loading ? <SkeletonList rows={4} /> : <ErrorBox message={error} onRetry={() => setTick((t) => t + 1)} />}
        </article>
      </div>
    );
  }

  const canManage = q.permissions.canManage;

  const saveAnswer = async (answerId: number) => {
    setSavingAnswer(true);
    try {
      await api.editAnswer(answerId, editDraft);
      setEditingId(null);
      setError('');
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSavingAnswer(false);
    }
  };

  const saveQuestion = async () => {
    const tagList = qTags.split(/[,，\s]+/).filter(Boolean);
    if (qTitle.trim().length < 10 || qTitle.trim().length > 150) {
      setError('标题需 10–150 字');
      return;
    }
    if (tagList.length < 1 || tagList.length > 5) {
      setError('标签需 1–5 个');
      return;
    }
    setSavingQuestion(true);
    try {
      await api.editQuestion(q.id, {
        title: qTitle.trim(),
        body: qBody,
        tags: tagList,
      });
      setEditingQuestion(false);
      setError('');
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSavingQuestion(false);
    }
  };

  const removeQuestion = () => {
    if (!confirm('删除这个问题？此操作不可撤销。')) return;
    api.deleteQuestion(q.id)
      .then(() => navigate('/'))
      .catch((e) => setError(e.message));
  };

  return (
    <div className="layout">
      <article ref={bodyRef}>
        {editingQuestion ? (
          <section className="answer-form">
            <h3>编辑问题</h3>
            <label>标题
              <input value={qTitle} onChange={(e) => setQTitle(e.target.value)} />
            </label>
            <div className="tabs">
              <button className={qTab === 'edit' ? 'active' : ''} onClick={() => setQTab('edit')}>编辑</button>
              <button className={qTab === 'preview' ? 'active' : ''} onClick={() => setQTab('preview')}>预览</button>
            </div>
            {qTab === 'edit' ? (
              <textarea rows={12} value={qBody} onChange={(e) => setQBody(e.target.value)} />
            ) : (
              <MarkdownPreview text={qBody} />
            )}
            <label>标签
              <input value={qTags} onChange={(e) => setQTags(e.target.value)} placeholder="用空格或逗号分隔" />
            </label>
            <div className="actions">
              <button className="primary" disabled={savingQuestion} onClick={saveQuestion}>
                {savingQuestion ? '保存中…' : '保存'}
              </button>
              <button onClick={() => setEditingQuestion(false)}>取消</button>
            </div>
          </section>
        ) : (
          <>
        <h1>{q.title}</h1>
        <p className="muted">
          {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览
          {q.course && <> · 课程 {q.course.name}</>}
        </p>

        <div className="post">
          <div className="votebox">
            <button disabled={!me} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === 1 ? 0 : 1))}>▲</button>
            <b>{q.score}</b>
            <button disabled={!me} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === -1 ? 0 : -1))}>▼</button>
          </div>
          <div className="body">
            <div dangerouslySetInnerHTML={{ __html: q.bodyHtml }} />
            <div className="tagrow">{q.tags.map((t) => <span key={t} className="tag">{t}</span>)}</div>
            <CommentList comments={q.comments} targetType="QUESTION" targetId={q.id} onDone={load} canComment={!!me} />
            <div className="actions">
              {canManage && (
                <button className="link" onClick={() => {
                  const points = Number(prompt('悬赏积分（10–500）', '50'));
                  if (points) act(() => api.bounty(q.id, points, 7));
                }}>发起悬赏
                </button>
              )}
              {q.permissions.canEdit && !editingQuestion && (
                <button className="link" onClick={() => {
                  setQTitle(q.title);
                  setQBody(q.body);
                  setQTags(q.tags.join(' '));
                  setQTab('edit');
                  setEditingQuestion(true);
                }}>编辑问题
                </button>
              )}
              {q.permissions.canDelete && <button className="link danger" onClick={removeQuestion}>删除问题</button>}
            </div>
          </div>
        </div>

          </>
        )}

        <h2>{q.answerCount} 个回答</h2>
        {q.answers.map((a) => (
          <div className={`post ${q.acceptedAnswerId === a.id ? 'is-accepted' : ''}`} key={a.id} id={`answer-${a.id}`}>
            <div className="votebox">
              <button disabled={!me} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === 1 ? 0 : 1))}>▲</button>
              <b>{a.score}</b>
              <button disabled={!me} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === -1 ? 0 : -1))}>▼</button>
              {q.acceptedAnswerId === a.id && <span className="check-mark" title="已采纳">✔</span>}
            </div>
            <div className="body">
              {editingId === a.id ? (
                <div className="edit-answer">
                  <div className="tabs">
                    <button className={editTab === 'edit' ? 'active' : ''} onClick={() => setEditTab('edit')}>编辑</button>
                    <button className={editTab === 'preview' ? 'active' : ''} onClick={() => setEditTab('preview')}>预览</button>
                  </div>
                  {editTab === 'edit' ? (
                    <textarea rows={10} value={editDraft} onChange={(e) => setEditDraft(e.target.value)} />
                  ) : (
                    <MarkdownPreview text={editDraft} />
                  )}
                  <div className="actions">
                    <button className="primary" disabled={savingAnswer} onClick={() => saveAnswer(a.id)}>
                      {savingAnswer ? '保存中…' : '保存'}
                    </button>
                    <button onClick={() => { setEditingId(null); setEditTab('edit'); }}>取消</button>
                  </div>
                </div>
              ) : (
                <div dangerouslySetInnerHTML={{ __html: a.bodyHtml }} />
              )}
              <p className="muted">
                {a.author.displayName}
                {a.endorsedBy && <span className="endorsed">教师认证 · {a.endorsedBy.displayName}</span>}
                · {new Date(a.createdAt).toLocaleString('zh-CN')}
              </p>
              <div className="actions">
                {q.permissions.canAccept && !q.acceptedAnswerId &&
                  <button onClick={() => act(() => api.accept(q.id, a.id))}>采纳此回答</button>}
                {canManage && !a.endorsedBy && <button onClick={() => act(() => api.endorse(a.id))}>教师认证</button>}
                {a.canEdit && editingId !== a.id && (
                  <button className="link" onClick={() => { setEditingId(a.id); setEditDraft(a.body); setEditTab('edit'); }}>
                    编辑
                  </button>
                )}
                {a.canEdit && (
                  <button className="link danger" onClick={() => {
                    if (confirm('删除这条回答？')) act(() => api.deleteAnswer(a.id));
                  }}>删除
                  </button>
                )}
              </div>
              <CommentList comments={a.comments} targetType="ANSWER" targetId={a.id} onDone={load} canComment={!!me} />
            </div>
          </div>
        ))}

        {me ? (
          <section className="answer-form">
            <h3>写回答</h3>
            <div className="tabs">
              <button className={answerTab === 'edit' ? 'active' : ''} onClick={() => setAnswerTab('edit')}>编辑</button>
              <button className={answerTab === 'preview' ? 'active' : ''} onClick={() => setAnswerTab('preview')}>预览</button>
            </div>
            {answerTab === 'edit' ? (
              <textarea rows={8} value={draft} onChange={(e) => setDraft(e.target.value)}
                        placeholder="支持 Markdown，代码请使用 ``` 包裹" />
            ) : (
              <MarkdownPreview text={draft} emptyHint="还没有输入内容" />
            )}
            <button className="primary" onClick={() => act(async () => {
              await api.answer(q.id, draft);
              setDraft('');
            })}>提交回答
            </button>
          </section>
        ) : <p className="muted"><Link to="/login">登录</Link>后可以回答与投票。</p>}
        <ErrorBox message={error} onRetry={load} />
      </article>

      <aside>
        <h3>相关问题</h3>
        <ul className="plain">
          {related.map((r) => (
            <li key={r.id}>
              <Link to={`/questions/${r.id}`}>{r.title}</Link>
              <span className="muted sim">
                · {r.answerCount} 回答 · 相似度 {(r.similarity * 100).toFixed(0)}%
                {r.accepted && <span className="check-mark"> ✔</span>}
              </span>
            </li>
          ))}
          {related.length === 0 && <li className="muted">暂无</li>}
        </ul>
      </aside>
    </div>
  );
}

function CommentList({ comments, targetType, targetId, onDone, canComment }: {
  comments: { id: number; author: { displayName: string }; body: string; createdAt: string }[];
  targetType: 'QUESTION' | 'ANSWER';
  targetId: number;
  onDone: () => void;
  canComment: boolean;
}) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  return (
    <div className="comments">
      {comments.map((c) => (
        <div className="comment" key={c.id}>
          <span><MentionText text={c.body} /></span>
          <span className="muted"> — {c.author.displayName}</span>
        </div>
      ))}
      {canComment && (open ? (
        <div className="comment-form">
          <input value={text} onChange={(e) => setText(e.target.value)} placeholder="评论，可用 @昵称 提醒他人" />
          <button onClick={() => api.comment(targetType, targetId, text).then(() => { setText(''); setOpen(false); onDone(); })}>
            发表
          </button>
        </div>
      ) : <button className="link" onClick={() => setOpen(true)}>添加评论</button>)}
    </div>
  );
}

const MENTION_PATTERN = /(@[\w\u4e00-\u9fa5]{2,20})/g;

function MentionText({ text }: { text: string }) {
  return (
    <>
      {text.split(MENTION_PATTERN).map((part, index) =>
        part.startsWith('@')
          ? <span className="mention" key={index}>{part}</span>
          : <span key={index}>{part}</span>)}
    </>
  );
}
