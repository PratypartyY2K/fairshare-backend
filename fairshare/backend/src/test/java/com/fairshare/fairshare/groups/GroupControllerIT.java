package com.fairshare.fairshare.groups;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairshare.fairshare.groups.api.AddMemberResponse;
import com.fairshare.fairshare.groups.api.dto.CreateGroupRequest;
import com.fairshare.fairshare.groups.api.dto.GroupUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class GroupControllerIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void createAddListUpdateGroupFlow() throws Exception {
        // create group
        var req = new CreateGroupRequest("Trip");
        var res = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        var group = mapper.readTree(res.getResponse().getContentAsString());
        Long id = group.get("id").asLong();
        assertThat(group.get("name").asText()).isEqualTo("Trip");

        // add member
        var addReq = new com.fairshare.fairshare.groups.api.dto.AddMemberRequest("Alice");
        mvc.perform(post(String.format("/groups/%d/members", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(addReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice"));

        // update name via PATCH
        var update = new GroupUpdateRequest("Weekend Trip");
        mvc.perform(patch(String.format("/groups/%d", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Weekend Trip"));

        // list groups
        mvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Weekend Trip"));
    }
}
