# US-001 注册 / US-002 登录 · 交付说明

> 负责人：张嘉雨（@ember216），角色 **Developer**　｜　日期：2026-10-28
> 来源：PO 邹园园《ProductBacklog》`US-001`（3 SP）+ `US-002`（2 SP），Sprint 1 承诺范围见 `docs/backlog/sprint1-plan.md` §五
> 分支：`feature/us-001-us-002-zhangjiayu`

---

## 一、这一版交付的是什么

`co-identity`（Identity 限界上下文）是课程框架自带的**参考实现**，领域层、应用层、接口层均已完备，没有 `TODO(S1)` 缺口。
因此本卡的交付重点不是"再写一遍注册登录"，而是**把这条链路从"能跑"变成"可验收、可回归"**：

| 交付项 | 内容 | 状态 |
| --- | --- | --- |
| 验收标准（Gherkin） | `pb-01-register.feature`（12 场景）、`pb-02-login.feature`（9 场景） | ✅ 新增 |
| 应用层用例测试 | `UserRegistrationServiceTest`（9 用例） | ✅ 新增 |
| 接口层用例测试 | `AuthControllerLoginTest`（4 用例） | ✅ 新增 |
| 测试替身 | `IdentityTestFixtures`（内存仓储 / 事件录制 / 审计录制 / 限流桩） | ✅ 新增 |
| 全链路端到端 | `CampusOverflowFlowIT`（注册 → 登录 → 提问 → …） | ⚠️ 框架提供，需 Testcontainers + Docker，本机未执行 |

**测试总数：13 个新增用例，全部通过；架构适应度函数 `ArchitectureTest` 仍全绿。**

---

## 二、验收标准（Gherkin）

编号对齐实验指导书的 `PB-xx` 口径（PO 的 `US-xxx` 编号写在文件头注释与 tag 里，两套编号可互查），
场景名沿用团队约定的「快乐路径 / 边界路径 / 异常路径」，同时**加了可被 `cucumber --tags` 筛选的英文 tag**（`@happy-path` / `@edge` / `@error` / `@SC-0xx`），避免只靠中文标题肉眼分辨。

| 文件 | 场景数 | 覆盖要点 |
| --- | --- | --- |
| `docs/backlog/stories/pb-01-register.feature` | 12 | 注册成功、邮箱大小写归一、学号/口令长度边界、`ID-1001`~`ID-1004`、`ID-2001`~`ID-2003`、审计脱敏 |
| `docs/backlog/stories/pb-02-login.feature` | 9 | 登录成功、`/me` 取本人资料、8 位口令边界、密码错误、账号不存在、账号禁用、限流 `AUTH-429`、`AUTH-401`、会话固定防护 |

错误码全部落在 `docs/错误码.md` 已有条目上，不新造编码。

---

## 三、自动化测试

### 3.1 US-001 · `UserRegistrationServiceTest`（co-identity-application）

验证的是**编排**而非领域规则本身（领域规则由既有 `UserTest` 覆盖）：

| 用例 | 断言要点 |
| --- | --- |
| 注册成功落库并广播事件 | 角色 STUDENT、未认证、可登录；发布 `UserRegisteredEvent`（Reputation 订阅它以开立声誉账户）；口令以哈希落库 |
| 审计脱敏 | `USER_REGISTERED`，detail = `2023****17` |
| 邮箱归一 | `Ming@Campus.edu` → `ming@campus.edu` |
| 学号重复 / 昵称重复 / 邮箱重复 | `ID-2001` / `ID-2002` / `ID-2003`（`ConflictException` → 409），且不产生第二条事件 |
| 弱口令 | `ID-1004`，且**落库数为 0、事件数为 0**——校验在持久化之前 |
| 学号非法 | `ID-1001` |
| 注册不触碰到登录限流器 | 防暴力破解只作用于登录链路 |

### 3.2 US-002 · `AuthControllerLoginTest`（co-identity-interfaces）

| 用例 | 断言要点 |
| --- | --- |
| 登录成功 | 返回 `MeView`、会话已保存、失败计数被 `reset`、审计 `LOGIN_SUCCESS` |
| 密码错误 | 抛 `BadCredentialsException("账号或密码错误")`、失败计数 +1、不查询 `UserQueryService` |
| 被限流 | 抛 `LockedException`、审计原因 `THROTTLED`、**`AuthenticationManager` 一次都没被调用** |
| 注册接口 | `POST /api/v1/auth/register` 返回 `201 Created` 与 `{"id":…}` |

> 设计取向：失败一律对外收敛为"账号或密码错误"，账号不存在与密码错误不做区分——这是防账号枚举的常规做法，已写进 `pb-02` 的 SC-004/SC-005。

---

## 四、如何复现

```bash
cd backend
JAVA_HOME="C:/Program Files/Java/jdk-21.0.10" \
  mvn -o -pl co-identity/co-identity-domain,co-identity/co-identity-application,\
co-identity/co-identity-infrastructure,co-identity/co-identity-interfaces -am test
```

- **必须 JDK 21**（`backend/pom.xml` 的 `java.version=21`）。用 JDK 17 会报
  `class file version 65.0 … only recognizes up to 61.0`——target 里残留的字节码版本与运行时不一致。
- `-o` 离线可用（依赖已在本地仓库）；若换机器首次构建，去掉 `-o`。

预期输出：

```
Tests run: 9 … UserRegistrationServiceTest
Tests run: 4 … AuthControllerLoginTest
Tests run: 9 … (identity domain: UserTest 6 + CourseTest 3)
BUILD SUCCESS
```

---

## 五、遗留与不做

| 项 | 说明 |
| --- | --- |
| 邮箱验证码 / 校园身份认证 | `User.verified` 由学院管理员在后台完成（`AdminUserService`），不在本卡范围内 |
| 端到端 IT | `CampusOverflowFlowIT` 需要 Docker（MySQL 8 + Redis 7），本机无 Docker 环境，未执行；CI 上跑 |
| 忘记密码 / 改密 | Backlog 中无对应故事，需 PO 补卡 |
| 前端页面 | 由页面负责人承接，本卡只交付后端链路与验收标准 |

---

## 六、看板状态

`US-001`、`US-002` 两张卡：**To Do → In Progress → Done（待 PR 合并后移入 Done）**。
