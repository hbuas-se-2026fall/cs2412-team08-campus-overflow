import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, SkeletonList } from '../components/States';
import type { Course, CourseMember } from '../types';

export function CoursesPage() {
  const { me, refresh } = useAuth();
  const [mine, setMine] = useState<Course[]>([]);
  const [all, setAll] = useState<Course[]>([]);
  const [selected, setSelected] = useState<Course | null>(null);
  const [members, setMembers] = useState<CourseMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [membersLoading, setMembersLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    const minePromise = me ? api.myCourses() : Promise.resolve([]);
    Promise.all([minePromise, api.courses()])
      .then(([my, every]) => { setMine(my); setAll(every); setError(''); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [me]);

  useEffect(load, [load]);

  useEffect(() => {
    if (!selected) { setMembers([]); return; }
    let alive = true;
    setMembers([]);
    setMembersLoading(true);
    api.courseMembers(selected.id)
      .then((r) => { if (alive) { setMembers(r); setError(''); } })
      .catch((e) => { if (alive) setError(e.message); })
      .finally(() => { if (alive) setMembersLoading(false); });
    return () => { alive = false; };
  }, [selected]);

  const join = async (course: Course) => {
    try {
      await api.joinCourse(course.id);
      setNotice(`已加入 ${course.code}`);
      await refresh();
      load();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const assign = async (course: Course, member: CourseMember, role: string) => {
    try {
      await api.assignCourseRole(course.id, member.userId, role);
      setMembers(await api.courseMembers(course.id));
      setNotice(`${member.displayName} 的课程角色已更新`);
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const joinedIds = new Set(mine.map((c) => c.id));
  const canAssign = selected ? selected.myRole === 'TEACHER' || selected.myRole === 'TA' : false;

  return (
    <div className="layout">
      <section>
        <h1>课程</h1>
        {notice && <p className="notice">{notice}</p>}
        <ErrorBox message={error} onRetry={load} />

        {loading ? <SkeletonList rows={3} /> : (
          <>
            {me && (
              <>
                <h2>我的课程</h2>
                <ul className="course-list">
                  {mine.map((c) => (
                    <li key={c.id} className={selected?.id === c.id ? 'selected' : ''}>
                      <div>
                        <b>{c.code}</b> {c.name}
                        <div className="muted">{c.term} · {c.teacherName} · {c.myRole}</div>
                      </div>
                      <button onClick={() => setSelected(c)}>查看成员</button>
                    </li>
                  ))}
                  {mine.length === 0 && <li className="muted">还没有加入课程。</li>}
                </ul>
              </>
            )}

            <h2>全部课程</h2>
            <ul className="course-list">
              {all.map((c) => (
                <li key={c.id}>
                  <div>
                    <b>{c.code}</b> {c.name}
                    <div className="muted">{c.term} · {c.teacherName}</div>
                  </div>
                  {me && !joinedIds.has(c.id) && <button onClick={() => join(c)}>加入</button>}
                  {joinedIds.has(c.id) && <span className="muted">已加入</span>}
                </li>
              ))}
              {all.length === 0 && <li className="muted">暂无课程。</li>}
            </ul>
          </>
        )}

        {selected && (
          <section className="panel">
            <div className="panel-head">
              <h3>{selected.code} {selected.name} · 成员</h3>
              <button className="link" onClick={() => setSelected(null)}>收起</button>
            </div>
            {membersLoading ? <SkeletonList rows={2} /> : (
              <table className="board">
                <thead><tr><th>成员</th><th>课程角色</th>{canAssign && <th>调整</th>}</tr></thead>
                <tbody>
                  {members.map((m) => (
                    <tr key={m.userId}>
                      <td><Link to={`/users/${m.userId}`}>{m.displayName}</Link></td>
                      <td>{m.role}</td>
                      {canAssign && (
                        <td>
                          <select value={m.role} onChange={(e) => assign(selected, m, e.target.value)}>
                            {['STUDENT', 'TA', 'TEACHER'].map((r) => <option key={r} value={r}>{r}</option>)}
                          </select>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {!membersLoading && members.length === 0 && <p className="muted">暂无成员。</p>}
          </section>
        )}
      </section>

      <aside>
        <h3>课程角色</h3>
        <p className="muted">STUDENT 可提问与回答；TA、TEACHER 可以认证回答、发起悬赏并管理课程成员。</p>
      </aside>
    </div>
  );
}
