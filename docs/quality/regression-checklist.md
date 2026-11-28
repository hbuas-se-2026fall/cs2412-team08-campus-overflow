# Lab8 回归用例清单

配套文档：`regression-plan.md`（策略与准入准出）、`lab8-defect-log.md`（缺陷登记）。失败项 24 小时内登记并定级。

路径前缀统一为 `/api/v1`，除特别说明外均需已登录会话；写操作需携带 CSRF 令牌。预期列中成功项记「2xx + 可观察结果」，失败项记具体错误码，错误码语义见 `docs/错误码.md`。

## 一、环境与部署

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-01 | P0 | `GET /` | 无 | 200，页面框架渲染，静态资源加载无 404 |
| R-02 | P0 | `GET /actuator/health` | 无 | 200，`status` 为 `UP` |
| R-03 | P0 | `GET /actuator/health/readiness` | 无 | 200，`status` 为 `UP`，与 compose 健康检查同源 |
| R-04 | P1 | `GET /v3/api-docs` | 无 | 200，返回 JSON，路径集合与后端控制器定义一致，无缺失路由 |
| R-05 | P1 | `docker compose -f deploy/docker-compose.yml ps` | 服务器 | mysql、redis、backend、web 均为 running/healthy |
| R-06 | P2 | 查询 `information_schema` 字符集与排序规则 | 服务器 | 库与表为 utf8mb4 / utf8mb4_0900_ai_ci，ngram_token_size=2 |
| R-07 | P2 | 查询 `flyway_schema_history` | 服务器 | 最新一条为 success，版本与 main 分支迁移脚本一致 |
| R-08 | P1 | `redis-cli PING` 或 `/actuator/health` 的 redis 组件 | 服务器 | 返回 PONG，或组件状态 `UP` |

## 二、认证与账号

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-09 | P0 | `GET /auth/csrf` | 无 | 2xx，返回令牌并下发 CSRF Cookie |
| R-10 | P0 | `POST /auth/register` | 无 | 201，返回用户 id，昵称全局唯一 |
| R-11 | P0 | `POST /auth/login` | 已注册账号 | 200，会话建立，`GET /auth/me` 可取到本人 |
| R-12 | P0 | `POST /auth/login` 错误密码 | 无 | 401 `AUTH-401`，响应不泄露账号是否存在 |
| R-13 | P1 | 1 分钟内连续 5 次错误登录 | 无 | 第 5 次起返回 429 `AUTH-429` |
| R-14 | P1 | `GET /auth/me` | 已登录 | 200，返回学号脱敏后的资料与角色 |
| R-15 | P1 | `PUT /auth/profile` | 已登录 | 2xx，昵称与邮箱更新生效；改他人资料返回 403 `ID-3002` |
| R-16 | P2 | `POST /auth/register` 密码仅字母 | 无 | 422 `ID-1004` |
| R-17 | P2 | `POST /auth/register` 重复学号或邮箱 | 已有账号 | 409 `ID-2001` |
| R-18 | P2 | `POST /questions` 不带 CSRF 令牌 | 已登录 | 403 `AUTH-403-CSRF` |

## 三、课程与角色

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-19 | P1 | `GET /courses` | 已登录 | 200，列表含本人已加入课程及其课程内角色 |
| R-20 | P1 | `POST /courses` | 教师会话 | 2xx，课程创建成功，教师自动成为课程成员 |
| R-21 | P1 | `POST /courses/{courseId}/join` | 学生会话 | 2xx，成员新增；重复加入返回 409 `ID-2005` |
| R-22 | P2 | `GET /courses/{courseId}/members` | 已登录 | 200，成员列表完整，查无对应用户时显示占位昵称 |
| R-23 | P2 | `PUT /courses/{courseId}/members/{userId}` | 教师或管理员 | 2xx，成员角色变更生效；非课程教师且非管理员返回 403 `ID-3003` |
| R-24 | P3 | `POST /courses` 同学期同代码 | 教师会话 | 409 `ID-2004` |

## 四、问答主流程

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-25 | P0 | `GET /questions?page=1&size=20` | 已登录 | 200，分页结构含 items、total、page、size |
| R-26 | P0 | `POST /questions` | 已登录 | 201，返回问题 id，列表页可见 |
| R-27 | P1 | `GET /questions/{questionId}` | 已登录 | 200，含正文、标签、回答列表、分数与本人投票状态 |
| R-28 | P2 | `PUT /questions/{questionId}` | 作者会话 | 2xx，编辑生效；非作者返回 403 `QA-3002` |
| R-29 | P2 | `DELETE /questions/{questionId}` | 作者会话 | 2xx，软删除后列表不可见；陌生人返回 403 `QA-3003` |
| R-30 | P2 | `GET /questions/{questionId}` 已删除的问题 | 已登录 | 404 `QA-4001` |
| R-31 | P2 | `POST /questions` 边界值 | 已登录 | 标题 9 字或 151 字 → 422 `QA-1001`；正文 19 字 → 422 `QA-1002`；标签 0 个或 6 个 → 422 `QA-1005` |
| R-32 | P1 | `POST /questions/{questionId}/answers` | 他人会话 | 201，回答出现在详情页，回答数递增 |
| R-33 | P3 | `POST /questions/{questionId}/answers` 问题不存在 | 已登录 | 404 `QA-4001` |
| R-34 | P1 | `PUT /questions/{questionId}/accepted-answer` | 提问者会话 | 2xx，问题标记已采纳，回答者声誉增加 15；重复采纳 409 `QA-2002`；非提问者且非教师/助教 403 `QA-3001` |

## 五、评论与投票

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-35 | P0 | `POST /comments` | 已登录 | 201，评论挂在目标下，目标类型与目标 id 正确 |
| R-36 | P1 | `POST /comments` 带 `parentId` | 已登录 | 201，回复挂在父评论下 |
| R-37 | P1 | 对二级回复再回复 | 已登录 | 422 `QA-1016` |
| R-38 | P1 | 回复父评论但目标类型不同 | 已登录 | 422 `QA-1017` |
| R-39 | P2 | `POST /comments` 正文长度边界 | 已登录 | 1 字或 601 字 → 422 `QA-1003` |
| R-40 | P0 | `PUT /votes` 值为 1 | 他人会话 | 200，`scoreDelta` 为 1，详情页分数同步 |
| R-41 | P1 | 同一目标由 1 改投 -1 | 同上会话 | 200，`scoreDelta` 为 -2，净分数为 -1 |
| R-42 | P2 | `PUT /votes` 值为 0 或 2，或给自己投票 | 已登录 | 422 `QA-1014`；自投 422 `QA-1015` |

## 六、声誉与悬赏

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-43 | P1 | `GET /users/{userId}/reputation` | 已登录 | 200，声誉、可用积分、冻结积分、徽章墙齐全；查看他人时积分不可见 |
| R-44 | P2 | `GET /users/{userId}/reputation/ledger` | 本人会话 | 200，流水按时间倒序；用他人会话调用返回 403 `REP-3002` |
| R-45 | P1 | `GET /leaderboard?period=week` | 已登录 | 200；以 month、term、all 各调一次均返回 200 且名次口径不同 |
| R-46 | P1 | `GET /questions/{questionId}/bounty` | 已登录 | 200，返回悬赏状态与积分 |
| R-47 | P2 | `POST /questions/{questionId}/bounty` 参数越界 | 教师或助教会话 | points 为 5 或 501 → 400 `SYS-400-VALIDATION`；days 为 0 或 15 → 400 `SYS-400-VALIDATION` |
| R-48 | P2 | `POST /questions/{questionId}/bounty` 权限与余额 | 学生会话、积分不足的教师会话 | 学生发起 403 `REP-3001`；教师可用积分不足 422 `REP-1002` |
| R-49 | P2 | 已采纳问题发悬赏或重复发悬赏 | 教师会话 | 422 `REP-1005`；已有进行中的悬赏 409 `REP-2001` |
| R-50 | P3 | `GET /admin/reputation/reconciliation` | 管理员会话 | 200，返回空列表表示账本与账户一致 |

## 七、搜索与通知

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-51 | P1 | `GET /search/questions?q=红黑树` | 已登录 | 200，命中相关问题，已采纳问题带标记 |
| R-52 | P2 | `GET /search/questions` 关键词越界 | 已登录 | 单个词项 1 字 → 422 `DIS-1001`；超过 50 字 → 422 `DIS-1002` |
| R-53 | P1 | `GET /search/questions?bounty=true` | 已登录 | 200，只返回带进行中悬赏的问题 |
| R-54 | P2 | `GET /questions/{questionId}/related?limit=5` | 已登录 | 200，按相似度返回，数量不超过 limit |
| R-55 | P1 | `GET /notifications?page=1&size=20` 与 `GET /notifications/unread-count` | 已登录 | 200，列表分页正确，未读数与实际一致 |
| R-56 | P2 | `POST /notifications/{id}/read` | 本人会话 | 2xx，未读数递减；标记他人通知返回 403 `DIS-3001` |
| R-57 | P2 | `POST /notifications/read-all` | 本人会话 | 2xx，未读归零 |

## 八、管理与权限

| 编号 | 层 | 请求 | 前置 | 预期 |
| --- | --- | --- | --- | --- |
| R-58 | P2 | `GET /admin/users?verified=false` | 管理员会话 | 200，按认证状态过滤生效；非管理员返回 403 `ID-3001` |
| R-59 | P2 | `POST /admin/users/{userId}/verify` | 管理员会话 | 2xx，用户认证标记生效；重复认证 409 `ID-2006` |
| R-60 | P2 | `PUT /admin/users/{userId}/role` | 管理员会话 | 2xx，角色变更生效；修改自己 422 `ID-1006` |
| R-61 | P2 | `POST /admin/users/{userId}/disable` | 管理员会话 | 2xx，被禁用账号无法登录；禁用自己 422 `ID-1007` |
| R-62 | P3 | `POST /admin/users` 创建教职工 | 管理员会话 | 2xx，创建即认证；重复工号 409 `ID-2001`；角色为学生 422 `ID-1005` |

## 九、稳定性与恢复

| 编号 | 层 | 观察/操作 | 前置 | 判据 |
| --- | --- | --- | --- | --- |
| R-63 | P3 | 回归期间接口延迟 | Grafana 或 `/actuator/metrics` | p95 不超过 500 毫秒 |
| R-64 | P3 | 回归期间 5xx 计数 | 同上 | 为 0 |
| R-65 | P3 | 后端日志 ERROR 行 | 服务器 | 除预期 4xx 外无异常堆栈 |
| R-66 | P2 | 重启 backend 容器 | 已部署 | 服务自动恢复，数据不丢，能重新登录 |
| R-67 | P2 | 回滚到上一镜像 tag | 已部署 | 接口可用，健康检查 UP，无需手工修库 |

## 十、执行记录

每轮执行填一行，累计多轮用于稳定性判断。

| 轮次 | 日期时间 | commit | 镜像 tag | 执行范围 | 通过 | 失败 | 阻塞 | 结论 | 执行人 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 |  |  |  | P0 |  |  |  |  | 秦悦 |
| 2 |  |  |  | P1 |  |  |  |  | 秦悦 |
| 3 |  |  |  | P2 |  |  |  |  | 秦悦 |
| 4 |  |  |  | P3 |  |  |  |  | 秦悦 |

失败项按 `lab8-defect-log.md` 的字段登记，编号从 D-09 起接续 Lab7。阻塞项在备注中写明缺哪一项环境，不计入失败率。
