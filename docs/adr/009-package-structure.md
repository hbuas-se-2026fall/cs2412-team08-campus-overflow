# ADR-009：后端 Maven 多模块分包架构（Package Structure）

**状态**：已接受 ｜ **驱动特征**：可演进性、可测试性、可维护性

> 本决策对应实验详案 Lab 4 提交物「ADR-002-package-structure.md」。因起始代码已占用
> `docs/adr/002-事务性发件箱.md` 编号，按《实验指导书 V3》Sprint 0 新增 ADR 从 009 起编号的
> 惯例，本记录以 **ADR-009** 落档，内容与实验要求的「分模块决策」一致。

## 背景

Lab 4 要求团队建立「Spring Boot + Maven 多模块工程，按 DDD 限界上下文物理分包」。起始代码已提供
27 个 Maven 模块的骨架（父 POM + 共享内核 1 + 四个上下文×5 层 + bootstrap 1 + 聚合 POM），团队需要
在此基础上确认并固化分包结构，作为后续所有 Sprint 编码的物理边界。没有明确的分包决策，会出现三类问题：
按技术分层导致子域代码混放、跨上下文直接 import 对方领域类、领域层混入 Spring/JPA 依赖——都会破坏
ADR-001 的模块化单体设计。

## 决策

1. **六个 Maven 聚合模块**，与四个限界上下文一一对应：

   | 模块 | 职责 |
   | --- | --- |
   | `co-bootstrap` | 启动装配：安全链、发件箱中继、全局异常、OpenAPI、演示数据、ArchUnit 与集成测试 |
   | `co-shared-kernel` | 共享内核：领域异常、Guard、Actor/Role、事件契约、分页（零框架依赖） |
   | `co-identity` | Identity 上下文（身份与课程） |
   | `co-qa` | Q&A 上下文（问答核心） |
   | `co-reputation` | Reputation 上下文（声誉与激励） |
   | `co-discovery` | Discovery 上下文（内容发现） |

2. **每个上下文内部再分五个 Maven 模块（五层）**：`*-api`（发布语言）、`*-domain`（领域层）、
   `*-application`（应用层）、`*-infrastructure`（基础设施）、`*-interfaces`（接口层）。
   依赖方向只允许由外向内：`api ← domain ← application ← infrastructure ← interfaces`。

3. **层间依赖规则**（与 ADR-001 一致，由 ArchUnit FF-1~FF-6 强制）：
   - `domain` 只依赖共享内核，禁止 Spring / JPA / Validation / Jackson；
   - 跨上下文只能依赖对方的 `*-api` 模块或订阅其集成事件；
   - 上下文之间无循环依赖；api 包只依赖 JDK 与共享内核。

4. **父 POM 统一管理依赖版本**：Spring Boot 3.4.x、JDK 21、JUnit 5、springdoc 2.8.6、
   ArchUnit 1.3.0、JaCoCo 0.8.12，子模块不重复声明版本，避免版本漂移。

## 后果

- 正面：模块边界即 Maven 依赖边界，配合 ArchUnit 测试可在 CI 中自动验证；领域层零框架依赖，
  单元测试无需容器即可毫秒级运行；同一上下文内的重构不影响其他上下文。
- 负面：模块数量多（27 个），首次构建与 IDE 索引较慢；开发者仍可能绕过规则直接 import，
  需要 CI 的 FF-1~FF-6 持续拦截；跨上下文协作必须走 api/事件，增加少量样板代码。
- 演进路径：若未来拆分服务，Discovery 是首选（纯下游、只读投影）；届时按 api 契约将模块
  提升为独立部署单元即可。

## 被否决的替代方案

- **单模块按包分层**：构建最快，但 Maven 无法强制边界，子域代码会随时间混入同一依赖图，
  架构退化不可控，不满足课程对限界上下文物理分离的要求。
- **每个上下文独立 Spring Boot 服务（微服务）**：运维与调试成本超出 5 人团队 8 周的能力范围，
  且系统无独立伸缩/部署需求（ADR-001 已论证为单一架构量子）。
- **按技术分层（controller/service/repository 全局分目录）**：会让四个子域的业务代码混在同一批
  包里，违背 DDD 战略设计的教学目标。
