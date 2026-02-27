package com.fairshare.fairshare.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class GroupControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Create, get, list and patch a group")
    void createGetPatchListGroup() throws Exception {
        // create group
        String body = "{\"name\":\"Test Group\"}";
        String resp = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = mapper.readTree(resp);
        Long id = node.get("id").asLong();
        assertThat(node.get("name").asText()).isEqualTo("Test Group");
        assertThat(node.path("actorUserId").isNull()).isTrue();

        // add member
        String addMem = String.format("{\"name\":\"alice\",\"email\":\"alice+%d@example.com\"}", id);
        String addResp = mvc.perform(post("/groups/" + id + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addMem))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode addNode = mapper.readTree(addResp);
        assertThat(addNode.get("userId").asLong()).isGreaterThan(0);
        assertThat(addNode.get("name").asText()).isEqualTo("alice");

        // get group
        String getResp = mvc.perform(get("/groups/" + id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode getNode = mapper.readTree(getResp);
        assertThat(getNode.get("id").asLong()).isEqualTo(id);
        assertThat(getNode.get("members").isArray()).isTrue();
        assertThat(getNode.path("actorUserId").isNull()).isTrue();

        // patch name
        String patch = "{\"name\":\"Renamed\"}";
        String patched = mvc.perform(patch("/groups/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patch))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode patchedNode = mapper.readTree(patched);
        assertThat(patchedNode.get("name").asText()).isEqualTo("Renamed");
        assertThat(patchedNode.path("actorUserId").isNull()).isTrue();

        // list groups includes our group
        String list = mvc.perform(get("/groups?size=100")) // Fetch all groups
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paginatedResponse = mapper.readTree(list);
        JsonNode arr = paginatedResponse.get("items");
        assertThat(arr.isArray()).isTrue();
        boolean found = false;
        for (JsonNode g : arr) {
            if (g.get("id").asLong() == id) {
                assertThat(g.path("actorUserId").isNull()).isTrue();
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Adding a member with an existing email reuses the same user")
    void addMemberReusesExistingUserByEmail() throws Exception {
        String groupResp = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Email Reuse Group\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long groupId = mapper.readTree(groupResp).get("id").asLong();

        String firstAdd = mvc.perform(post("/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"alice\",\"email\":\"alice+%d@example.com\"}", groupId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long firstUserId = mapper.readTree(firstAdd).get("userId").asLong();

        String secondAdd = mvc.perform(post("/groups/" + groupId + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"name\":\"alice2\",\"email\":\"alice+%d@example.com\"}", groupId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long secondUserId = mapper.readTree(secondAdd).get("userId").asLong();
        assertThat(secondUserId).isEqualTo(firstUserId);

        String getResp = mvc.perform(get("/groups/" + groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode members = mapper.readTree(getResp).get("members");
        assertThat(members.isArray()).isTrue();
        assertThat(members.size()).isEqualTo(1);
    }
}
