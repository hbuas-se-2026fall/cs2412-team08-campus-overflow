package com.campusoverflow.reputation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

/**
 * 排行榜周期键。学期划分（可按学校校历调整）：2–7 月为春季学期，8–12 月与次年 1 月为秋季学期。
 */
public final class AcademicTerm {

    public static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private AcademicTerm() {
    }

    public static String termKey(Instant at) {
        LocalDate d = at.atZone(CAMPUS_ZONE).toLocalDate();
        int m = d.getMonthValue();
        int y = d.getYear();
        if (m >= 2 && m <= 7) {
            return y + "-SPRING";
        } else if (m >= 8) {
            return y + "-FALL";
        } else { // m == 1，属于上一年秋季学期
            return (y - 1) + "-FALL";
        }
    }

    public static String weekKey(Instant at) {
        LocalDate d = at.atZone(CAMPUS_ZONE).toLocalDate();
        int week = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = d.get(IsoFields.WEEK_BASED_YEAR);
        return year + "-W" + String.format("%02d", week);
    }

    public static String monthKey(Instant at) {
        YearMonth ym = YearMonth.from(at.atZone(CAMPUS_ZONE));
        return String.format("%d-%02d", ym.getYear(), ym.getMonthValue());
    }
}
