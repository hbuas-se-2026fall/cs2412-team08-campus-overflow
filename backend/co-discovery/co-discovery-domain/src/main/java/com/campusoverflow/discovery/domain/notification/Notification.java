package com.campusoverflow.discovery.domain.notification;

import com.campusoverflow.shared.domain.BusinessRuleException;
import com.campusoverflow.shared.domain.ForbiddenException;
import java.time.Instant;
import java.util.Objects;

/** 站内通知。(sourceEventId, recipientId) 唯一，事件重复投递不会产生重复通知。 */
public class Notification {

    private Long id;
    private final long recipientId;
    private final NotificationType type;
    private final String title;
    private final String link;
    private final String sourceEventId;
    private boolean read;
    private final Instant createdAt;

    private Notification(Long id, long recipientId, NotificationType type, String title, String link,
                         String sourceEventId, boolean read, Instant createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.type = Objects.requireNonNull(type);
        this.title = title;
        this.link = link;
        this.sourceEventId = Objects.requireNonNull(sourceEventId);
        this.read = read;
        this.createdAt = createdAt;
    }

    public static final int MAX_TITLE_LENGTH = 120;

    public static Notification create(long recipientId, NotificationType type, String title, String link,
                                      String sourceEventId, Instant now) {
        if (recipientId <= 0) {
            throw new BusinessRuleException("DIS-3001", "通知接收人不能为空");
        }
        Objects.requireNonNull(type, "通知类型不能为空");
        if (title == null || title.isBlank()) {
            throw new BusinessRuleException("DIS-3002", "通知标题不能为空");
        }
        String normalizedTitle = title.strip();
        if (normalizedTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessRuleException("DIS-3002", "通知标题不能超过 " + MAX_TITLE_LENGTH + " 个字符");
        }
        String normalizedLink = link == null || link.isBlank() ? null : link.strip();
        if (sourceEventId == null || sourceEventId.isBlank()) {
            throw new BusinessRuleException("DIS-3003", "通知必须记录来源事件标识");
        }
        Objects.requireNonNull(now, "通知创建时间不能为空");
        return new Notification(null, recipientId, type, normalizedTitle, normalizedLink, sourceEventId.strip(),
                false, now);
    }

    public static Notification reconstitute(Long id, long recipientId, NotificationType type, String title,
                                            String link, String sourceEventId, boolean read, Instant createdAt) {
        return new Notification(id, recipientId, type, title, link, sourceEventId, read, createdAt);
    }

    /** 只有接收人可以标记已读；重复标记是幂等的。 */
    public void markRead(long actorId) {
        if (actorId != recipientId) {
            throw new ForbiddenException("DIS-3004", "只有通知接收人可以标记为已读");
        }
        this.read = true;
    }

    public void assignId(long id) {
        this.id = id;
    }

    public Long id() { return id; }
    public long recipientId() { return recipientId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String link() { return link; }
    public String sourceEventId() { return sourceEventId; }
    public boolean read() { return read; }
    public Instant createdAt() { return createdAt; }
}
