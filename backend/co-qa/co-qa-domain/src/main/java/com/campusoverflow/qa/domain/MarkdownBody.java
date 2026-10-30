package com.campusoverflow.qa.domain;

import com.campusoverflow.shared.domain.Guard;

/** Markdown 正文。只保存原文；渲染与 XSS 净化在基础设施层完成。 */
public record MarkdownBody(String value) {

    /** 问题正文：15–20000 个字符。 */
    public static final int QUESTION_MIN = 15;
    public static final int QUESTION_MAX = 20_000;

    /** 回答正文：10–10000 个字符。 */
    public static final int ANSWER_MIN = 10;
    public static final int ANSWER_MAX = 10_000;

    public static MarkdownBody ofQuestion(String raw) {
        return new MarkdownBody(Guard.requireText(raw, QUESTION_MIN, QUESTION_MAX, "QA-1002", "问题正文"));
    }

    public static MarkdownBody ofAnswer(String raw) {
        return new MarkdownBody(Guard.requireText(raw, ANSWER_MIN, ANSWER_MAX, "QA-1002", "回答正文"));
    }
}
