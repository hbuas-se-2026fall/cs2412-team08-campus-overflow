# 上下文映射图 Context Map · v1.0

> 文件路径：`docs/ddd/context-map.md`
> 负责人：张嘉雨（@ember216），Developer
> 日期：2026-10-31
> 配套图：`context-map-v1.0.png` / `context-map-v1.0.pdf`

## 一、限界上下文清单

| 上下文 | 子域类型 | 职责 | 聚合根 |
| --- | --- | --- | --- |
| `co-identity` | 支撑域 | 用户注册、登录、认证、账号禁用、课程成员管理 | User（CourseMember为User内实体） |
| `co-qa` | **核心域** | 提问、回答、评论、投票、采纳、编辑/软删除、教师认证 | Question、Answer、Vote、Comment |
| `co-reputation` | **核心域** | 声誉账户、积分账本、采纳/投票计分、徽章、悬赏、排行榜 | ReputationAccount、Bounty |
| `co-discovery` | 支撑域 | 搜索索引、相关推荐、通知中心、@提醒 | Notification（SearchDocument为读模型/投影，非聚合根） |
| `co-shared-kernel` | 共享域 | 领域事件基类、通用值对象、ID生成器、错误码 | （无聚合，共享代码） |
| `co-bootstrap` | 通用域 | Spring自动装配各上下文实现，启动入口 | （无） |

## 二、上下文间集成关系

| 编号 | 上游 → 下游 | 集成模式 | 同步/异步 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | `co-identity` → `co-qa` | 客户-供应商（Customer-Supplier） | 同步调用 | Q&A 通过 IdentityApi 获取当前登录用户与角色 |
| 2 | `co-identity` → `co-reputation` | 开放主机服务 + 发布语言（OHS/PL） | 异步事件 | UserRegisteredEvent 触发自动开户；AccountDisabledEvent 冻结账户 |
| 3 | `co-qa` → `co-reputation` | 发布-订阅（Pub/Sub）+ 防腐层（ACL） | 异步事件 | AnswerAcceptedEvent、VoteCastEvent 触发声誉计分；Reputation 用 ACL 把 Q&A 事件转成内部命令 |
| 4 | `co-qa` → `co-discovery` | 遵奉者（Conformist） | 异步事件 | QuestionPostedEvent、AnswerSubmittedEvent、ContentDeletedEvent 同步搜索索引；Discovery 直接用 Q&A 事件数据建投影 |
| 5 | `co-reputation` → `co-discovery` | 遵奉者（Conformist） | 异步事件 | BountyOpenedEvent、BadgeAwardedEvent 触发通知与悬赏标记更新 |
| 6 | 所有上下文 → `co-shared-kernel` | 共享内核（Shared Kernel） | Maven 依赖 | 共享领域事件基类、值对象、错误码；改动需团队共识 |

## 三、为什么这样划

1. **`co-qa` 是最大的核心域**：提问、回答、投票、采纳是平台价值核心，所有其他上下文都围绕它产生事件。
2. **`co-reputation` 独立出来**：声誉计算有自己的状态机（账本只追加、积分上限、徽章触发规则），如果和 Q&A 放一起会让 Question 聚合承担太多不变式。
3. **`co-discovery` 独立出来**：搜索索引和通知是读模型/投影，不需要写模型强一致；用事件驱动最终一致即可。
4. **`co-shared-kernel` 最小化**：只放真正共享的基类和值对象，避免变成"大杂烩"。

## 四、与事件风暴文档的关系

本图是对 `event-storming.md` 第5节"跨上下文集成关系"的可视化补充，术语命名已对齐：
- OHS/PL（Identity→其他）
- 客户-供应商（Identity→Q&A）
- 发布-订阅+ACL（Q&A→Reputation）
- 遵奉者（Q&A→Discovery、Reputation→Discovery）
- 共享内核（所有→shared-kernel）
