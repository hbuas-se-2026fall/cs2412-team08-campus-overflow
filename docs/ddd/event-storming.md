# Lab 3 事件风暴全景图 · 说明文档

> 配套图片：`event-storming.png`（同目录，2600 × 1800）

---

## 1. 这张图是什么

这是 Lab 3 Step 1～Step 4 跑完之后的白板快照：把 CampusOverflow 的**领域事件、命令、角色、外部系统、聚合、限界上下文**一次性铺在同一张平面上，用来和组内同学对齐"这个系统到底在发生什么事"。

它解决三个问题：

1. **业务视角**：不看代码，先看业务上发生了哪些"已经被确认的事实"（领域事件）。
2. **建模视角**：这些事实该由哪个聚合裁决、跨不跨上下文、跨的时候靠什么模式集成。
3. **风险视角**：哪些地方还没想清楚（热点），留给组会决策。

## 2. 怎么读这张图（从外到内五层）

### 第 1 层：顶部业务时间线

从左到右是一条主干价值链：

```
注册加入 → 提问 → 回答 → 评论 / 投票 → 采纳 → 声誉结算 → 搜索发现 / 通知
```

它回答"先发生什么、后发生什么"。注意**声誉结算和搜索发现都在采纳之后**——这正是跨上下文最终一致性的来源。

### 第 2 层：图例（色规）

严格按教学方案 Step 2 的色规，颜色不是装饰，是语义：

| 颜色 | 含义 | 在本图中的用法 |
| --- | --- | --- |
| 橙 | 领域事件（过去时） | 一律用 `QuestionPosted` 这种"已发生"的命名 |
| 蓝 | 命令 | 用户/系统**想**做的事，`PostQuestion` |
| 黄 | 角色 | 谁发起的：学生、教师、管理员、定时任务、事件中继 |
| 紫 | 聚合 / 值对象 | 谁对命令负责、谁守住不变式 |
| 粉 | 外部系统 | 校园统一身份认证、SMTP、Redis、MySQL FULLTEXT |
| 绿 | 读模型 | 查询侧投影，不参与写模型一致性 |
| 红 | 热点 Hot Spot | 有争议、没定论的地方，留给组会 |

### 第 3 层：`co-qa` 核心域（最大那一框）

整条横带就是一个限界上下文，四行从下往上读是"聚合 → 事件 → 命令 → 角色"：

- **黄（角色）**：学生·提问者、学生·回答者、课程管理者·教师、管理员、定时任务
- **蓝（命令）**：PostQuestion、EditQuestion、SubmitAnswer、PostComment、CastVote（投票/改投/撤销）、AcceptAnswer、EndorseAnswer、SoftDeleteContent
- **橙（事件）**：QuestionPosted、QuestionEdited、AnswerSubmitted、CommentPosted、VoteCast、AnswerAccepted、AnswerEndorsed、ContentDeleted
- **紫（聚合）**：Question（聚合根，采纳在此裁决）、Answer（聚合根，采纳由 Question 决定）、Vote（独立聚合，value ∈ {−1,+1}）、Comment（独立聚合，只允许一层）

框内三条不变式来自指导书对 Question / Answer 聚合的约束定义，并带上错误码——这是"聚合识别"是否真的落地的证据：

1. 一个问题最多一个被采纳答案且不可更改（重复采纳 → 409 QA-2002）
2. 只有提问者或课程管理者可采纳（否则 403）；已删除问题不可再回答、编辑或采纳
3. 不能给自己的内容投票（422 QA-1015）；被采纳的回答不可由作者删除（防"骗采纳再删除"）

> 为什么 Vote 和 Comment 是"独立聚合"而不是 Question 的子实体？因为它们**高并发写、且不需要与 Question 强一致**（一个赞不该阻塞编辑问题）。放在 Question 聚合里会让整个题目行被锁住，这是取舍，不是遗漏。

### 第 4 层：下半三个上下文框

| 上下文 | 子域类型 | 角色 | 命令 | 事件 | 聚合 / 值对象 |
| --- | --- | --- | --- | --- | --- |
| `co-identity` | 支撑子域 | 学生、管理员、教职工 | Register、VerifyStudent、CreateStaffAccount、DisableAccount | UserRegistered●、StudentVerified、StaffAccountCreated、AccountDisabled | User（聚合根）、CourseMember（实体） |
| `co-reputation` | 核心域 | 系统·事件中继、定时任务、管理员 | OpenAccount、ApplyReputation、AwardBadge、OpenBounty、CloseBounty、RefreshLeaderboard | ReputationAccountOpened、ReputationChanged●、BountyOpened●、BountyAwarded●、BadgeAwarded●、BountyClosed●、LeaderboardUpdated | ReputationAccount（聚合根·乐观锁）、Bounty（聚合根·状态机）、LedgerEntry（值对象·只追加）、ReputationRulebook（领域策略） |
| `co-discovery` | 通用 / 支撑子域 | 搜索用户、系统·中继、被 @ 用户 | ReindexQuestion、SearchQuestions、MarkNotificationRead | QuestionIndexed、RelatedQuestionsRecalculated、MentionNotified、NotificationCreated | SearchDocument（值对象·写模型）、Notification（聚合根）、SearchQuery（值对象·已清洗） |

### 第 5 层：底部四块

- **绿的读模型**：问题列表缓存（Q&A）、搜索结果投影（Discovery）、排行榜 ZSET（Reputation）、通知中心列表（Discovery）。注意它们分别挂在**哪个上下文**下面——读模型不是全局共享的。
- **粉的外部系统**：校园统一身份认证（OAuth）、邮件服务（SMTP）、Redis、MySQL FULLTEXT。放在这里是为了标清"哪些复杂度不归我们"。
- **红的热点**：见第 6 节。
- **最底部两行文字**：4 条跨上下文集成关系 + 共享内核声明 + 本次四步流程的时长。

---

## 3. 四步流程与图的对应

| 步骤 | 时长 | 本图上留下的痕迹 |
| --- | --- | --- |
| Step 1 Big Picture | 40 min | 全部橙贴（先只贴事件，不写命令） |
| Step 2 Process Modeling | 30 min | 蓝贴（命令）、黄贴（角色）、粉贴（外部系统） |
| Step 3 Software Design | 30 min | 紫贴（聚合 / 值对象）+ Q&A 框内三条不变式 |
| Step 4 上下文映射与术语表 | 20 min | 四个虚线块（限界上下文）+ 底部 4 条集成关系 |

---

## 4. 领域事件清单（23 个，与指导书事件目录逐条对照）

### 4.1 指导书已定义：14 个，全部覆盖

| # | 事件 | 发布者 | 订阅者与处理 | 图内位置 |
| --- | --- | --- | --- | --- |
| 1 | UserRegisteredEvent | Identity | Reputation 开户 + 按角色发初始悬赏积分；Discovery 建索引 | identity |
| 2 | QuestionPostedEvent | Q&A | Discovery 插入搜索投影 | qa |
| 3 | QuestionEditedEvent | Q&A | Discovery 更新投影文本与标签 | qa |
| 4 | AnswerSubmittedEvent | Q&A | Reputation 授予"初来乍到"；Discovery 回答数 +1、通知提问者 | qa |
| 5 | AnswerAcceptedEvent | Q&A | Reputation +15、徽章、悬赏结算；Discovery 标记已采纳、通知回答者 | qa |
| 6 | AnswerEndorsedEvent | Q&A | Reputation 授予"名师认可"；Discovery 通知回答者 | qa |
| 7 | VoteCastEvent | Q&A | Reputation 按差值计分（受每日上限约束）；Discovery 得分同步 | qa |
| 8 | CommentPostedEvent | Q&A | Discovery 解析 @提及并通知 | qa |
| 9 | ContentDeletedEvent | Q&A | Discovery 标记投影删除 / 回答数 −1 | qa |
| 10 | ReputationChangedEvent | Reputation | 排行榜更新周/月/学期 ZSET（经 ProcessedEventStore 幂等） | reputation |
| 11 | BadgeAwardedEvent | Reputation | Discovery 通知获得者 | reputation |
| 12 | BountyOpenedEvent | Reputation | Discovery 维护"悬赏中"标记 | reputation |
| 13 | BountyAwardedEvent | Reputation | Discovery 通知获奖者 | reputation |
| 14 | BountyClosedEvent | Reputation | Discovery 撤销"悬赏中"标记 | reputation |

> 命名约定：白板便利贴上事件名省略 `Event` 后缀（写 `AnswerAccepted`），代码里的事件类名补全后缀（写 `AnswerAcceptedEvent`）。下表按代码名列出。

### 4.2 本组补充识别：9 个

| 事件 | 所属上下文 | 为什么要补 |
| --- | --- | --- |
| StudentVerified | Identity | 校园认证是独立动作，失败/成功要能被审计，不能混进 UserRegistered |
| StaffAccountCreated | Identity | 教职工账号只能由管理员创建，与自助注册是两条不同路径 |
| AccountDisabled | Identity | 禁用后必须联动：不能再发帖、声誉账户冻结，是一个必须被广播的事实 |
| ReputationAccountOpened | Reputation | 开户是计分的前置条件，独立成事件才能处理"事件乱序先到计分"的情况 |
| LeaderboardUpdated | Reputation | 排行榜是投影，但它的更新节奏（周/月/学期）本身是可观察的业务事实 |
| QuestionIndexed | Discovery | 投影同步完成是 Discovery 自己的事实，用于排查"搜不到"的问题 |
| RelatedQuestionsRecalculated | Discovery | 相关问题重算是计算型动作，需要知道何时完成、按什么规则 |
| MentionNotified | Discovery | @提醒涉及外部（邮件）与站内两条通道，成败需要分别记录 |
| NotificationCreated | Discovery | 站内通知有 `(sourceEventId, recipientId)` 唯一约束，创建本身是聚合行为 |

---

## 5. 跨上下文集成关系

| 编号 | 上游 → 下游 | 模式 | 载体 |
| --- | --- | --- | --- |
| 1 | Identity → Q&A / Reputation / Discovery | 开放主机服务 + 发布语言（OHS/PL） | 同步：`IdentityApi` 查 `UserSummary`；异步：`UserRegisteredEvent` |
| 2 | Q&A → Reputation | 客户 / 供应商（Customer/Supplier） | `Answer*` / `VoteCast` 事件；同步补数据走 `QaQueryApi.findQuestion` |
| 3 | Q&A → Discovery | 遵奉者（Conformist） | `Question*` / `Answer*` / `VoteCast` / `Comment` / `ContentDeleted` 事件 |
| 4 | Reputation → Discovery | 遵奉者（Conformist） | `Bounty*` / `BadgeAwarded` 事件 |
| — | 全部上下文 → `co-shared-kernel` | 共享内核（Shared Kernel） | Maven 依赖 |

---

## 6. 四个热点

| # | 热点 | 争议点 | 当前倾向 |
| --- | --- | --- | --- |
| ① | 采纳后的最终一致 | 声誉 +15 与投影"已采纳"不在同一事务，演示时会有数百毫秒延迟 | 演示话术明确说明"最终一致"，不假装强一致 |
| ② | 每日上限与并发记账 | 每日 200 上限与并发计分顺序冲突；`LedgerEntry` 幂等键如何参与去重 | 以账本流水为准，先写流水再算余额，上限在日终校正 |
| ③ | 悬赏到期处理 | 到期未采纳由谁关闭、冻结积分怎么退 | 定时任务关闭 → `BountyClosed`，终态 AWARDED / EXPIRED 不可变 |
| ④ | 软删除的联动 | `ContentDeleted` 之后：投影标记删除、回答数 −1、已发通知是否撤回 | 投影标记删除、计数回退；通知不撤回但点击后提示"内容已删除" |

