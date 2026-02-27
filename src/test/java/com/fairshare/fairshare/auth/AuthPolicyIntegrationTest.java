package com.fairshare.fairshare.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "fairshare.auth.required=true")
class AuthPolicyIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Missing X-User-Id is rejected when auth is required")
    void missingHeaderRejected() throws Exception {
        mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No Auth Group\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Owner-only actions and membership-based reads are enforced")
    void ownerAndMembershipPolicy() throws Exception {
        Long ownerId = createUser("owner");
        Long outsiderId = createUser("outsider");
        Long memberId = createUser("member");

        String created = mvc.perform(post("/groups")
                        .header(AuthContext.USER_ID_HEADER, String.valueOf(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Policy Group\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode createdNode = mapper.readTree(created);
        long groupId = createdNode.get("id").asLong();
        assertThat(createdNode.path("actorUserId").asLong()).isEqualTo(ownerId);

        mvc.perform(get("/groups/" + groupId)
                        .header(AuthContext.USER_ID_HEADER, String.valueOf(outsiderId)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/groups/" + groupId + "/members")
                        .header(AuthContext.USER_ID_HEADER, String.valueOf(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + memberId + "}"))
                .andExpect(status().isCreated());

        String groupAsMember = mvc.perform(get("/groups/" + groupId)
                        .header(AuthContext.USER_ID_HEADER, String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode groupAsMemberNode = mapper.readTree(groupAsMember);
        assertThat(groupAsMemberNode.path("actorUserId").asLong()).isEqualTo(memberId);

        mvc.perform(patch("/groups/" + groupId)
                        .header(AuthContext.USER_ID_HEADER, String.valueOf(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed By Member\"}"))
                .andExpect(status().isForbidden());

        String listAsMember = mvc.perform(get("/groups")
                        .header(AuthContext.USER_ID_HEADER, String.valueOf(memberId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode items = mapper.readTree(listAsMember).get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThan(0);
        boolean found = false;
        for (JsonNode item : items) {
            if (item.path("id").asLong() == groupId) {
                assertThat(item.path("actorUserId").asLong()).isEqualTo(memberId);
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    private Long createUser(String name) throws Exception {
        String response = mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asLong();
    }
}
