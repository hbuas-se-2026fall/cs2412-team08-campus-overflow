package com.campusoverflow;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 问答模块的接口到持久化全链路：投票改投、评论一层回复、热门标签。
 * 使用真实 MySQL 8 与 Redis 7，验证 Controller → Service → Repository → Flyway 迁移。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "co.outbox.poll-interval-ms=200",
        "spring.flyway.clean-disabled=false"
})
class QuestionInteractionIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.39"))
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--ngram-token-size=2");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void 投票改投与评论层级限制走真实链路() throws Exception {
        register("2026101", "链路小明", "it2-ming@campus.edu.cn");
        register("2026102", "链路小红", "it2-hong@campus.edu.cn");
        MockHttpSession ming = login("2026101");
        MockHttpSession hong = login("2026102");

        long questionId = idOf(mvc.perform(post("/api/v1/questions").session(ming).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"红黑树的黑高为什么不变量如此重要？",
                                 "body":"插入调整里变色与旋转的顺序，为什么必须优先保证黑高一致？请给出直观解释。",
                                 "tags":["数据结构","红黑树"]}
                                """))
                .andExpect(status().isCreated()).andReturn());

        long answerId = idOf(mvc.perform(post("/api/v1/questions/" + questionId + "/answers").session(hong)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"body":"黑高一致才能保证最长路径不超过最短路径的两倍，旋转是把失衡的子树搬回合法形态。"}
                                """))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(put("/api/v1/votes").session(ming).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ANSWER\",\"targetId\":" + answerId + ",\"value\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreDelta").value(1));

        mvc.perform(get("/api/v1/questions/" + questionId).session(ming))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].score").value(1))
                .andExpect(jsonPath("$.answers[0].myVote").value(1));

        mvc.perform(put("/api/v1/votes").session(ming).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ANSWER\",\"targetId\":" + answerId + ",\"value\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreDelta").value(-2));

        mvc.perform(get("/api/v1/questions/" + questionId).session(ming))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers[0].score").value(-1))
                .andExpect(jsonPath("$.answers[0].myVote").value(-1));

        long commentId = idOf(mvc.perform(post("/api/v1/comments").session(hong).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"QUESTION\",\"targetId\":" + questionId
                                + ",\"body\":\"这个解释里的两倍关系能再展开一下吗？\"}"))
                .andExpect(status().isCreated()).andReturn());

        long replyId = idOf(mvc.perform(post("/api/v1/comments").session(ming).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"QUESTION\",\"targetId\":" + questionId
                                + ",\"parentId\":" + commentId + ",\"body\":\"因为红黑性质要求每条路径的黑节点数相同。\"}"))
                .andExpect(status().isCreated()).andReturn());

        mvc.perform(post("/api/v1/comments").session(hong).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"QUESTION\",\"targetId\":" + questionId
                                + ",\"parentId\":" + replyId + ",\"body\":\"那第二层回复应该被拒绝。\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QA-1016"));

        mvc.perform(post("/api/v1/comments").session(hong).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ANSWER\",\"targetId\":" + answerId
                                + ",\"parentId\":" + commentId + ",\"body\":\"跨目标回复应当被拒绝。\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("QA-1017"));

        mvc.perform(get("/api/v1/tags/popular").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='数据结构')]").exists())
                .andExpect(jsonPath("$[?(@.name=='红黑树')]").exists());
    }

    private long register(String username, String displayName, String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterPayload(username, displayName, email,
                                "Campus2026", "计算机工程学院", "计科2301"))))
                .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Campus2026\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode node = json.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    private record RegisterPayload(String username, String displayName, String email, String password,
                                   String college, String className) {
    }
}
