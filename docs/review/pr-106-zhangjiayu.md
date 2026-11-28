# PR #106 评审记录

| 项目 | 内容 |
| --- | --- |
| 仓库 | `hbuas-se-2026fall/cs2412-team08-campus-overflow` |
| PR | [#106 test(reputation): 补齐声誉模块边界测试](https://github.com/hbuas-se-2026fall/cs2412-team08-campus-overflow/pull/106) |
| 作者 | 邹园园（`zouyuanyuan27`） |
| 评审人 | 张嘉雨（`ember216`）、王钰滢（`wtr999-jpg`） |
| 评审日期 | 2026-11-28 |
| 变更规模 | 5 个文件：4 个 reputation-application 测试类 + 1 个 reputation-domain 测试类 |
| 评审结论 | 通过（APPROVED），未发现阻塞问题 |

## 一、结论

声誉模块边界测试补齐到位，断言都落到具体状态、winnerId、余额数值、过期时间、异常类型或 Mockito 交互，与生产实现逐条比对一致：账户不存在 REP-4001、流水越权 REP-3002、悬赏角色/关闭/积分 10~500/天数 1~14、重复结算 REP-2002、自领自赏 REP-1007、冻结/支出/退款余额推演、徽章阈值、学期划分、排行榜幂等、总榜/周期榜读取来源等。

评审人本地复跑：声誉模块 56 例全绿（0 失败/错误/跳过），编译通过；本 PR 新增 28 例，王钰滢本地 28 个用例全绿。

> 覆盖率口径说明：PR 描述应用层 91.7%、领域 89.3%、全仓 182 例；评审人本地仅复跑声誉模块 56 例，覆盖率数字与全仓用例口径未在本地复算，待全仓 verify 时核对。

## 二、建议项（不阻塞合并）

### 🟡 建议改

**1. `ReputationApiImplTest` 偏薄：缺账户口径未测**

`summary` 缺失补 0、`reputations` 批量不补默认值，两者差异没有覆盖到。建议补一组无账户/空批量的用例，固定两种口径的返回形态。

**2. 声誉模块未纳入 CI 测试范围**

CI 的 `SPRINT_MODULES` 目前不含 `co-reputation`，阶段 2（单元测试）与阶段 4（覆盖率门禁）只在列出的模块上跑，这批新测试在 CI 里实际不会被执行，只有阶段 1 编译覆盖到。建议把 `co-reputation/co-reputation-domain`、`co-reputation/co-reputation-application` 加进 `SPRINT_MODULES`，否则覆盖率门禁对声誉模块仍然是空的。

### 🟢 可选

**3. `BadgeServiceTest` 覆盖偏窄**

目前仅授予 + 幂等两条，阈值匹配由领域 `BadgePolicy` 测试兜底，可接受；后续可按徽章档位补边界用例。

## 三、验证方式

评审人本地复跑声誉模块单测 56 例全绿、编译通过；新增 28 例逐一核对断言与实现一致。CI 阶段 2/4 尚未跑声誉模块（见建议 2），模块覆盖以本地与全仓后续 verify 为准。

## 四、后续

- `SPRINT_MODULES` 随 Step3 门禁收口一并收宽，纳入声誉模块；
- 覆盖率百分比待全仓 verify 复算后回填到缺陷清单与报告；
- 建议 1 的无账户/空批量口径排入后续 Sprint 补测。
