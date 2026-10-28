@pb-01 @us-001 @identity
Feature: 注册校园账号
  作为 未注册的学生
  我想 用学号、昵称和校园邮箱注册账号
  以便 拥有平台访问权限并提问、回答

  # 编号口径：PB-01 为实验指导书 Sprint 1 承诺编号，对应 PO《ProductBacklog》US-001（3 SP）
  # 错误码口径：docs/错误码.md —— ID-1xxx 业务规则（422）、ID-2xxx 冲突（409）
  # 实现基线：co-identity 模块的 User 聚合 + UserRegistrationService + POST /api/v1/auth/register

  Background:
    Given 系统已开放学生自助注册
    And 注册接口为 POST /api/v1/auth/register

  @happy-path @SC-001
  Scenario: 快乐路径-提交合法信息注册成功
    Given 学号 "2023001217" 未被注册
    And 昵称 "小明" 未被占用
    And 邮箱 "Ming@Campus.edu" 未被注册
    When 我提交注册信息 学号"2023001217" 昵称"小明" 邮箱"Ming@Campus.edu" 密码"Campus2026"
    Then 系统返回 201 Created
    And 响应体包含新用户的 id
    And 账号角色为 STUDENT 且未通过校园身份认证
    And 账号状态为 ACTIVE，可以登录
    And 口令以 BCrypt 哈希存储，数据库中不存在明文口令

  @happy-path @SC-002
  Scenario: 快乐路径-邮箱大小写归一后落库
    When 我用邮箱 "Ming@Campus.edu" 提交注册
    Then 系统返回 201 Created
    And 账号中保存的邮箱为 "ming@campus.edu"
    When 另一用户用邮箱 "MING@CAMPUS.EDU" 提交注册
    Then 系统返回 409 ID-2003

  @edge @SC-003
  Scenario: 边界路径-学号恰好达到下限 4 位
    When 我提交注册信息 学号"a123" 昵称"小明" 邮箱"ming@campus.edu" 密码"Campus2026"
    Then 系统返回 201 Created

  @edge @SC-004
  Scenario: 边界路径-口令恰好达到下限 8 位且含字母与数字
    When 我提交注册信息 学号"2023001217" 昵称"小明" 邮箱"ming@campus.edu" 密码"Ab123456"
    Then 系统返回 201 Created

  @error @SC-005
  Scenario: 异常路径-学号不足 4 位
    When 我提交注册信息 学号"ab" 昵称"小明" 邮箱"ming@campus.edu" 密码"Campus2026"
    Then 系统返回 422 ID-1001
    And 提示"学号/工号应为 4–20 位字母或数字"

  @error @SC-006
  Scenario: 异常路径-昵称包含 @ 或空格
    When 我提交注册信息 学号"2023001217" 昵称"小 明" 邮箱"ming@campus.edu" 密码"Campus2026"
    Then 系统返回 422 ID-1002
    And 提示"昵称不能包含 @ 或空格"

  @error @SC-007
  Scenario: 异常路径-邮箱格式不合法
    When 我提交注册信息 学号"2023001217" 昵称"小明" 邮箱"not-an-email" 密码"Campus2026"
    Then 系统返回 422 ID-1003
    And 提示"邮箱格式不正确"

  @error @SC-008
  Scenario: 异常路径-口令只有字母没有数字
    When 我提交注册信息 学号"2023001217" 昵称"小明" 邮箱"ming@campus.edu" 密码"onlyletters"
    Then 系统返回 422 ID-1004
    And 提示"密码必须同时包含字母和数字"
    And 数据库未新增任何用户
    And 未发布 UserRegisteredEvent（不产生幽灵事件）

  @error @SC-009
  Scenario: 异常路径-学号已被注册
    Given 学号 "2023001217" 已被注册
    When 我再次用该学号提交注册
    Then 系统返回 409 ID-2001
    And 提示"该学号/工号已注册"

  @error @SC-010
  Scenario: 异常路径-昵称已被占用
    Given 昵称 "小明" 已被占用
    When 我用该昵称提交注册
    Then 系统返回 409 ID-2002
    And 提示"该昵称已被使用"

  @error @SC-011
  Scenario: 异常路径-邮箱已被注册
    Given 邮箱 "ming@campus.edu" 已被注册
    When 我用该邮箱提交注册
    Then 系统返回 409 ID-2003
    And 提示"该邮箱已被使用"

  @audit @SC-012
  Scenario: 注册成功写入审计流水且学号脱敏
    When 我提交注册信息 学号"2023001217" 昵称"小明" 邮箱"ming@campus.edu" 密码"Campus2026"
    Then 审计表新增一条 USER_REGISTERED 记录
    And 记录中的学号脱敏为 "2023****17"
