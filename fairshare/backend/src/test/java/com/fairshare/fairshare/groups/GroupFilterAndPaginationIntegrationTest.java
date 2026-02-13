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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class GroupFilterAndPaginationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Name filter returns matching item even when requested page is out-of-range")
    void nameFilterOutOfRangePageReturnsItem() throws Exception {
        String uniqueName = "6 Log " + UUID.randomUUID();
        String createBody = String.format("{\"name\":\"%s\"}", uniqueName);

        String createResp = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = mapper.readTree(createResp);
        Long gid = created.get("id").asLong();
        assertThat(created.get("name").asText()).isEqualTo(uniqueName);

        // First ensure name filter with page=0 returns the item (use param to avoid + encoding issues)
        String listResp0 = mvc.perform(get("/groups")
                        .param("page", "0")
                        .param("pageSize", "10")
                        .param("name", uniqueName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paginated0 = mapper.readTree(listResp0);
        JsonNode items0 = paginated0.get("items");
        boolean found0 = false;
        for (JsonNode g : items0) {
            if (g.get("id").asLong() == gid) {
                found0 = true;
                break;
            }
        }
        assertThat(found0).isTrue();

        // Now request page=1 while there is only 1 matching item; service should return last page's items
        String listResp1 = mvc.perform(get("/groups")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("name", uniqueName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode paginated1 = mapper.readTree(listResp1);
        JsonNode items1 = paginated1.get("items");
        boolean found1 = false;
        for (JsonNode g : items1) {
            if (g.get("id").asLong() == gid) {
                found1 = true;
                break;
            }
        }
        assertThat(found1).isTrue();
    }

    @Test
    @DisplayName("pageSize query param acts as an alias for size")
    void pageSizeOverridesSize() throws Exception {
        // create a handful of groups to ensure multiple pages
        for (int i = 0; i < 7; i++) {
            String name = "PSGroup " + UUID.randomUUID();
            String body = String.format("{\"name\":\"%s\"}", name);
            mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        // Request with pageSize=3 and page=1 should return 3 items for the page
        String resp = mvc.perform(get("/groups?page=1&pageSize=3&sort=id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(3))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()", lessThanOrEqualTo(3)))
                .andReturn().getResponse().getContentAsString();

        JsonNode paginated = mapper.readTree(resp);
        assertThat(paginated.get("pageSize").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("memberCount reflects actual number of members on group get")
    void memberCountReflectsMembers() throws Exception {
        String name = "MemberCountGroup " + UUID.randomUUID();
        String body = String.format("{\"name\":\"%s\"}", name);

        String createResp = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode created = mapper.readTree(createResp);
        Long gid = created.get("id").asLong();

        // Add two members
        mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"alice\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"bob\"}"))
                .andExpect(status().isCreated());

        String getResp = mvc.perform(get("/groups/" + gid))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode group = mapper.readTree(getResp);
        assertThat(group.get("memberCount").asInt()).isEqualTo(2);
        assertThat(group.get("members").isArray()).isTrue();
        assertThat(group.get("members").size()).isEqualTo(2);
    }
}
