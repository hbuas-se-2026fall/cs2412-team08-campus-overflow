package com.campusoverflow.identity.interfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.campusoverflow.identity.application.AuthenticationAuditService;
import com.campusoverflow.identity.application.LoginThrottle;
import com.campusoverflow.identity.application.MeView;
import com.campusoverflow.identity.application.RegisterUserCommand;
import com.campusoverflow.identity.application.UserQueryService;
import com.campusoverflow.identity.application.UserRegistrationService;
import com.campusoverflow.identity.interfaces.AuthController.LoginRequest;
import com.campusoverflow.identity.interfaces.AuthController.RegisterRequest;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.ActorAware;
import com.campusoverflow.shared.security.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * US-002 登录 · 接口层测试。
 *
 * <p>AuthController 是登录用例的编排边界，这里验证三件事：
 * <ol>
 *   <li>认证成功后建立会话并返回本人资料；</li>
 *   <li>失败一律对外收敛为“账号或密码错误”，不泄露账号是否存在；</li>
 *   <li>防暴力破解（QS-08）在认证之前生效，失败与被限流都要留审计。</li>
 * </ol>
 */
@DisplayName("US-002 登录")
class AuthControllerLoginTest {

    private static final long MING_ID = 1L;

    private final UserRegistrationService registration = mock(UserRegistrationService.class);
    private final UserQueryService userQuery = mock(UserQueryService.class);
    private final FakeThrottle throttle = new FakeThrottle();
    private final RecordingAudit auditTrail = new RecordingAudit();
    private final AuthenticationAuditService authAudit = new AuthenticationAuditService(auditTrail);
    private final RecordingSecurityContextRepository contextRepository = new RecordingSecurityContextRepository();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(registration, userQuery, alwaysSucceeds(), contextRepository, throttle,
                authAudit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private AuthenticationManager alwaysSucceeds() {
        return authentication -> UsernamePasswordAuthenticationToken.authenticated(
                new TestPrincipal(MING_ID, "小明", Role.STUDENT), "credentials",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private AuthenticationManager alwaysFails() {
        return authentication -> {
            throw new BadCredentialsException("bad");
        };
    }

    private MeView mingView() {
        return new MeView(MING_ID, "2023001217", "小明", "ming@campus.edu", Role.STUDENT, false,
                "计算机工程学院", "计科2301", List.of());
    }

    @Test
    @DisplayName("SC-001 账号密码正确：返回本人资料、建立会话、清零失败计数并记录 LOGIN_SUCCESS")
    void loginSucceeds() {
        when(userQuery.me(MING_ID)).thenReturn(mingView());

        MeView view = controller.login(new LoginRequest("2023001217", "Campus2026"), request, response);

        assertThat(view.id()).isEqualTo(MING_ID);
        assertThat(view.displayName()).isEqualTo("小明");
        assertThat(contextRepository.savedContexts()).hasSize(1);
        assertThat(throttle.resets()).containsExactly("2023001217");
        assertThat(auditTrail.byAction("LOGIN_SUCCESS")).singleElement()
                .satisfies(e -> assertThat(e.detail()).isEqualTo("2023****17"));
    }

    @Test
    @DisplayName("SC-002 密码错误：对外只说“账号或密码错误”，内部累加失败计数并留审计")
    void loginFailsWithBadCredentials() {
        controller = new AuthController(registration, userQuery, alwaysFails(), contextRepository, throttle,
                authAudit);

        assertThatThrownBy(() -> controller.login(new LoginRequest("2023001217", "wrong-password"), request, response))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("账号或密码错误");

        assertThat(throttle.failuresOf("2023001217")).isEqualTo(1);
        assertThat(contextRepository.savedContexts()).isEmpty();
        assertThat(auditTrail.byAction("LOGIN_FAILURE")).singleElement()
                .satisfies(e -> assertThat(e.detail()).contains("2023****17"));
        verifyNoInteractions(userQuery);
    }

    @Test
    @DisplayName("SC-003 连续失败被限流：直接拒绝，不再走认证（防暴力破解 QS-08）")
    void loginBlockedByThrottle() {
        AuthenticationManager neverCalled = mock(AuthenticationManager.class);
        controller = new AuthController(registration, userQuery, neverCalled, contextRepository,
                new FakeThrottle().blocked(), authAudit);

        assertThatThrownBy(() -> controller.login(new LoginRequest("2023001217", "Campus2026"), request, response))
                .isInstanceOf(LockedException.class);

        verify(neverCalled, never()).authenticate(any(Authentication.class));
        assertThat(auditTrail.byAction("LOGIN_FAILURE")).singleElement()
                .satisfies(e -> assertThat(e.detail()).contains("THROTTLED"));
    }

    @Test
    @DisplayName("US-001 接口层：注册返回 201 Created 与新用户 id")
    void registerReturnsCreated() {
        when(registration.register(any(RegisterUserCommand.class))).thenReturn(MING_ID);

        ResponseEntity<Map<String, Long>> result = controller.register(new RegisterRequest("2023001217", "小明",
                "ming@campus.edu", "Campus2026", "计算机工程学院", "计科2301"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).containsEntry("id", MING_ID);
    }

    /** 会话中的安全主体：接口层通过 {@link ActorAware} 把它转换为领域 Actor。 */
    private record TestPrincipal(long userId, String displayName, Role role) implements ActorAware {

        @Override
        public Actor toActor() {
            return new Actor(userId, displayName, role);
        }
    }

    /** 登录限流替身：可预置封禁状态，并记录 reset / recordFailure。 */
    private static final class FakeThrottle implements LoginThrottle {

        private final Map<String, Integer> failures = new HashMap<>();
        private final List<String> resets = new ArrayList<>();
        private boolean blocked = false;

        FakeThrottle blocked() {
            this.blocked = true;
            return this;
        }

        @Override
        public boolean isBlocked(String username) {
            return blocked;
        }

        @Override
        public void recordFailure(String username) {
            failures.merge(username, 1, Integer::sum);
        }

        @Override
        public void reset(String username) {
            resets.add(username);
        }

        int failuresOf(String username) {
            return failures.getOrDefault(username, 0);
        }

        List<String> resets() {
            return List.copyOf(resets);
        }
    }

    /** 审计替身：只保留断言需要的最小信息。 */
    private static final class RecordingAudit implements AuditTrail {

        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void record(String action, Long actorId, String targetType, String targetId, String detail) {
            entries.add(new Entry(action, actorId, targetType, targetId, detail));
        }

        List<Entry> byAction(String action) {
            return entries.stream().filter(e -> e.action().equals(action)).toList();
        }

        private record Entry(String action, Long actorId, String targetType, String targetId, String detail) {
        }
    }

    /** 会话仓储替身：只关心“会话有没有被保存”，不关心存到哪（生产是 Spring Session + Redis，见 ADR-003）。 */
    private static final class RecordingSecurityContextRepository implements SecurityContextRepository {

        private final List<SecurityContext> saved = new ArrayList<>();

        @Override
        public SecurityContext loadContext(HttpRequestResponseHolder holder) {
            return SecurityContextHolder.createEmptyContext();
        }

        @Override
        public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
            saved.add(context);
        }

        @Override
        public boolean containsContext(HttpServletRequest request) {
            return !saved.isEmpty();
        }

        List<SecurityContext> savedContexts() {
            return List.copyOf(saved);
        }
    }
}
