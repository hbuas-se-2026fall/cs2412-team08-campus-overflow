package com.campusoverflow.qa.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.qa.application.fake.QaTestFixtures.CountingCache;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubContentRenderer;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubViewCounter;
import com.campusoverflow.qa.application.fake.QaTestFixtures.InMemoryReadModel;
import com.campusoverflow.qa.application.fake.QaTestFixtures.StubIdentity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 热门标签查询用例测试：limit 边界裁剪与读模型转发。 */
class QuestionQueryServiceTest {

    private final InMemoryReadModel readModel = new InMemoryReadModel();
    private QuestionQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new QuestionQueryService(readModel, new StubIdentity(), new StubContentRenderer(), new StubViewCounter(), new CountingCache());
    }

    @Test
    @DisplayName("热门标签：原样转发读模型结果")
    void delegatesToReadModel() {
        readModel.tags = List.of(new TagView("java", 12L), new TagView("红黑树", 8L));

        List<TagView> result = queryService.popularTags(20);

        assertThat(result).containsExactlyElementsOf(readModel.tags);
        assertThat(readModel.lastPopularLimit).isEqualTo(20);
    }

    @Test
    @DisplayName("热门标签：limit 裁剪到 1–50")
    void clampsLimit() {
        queryService.popularTags(0);
        assertThat(readModel.lastPopularLimit).isEqualTo(1);
        queryService.popularTags(-5);
        assertThat(readModel.lastPopularLimit).isEqualTo(1);
        queryService.popularTags(999);
        assertThat(readModel.lastPopularLimit).isEqualTo(50);
    }
}
