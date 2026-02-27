package com.fairshare.fairshare.users;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Create user persists and returns normalized email")
    void createUserReturnsEmail() throws Exception {
        String email = "Test.User+" + System.nanoTime() + "@Example.COM";
        String payload = "{\"name\":\"test-user\",\"email\":\"" + email + "\"}";

        String created = mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode createdNode = mapper.readTree(created);
        long userId = createdNode.get("id").asLong();
        assertThat(createdNode.get("email").asText()).isEqualTo(email.toLowerCase());

        String fetched = mvc.perform(get("/users/" + userId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fetchedNode = mapper.readTree(fetched);
        assertThat(fetchedNode.get("email").asText()).isEqualTo(email.toLowerCase());
    }

    @Test
    @DisplayName("Create user rejects duplicate email ignoring case")
    void createUserRejectsDuplicateEmail() throws Exception {
        String email = "dupe+" + System.nanoTime() + "@example.com";
        String firstPayload = "{\"name\":\"first\",\"email\":\"" + email + "\"}";
        String secondPayload = "{\"name\":\"second\",\"email\":\"" + email.toUpperCase() + "\"}";

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPayload))
                .andExpect(status().isCreated());

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPayload))
                .andExpect(status().isBadRequest());
    }
}
