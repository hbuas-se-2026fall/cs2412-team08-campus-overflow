package com.campusoverflow.identity.application;

import com.campusoverflow.identity.domain.User;
import com.campusoverflow.identity.domain.UserRepository;
import com.campusoverflow.shared.audit.AuditTrail;
import com.campusoverflow.shared.domain.ConflictException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用例：用户更新自己的公开资料（昵称、邮箱）。 */
@Service
public class ProfileService {

    private final UserRepository users;
    private final AuditTrail audit;
    private final UserQueryService userQuery;

    public ProfileService(UserRepository users, AuditTrail audit, UserQueryService userQuery) {
        this.users = users;
        this.audit = audit;
        this.userQuery = userQuery;
    }

    @Transactional
    public MeView updateProfile(Actor actor, UpdateProfileCommand cmd) {
        User user = users.findById(actor.userId())
                .orElseThrow(() -> new NotFoundException("ID-4001", "用户不存在"));
        String oldDisplayName = user.displayName();
        String oldEmail = user.email();
        // 昵称/邮箱唯一性校验（排除自己；邮箱大小写不敏感，由仓储按小写比对）
        String normEmail = cmd.email().toLowerCase();
        if (users.existsByDisplayName(cmd.displayName())
                && !cmd.displayName().equalsIgnoreCase(user.displayName())) {
            throw new ConflictException("ID-2002", "该昵称已被使用");
        }
        if (users.existsByEmail(normEmail) && !normEmail.equalsIgnoreCase(user.email())) {
            throw new ConflictException("ID-2003", "该邮箱已被使用");
        }
        user.updateProfile(actor, cmd.displayName(), cmd.email());
        users.save(user);
        audit.record("PROFILE_UPDATED", actor.userId(), "USER", String.valueOf(user.id()),
                oldDisplayName + " -> " + user.displayName() + "; "
                        + maskEmail(oldEmail) + " -> " + maskEmail(user.email()));
        // 复用 UserQueryService.me 组装完整 MeView（含课程徽章），与 GET /me 返回一致
        return userQuery.me(user.id());
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
}
