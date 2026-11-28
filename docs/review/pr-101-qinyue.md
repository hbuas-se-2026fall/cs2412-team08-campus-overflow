# PR #101 评审记录

| 项目 | 内容 |
| --- | --- |
| 仓库 | `hbuas-se-2026fall/cs2412-team08-campus-overflow` |
| PR | [#101 前端：Sprint4 页面补齐（课程、管理员、个人主页、问题详情编辑）](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/101) |
| 作者 | 邹园园（`zouyuanyuan27`） |
| 评审人 | 秦悦（`qy2026932`） |
| 评审日期 | 2026-11-26 |
| 变更规模 | 16 个新增/修改文件，前端为主 |
| 评审结论 | 契约全部对齐，两处阻塞项当场修复后合并 |

## 一、结论

接口契约逐条对照后端 Controller 后确认一致：`admin/users` 的 `verified / page / size`、`{id}/verify`、`{id}/role`、`{id}/disable`；`courses` 的 list、`mine`、`join`、`members`、`members/{userId}`；`auth/profile`；`questions/{id}` 的 PUT 与 DELETE；`answers/{id}` 的 PUT 与 DELETE。视图模型同样对齐：`AdminUser` 对 `UserView`、`Course` 对 `CourseView`、`CourseMember` 对 `CourseMemberView`。

安全与交互细节也合格：`MarkdownPreview` 走 DOMPurify 白名单，评论中的 @ 提及没有使用 `innerHTML`，删除类操作均带 confirm，`RequireAdmin` 前端守卫之外还有后端 URL 级授权兜底。

## 二、当场修复的两处阻塞项

结论：这两条会让功能出错，不适合留到合并后，已直接改在 `feat/frontend-sprint4`（commit `2fa0022`），本地 `npm run typecheck` 与 `npm run build` 均通过。

**1. `CoursesPage` 成员列表请求竞态**

连续点击两个课程的"查看成员"，先发出的请求可能后返回，把上一门课的成员 Data 写进当前页面，出现"标题是 B、成员是 A"。修法：effect 内加失效守卫（`alive` 标记与清理函数），切换课程时先清空成员列表，避免旧响应覆盖新状态。

**2. `AdminUsersPage` 分页双常量**

`totalPages` 按前端本地 `PAGE_SIZE = 20` 推算，但请求不传 `size`，实际吃后端默认 20，任意一侧改动分页就会错位（与 #98 中 `api.ledger` 的问题是同一类型）。修法：`api.adminUsers` 显式传 `size`，`totalPages` 改用后端返回的 `data.size` 兜底计算；切换 tab 时同时清空上一轮的错误与提示。

## 三、建议项（不阻塞合并，排入后续）

**1. `ProfilePage` 的 `api.updateProfile` 返回类型**

声明为 `void`，后端实际返回 `MeView`。可以拿返回值直接更新表单状态，省掉一次 `GET /me`。

**2. 创建教职工账号表单缺前端校验**

昵称 2–20、密码 8–64、邮箱格式全靠后端 400 兜底，错误提示是 Problem Details 的 `detail`，对使用者不友好。建议前端先校验并给字段级提示。

**3. 编辑问题表单缺前端校验，且与编辑回答共用 `saving` 状态**

标题 10–150、标签 1–5 未校验；两个入口共用一个 `saving`，提交问题编辑时会连带禁用回答编辑按钮。

**4. `QuestionListPage` 关键词搜索与 `QuestionDetailPage` 的 related 是裸请求**

没有失效守卫，快速切换关键词或问题 id 时，旧响应可能覆盖新结果。列表页的骨架屏能遮住一部分观感问题，但数据覆盖仍在。

**5. `frontend/tsconfig.tsbuildinfo` 未进 `.gitignore`**

`tsc -b` 的构建产物，导致本地 `git status` 长期挂一个未跟踪文件。

## 四、验证方式

`npm ci` 安装依赖后执行 `npm run typecheck`、`npm run build`，均通过（构建产物 481.67 kB）。

## 五、后续

两处阻塞项已修，建议项归入 Sprint 4 前端技术债；评审人 WTR（`wtr999-jpg`）已 Approve，PR 已合并。
