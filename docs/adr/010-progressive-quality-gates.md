# ADR-010：渐进式质量门禁（Progressive Quality Gates）

**状态**：已接受 ｜ **驱动特征**：可持续集成、可演进性、反馈速度

## 背景

Sprint 0 起仓库 CI 即要求"后端构建与验证"全绿，但起始代码按 15 周（S0–S4）规划，Reputation、Discovery、
Q&A 应用层等大量代码在后续 Sprint 才实现。若从第一天就全量运行所有模块的测试与覆盖率，未实现模块的
失败测试会让 CI 从第 1 天红到最后，团队失去"每个 Sprint 收尾流水线都是绿的"这一工程底线（PB-04）。
渐进式质量门禁的核心是：**CI 只验证当前 Sprint 已承诺交付的模块，范围随 Sprint 推进逐步放宽**，
既保证红线不破，又避免用"跳过 CI"换取绿。

## 决策

1. **CI 通过 `SPRINT_MODULES` 环境变量声明当前门禁范围**（`.github/workflows/ci.yml`）：

   | Sprint | 门禁范围 | 说明 |
   | --- | --- | --- |
   | S1（当前） | `co-shared-kernel` + `co-identity`（五层全模块） + `co-qa/co-qa-domain` | 共享内核、Identity 已实现，Q&A 领域层（18 项测试） |
   | S2 | 追加 `co-qa` 应用层与 `co-reputation` | 随 T1-08~11、Reputation 实现后加入 |
   | S3 | 追加 `co-discovery`，恢复全量 | 内容发现实现后覆盖全部模块 |

2. **单元测试与覆盖率门禁阶段均收窄到 `SPRINT_MODULES`**：

   ```bash
   mvn test    -pl "$SPRINT_MODULES" -am
   mvn verify  -DskipITs -Djacoco.skip=false -pl "$SPRINT_MODULES" -am
   ```

   编译与架构约束（ArchUnit FF-1~FF-6）保持全量——架构红线任何 Sprint 都不可让步。

3. **集成测试（Testcontainers）暂允许失败**（`continue-on-error: true`）：Sprint 0–2 阶段其不属于
   门禁范围，Sprint 3 恢复完整门禁后移除该配置。

4. **领域层覆盖率门槛按模块独立设置**：`co-qa/co-qa-domain` 依据 T1-14 设为 **0.70**
   （当前行覆盖率 82.2%，达标）；其余领域模块暂保持 0，随实现推进逐模块提升。

## 后果

- 正面：每个 Sprint 收尾 CI 保持绿色；新增实现无需一次性补齐全部模块测试；Actions 用量随范围
  收窄而降低（8.5 节）。
- 代价：`SPRINT_MODULES` 需要随 Sprint 更新，若忘记扩展，新实现模块不会被 CI 覆盖——通过
  Sprint 评审与"范围即承诺"纪律约束。
- 与 T1-14 的关系：T1-14「CI 门禁范围加入 Q&A 模块；co-qa-domain 覆盖率门槛设为 0.70」即本
  ADR 的 S1 配置，已在 `.github/workflows/ci.yml` 与 `backend/co-qa/co-qa-domain/pom.xml` 落地。
