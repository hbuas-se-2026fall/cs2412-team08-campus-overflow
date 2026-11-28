# 缺陷清单（Sprint 4 / Lab8）

登记规则：任何人发现问题即登记，编号不复用；优先级按影响面与演示阻断程度定级，P0 立即修，P1 当班修，P2 排入下个迭代。
分级口径：P0 阻断主流程或数据错误；P1 功能可用但有明显错误或体验断裂；P2 文案、样式、可维护性问题。

## 一、当前已登记

| 编号 | 标题 | 模块 | 现象与复现 | 优先级 | 状态 | 来源 |
| --- | --- | --- | --- | --- | --- | --- |
| D-01 | 声誉页首屏闪现「暂无流水记录」 | frontend / 声誉档案 | 进入 `/users/{id}` 后，流水请求未返回前 `entries` 为空且 `ledgerNote` 为空，先渲染空态提示再切表格 | P2 | 待修 | 评审 #98 |
| D-02 | 流水页分页大小存在两处常量 | frontend / 声誉档案 | `api.ledger(userId, page)` 依赖 `api.ts` 默认 `size = 20`，组件内另有 `PAGE_SIZE = 20`，改一处漏一处会导致分页与总数不一致 | P2 | 待修 | 评审 #98 |
| D-03 | 演示脚本 4:20 段的 Swagger 分组名错误 | docs / 演示材料 | `GET /api/v1/admin/reputation/reconciliation` 位于 `AdminReputationController`，`@Tag` 为「管理 Admin」，脚本写成在「声誉 Reputation」分组操作，现场会找不到入口 | P1 | 已合并，待补改 | 评审 #99 |
| D-04 | 前端缺少自动化测试 | frontend | CI 仅执行 `typecheck` 与 `build`，无单测与组件测试，页面逻辑全靠人工冒烟 | P1 | 待排期 | 自查 |
| D-05 | 集成测试在 CI 中不计入门禁 | CI / backend | `ci.yml` 的集成测试步骤带 `continue-on-error: true`，Testcontainers 失败不会拦住合并 | P1 | 已确认·已排期（王钰滢，Lab8 窗口内移除 `continue-on-error`；关闭判据：集成测试真实失败即拦合并） | 自查 |
| D-06 | 覆盖率门禁阈值未启用 | CI / backend | 根 pom 的 `domain.coverage.minimum` 为 0 且未配置 `check` 执行，JaCoCo 只出报告不拦截 | P1 | 已确认·已排期（王钰滢，配 `SONAR_TOKEN` 并让 `domain.coverage.minimum` 的 check 生效；关闭判据：门禁 Failed 能拦住合并） | 自查 |
| D-07 | `co-qa-interfaces` 与 `co-qa-infrastructure` 无单元测试 | backend / Q&A | 接口层 5 个类、基础设施层 17 个类零单测，此前仅靠端到端 IT 间接覆盖 | P2 | 部分缓解 | 自查 |
| D-08 | CI 集成测试步骤被 `continue-on-error` 掩盖，失败原因不可见 | CI / backend | 「集成测试」步骤在 Check 注释里带 `Process completed with exit code 1`，后端 job 仍显示 succeeded，API 里该 step 结论为 success（continue-on-error 的表现）。e4973b2 的运行 run 45740544391 中该步骤耗时 28 秒；本地用同一条命令 `verify -Dsurefire.skip=true` 执行时，failsafe 能识别 `CampusOverflowFlowIT` 与 `QuestionInteractionIT`，说明 CI 该步骤不是空跑，失败发生在运行期。失败原因已定位，见下方 2026-11-28 补充说明 | P1 | 已确认·已排期（王钰滢，先在集成测试步骤输出 `docker info` 与 `docker version` 再定配置层根因；关闭判据：failsafe errors 归零） | 自查 + 评审 #102 |

D-07 的缓解说明：新增 `QuestionInteractionIT` 覆盖投票改投、评论层级、热门标签的 Controller → Service → Repository 全链路，接口层与持久化层不再是纯裸奔状态，但仓储层的分支（软删过滤、分页边界）仍需补单测。

D-08 的结论（2026-11-28 补充，失败点已核实）：`#107` 在 CI 中新增测试报告归档后，首次拿到了集成测试的失败正文。run 46144790413（main，796c638）的 `backend-test-reports` 中，`failsafe-summary.xml` 为 completed=2、errors=2、failures=0、result=255；两份报告分别是 `CampusOverflowFlowIT`（0.024 秒）与 `QuestionInteractionIT`（1.063 秒），异常均为 `ContainerFetchException: Can't get Docker image: mysql:8.0.39`，底层原因为 `IllegalStateException: Could not find a valid Docker environment`。

结论是：集成测试在 CI 中确实执行，失败发生在容器启动阶段，不是业务断言失败。`Could not find a valid Docker environment` 是 Testcontainers 自身给出的判定，只说明它未能定位到可用的 Docker daemon；runner 是 ubuntu-latest，本身预装 Docker，因此不能据此断言「runner 没有 Docker」，更可能是 daemon 未运行或不可达、`DOCKER_HOST` 未设置、socket 权限不足，或基础镜像拉取失败。测试代码本身没有被真正验证，continue-on-error 又让 job 显示 success，所以 Lab7 的这两个 IT 仍不能记为「已验证通过」。下一步先在集成测试步骤前输出 `docker info` 与 `docker version` 做诊断，确认 daemon 可达后再定配置层根因；修复后重跑，以 `failsafe-summary` 的 errors 归零作为关闭条件。

排期口径（2026-11-28）：D-05、D-06、D-08 三条 P1 的修复人是王钰滢，依据为 Lab7 Step3 的既定分工与 #107 评审结论，目标窗口为 Lab8。#107 已合并但三项均未落地，所以状态记为「已确认·已排期」而非「已关闭」；若 Lab8 窗口内仍无修复提交，评审会前改判为「已确认·待排期」，计入未闭环项。

## 二、登记模板

| 字段 | 填写要求 |
| --- | --- |
| 编号 | D-序号，按登记顺序递增，不复用 |
| 标题 | 一句话说明现象，不写解决方案 |
| 模块 | identity / qa / reputation / discovery / frontend / ci / docs |
| 现象与复现 | 操作步骤 + 预期 + 实际，可附请求与响应片段 |
| 优先级 | P0 / P1 / P2，按上文口径 |
| 状态 | 待修 / 修复中 / 待验证 / 已关闭 |
| 来源 | 冒烟、评审、演示、用户反馈 |

## 三、上线后冒烟的执行约定

冒烟按 `smoke-checklist.md` 顺序执行，任一项失败即在本表登记并把失败项标为 P0 或 P1。
同一轮冒烟发现的同类问题合并登记一条，避免刷条目。
