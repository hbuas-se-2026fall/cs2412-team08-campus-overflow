@pb-02 @us-002 @identity
Feature: 用学号与密码登录
  作为 已注册的学生
  我想 用学号和密码登录
  以便 使用提问与回答功能

  # 编号口径：PB-02 为实验指导书 Sprint 1 承诺编号，对应 PO《ProductBacklog》US-002（2 SP）
  # 错误码口径：docs/错误码.md —— AUTH-401 未登录、AUTH-429 登录失败过多
  # 实现基线：POST /api/v1/auth/login（服务端会话 + HttpOnly Cookie，见 ADR-003，不用长效 JWT）

  Background:
    Given 学号 "2023001217" 的账号已注册且状态为 ACTIVE
    And 登录接口为 POST /api/v1/auth/login

  @happy-path @SC-001
  Scenario: 快乐路径-学号与密码正确
    Given 我未登录
    When 我提交学号 "2023001217" 与正确密码
    Then 系统返回 200 OK
    And 响应体包含 id、昵称、角色与认证状态
    And 服务端建立会话并下发 HttpOnly Cookie
    And 该学号的登录失败计数被清零
    And 审计表新增一条 LOGIN_SUCCESS 记录

  @happy-path @SC-002
  Scenario: 快乐路径-登录后可以查询本人资料
    Given 我已登录
    When 我请求 GET /api/v1/auth/me
    Then 系统返回 200 OK
    And 返回的学号为 "2023001217"

  @edge @SC-003
  Scenario: 边界路径-密码恰好 8 位且含字母与数字
    Given 我的密码为 "Ab123456"
    When 我用该密码登录
    Then 系统返回 200 OK

  @error @SC-004
  Scenario: 异常路径-密码错误
    When 我提交学号 "2023001217" 与错误密码
    Then 系统返回 401
    And 提示"账号或密码错误"（不区分账号不存在与密码错误，避免账号枚举）
    And 该学号的登录失败计数加 1
    And 审计表新增一条 LOGIN_FAILURE 记录且学号脱敏为 "2023****17"

  @error @SC-005
  Scenario: 异常路径-账号不存在
    Given 学号 "2099000000" 未注册
    When 我用该学号与任意密码登录
    Then 系统返回 401
    And 提示"账号或密码错误"

  @error @SC-006
  Scenario: 异常路径-账号已被管理员禁用
    Given 我的账号状态为 DISABLED
    When 我用正确密码登录
    Then 系统返回 401
    And 提示"账号或密码错误"（不向调用方泄露账号被禁用）

  @error @SC-007
  Scenario: 异常路径-1 分钟内连续失败 5 次被限流
    Given 我在 1 分钟内已连续 5 次密码错误
    When 我再次提交登录
    Then 系统返回 429 AUTH-429
    And 提示"登录失败次数过多，请 1 分钟后再试"
    And 本次请求不再执行密码校验
    And 审计表新增一条 LOGIN_FAILURE 记录且原因标记为 THROTTLED

  @error @SC-008
  Scenario: 异常路径-未登录访问受保护接口
    Given 我未登录
    When 我请求 GET /api/v1/auth/me
    Then 系统返回 401 AUTH-401

  @security @SC-009
  Scenario: 会话固定攻击防护-登录成功后会话 ID 轮换
    Given 我已在登录页拿到一个匿名会话
    When 我登录成功
    Then 会话 ID 与登录前不同
