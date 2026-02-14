// java
package com.fairshare.fairshare.pagination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest
@AutoConfigureMockMvc
public class PaginationIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("List groups with pagination")
    void listGroupsWithPagination() throws Exception {
        // Create 5 groups
        for (int i = 0; i < 5; i++) {
            String groupName = "Group " + i;
            String group = String.format("{\"name\":\"%s\"}", groupName);
            mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group))
                    .andExpect(status().isCreated());
        }

        // Get first page with size 2
        String response = mvc.perform(get("/groups?page=0&size=2&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber())
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andReturn().getResponse().getContentAsString();

        JsonNode paginatedResponse = mapper.readTree(response);
        assertThat(paginatedResponse.get("totalItems").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(paginatedResponse.get("totalPages").asInt()).isGreaterThanOrEqualTo(3);

        // Get second page with size 2
        mvc.perform(get("/groups?page=1&size=2&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.currentPage").value(1));

        // Get last page
        mvc.perform(get("/groups?page=2&size=2&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$.currentPage").value(2));
    }

    @Test
    @DisplayName("List expenses with pagination and date filter")
    void listExpensesWithPaginationAndDateFilter() throws Exception {
        // Create group
        String group = "{\"name\":\"ExpensePaginationGroup\"}";
        String gresp = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long gid = mapper.readTree(gresp).get("id").asLong();

        // Add member
        String m1 = "{\"name\":\"payer\"}";
        Long payerId = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();

        // Create 5 expenses
        for (int i = 0; i < 5; i++) {
            String description = "Expense " + i;
            String exp = String.format("{\"description\":\"%s\",\"amount\":\"10.00\",\"payerUserId\":%d}", description, payerId);
            mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON).content(exp))
                    .andExpect(status().isCreated());
            Thread.sleep(10); // Ensure different creation times for sorting
        }

        // Get first page with size 2, sorted by createdAt desc
        mvc.perform(get("/groups/" + gid + "/expenses?page=0&size=2&sort=createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(2));

        // Filter by date (e.g., last 3 expenses)
        mvc.perform(get("/groups/" + gid + "/expenses?page=0&size=10&fromDate=2023-01-01T00:00:00Z&toDate=2025-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    @DisplayName("List events with pagination")
    void listEventsWithPagination() throws Exception {
        // Create group
        String group = "{\"name\":\"EventPaginationGroup\"}";
        String gresp = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long gid = mapper.readTree(gresp).get("id").asLong();

        // Add member (generates an event)
        String m1 = "{\"name\":\"user\"}";
        Long userId = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();

        // Create expense (generates an event)
        String exp = String.format("{\"description\":\"Test Expense\",\"amount\":\"10.00\",\"payerUserId\":%d}", userId);
        mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON).content(exp))
                .andExpect(status().isCreated());

        // Get events with pagination
        mvc.perform(get("/groups/" + gid + "/events?page=0&size=1&sort=createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("List confirmed transfers with pagination")
    void listConfirmedTransfersWithPagination() throws Exception {
        // Create group
        String group = "{\"name\":\"TransferPaginationGroup\"}";
        String gresp = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long gid = mapper.readTree(gresp).get("id").asLong();

        // Add members
        String m1 = "{\"name\":\"fromUser\"}";
        Long fromUserId = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();
        String m2 = "{\"name\":\"toUser\"}";
        Long toUserId = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();

        // Confirm 3 transfers
        for (int i = 0; i < 3; i++) {
            String confirmationId = "transfer-confirm-" + i;
            String body = String.format("{\"confirmationId\":\"%s\",\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":\"1.00\"}]}", confirmationId, fromUserId, toUserId);
            mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
            Thread.sleep(10); // Ensure different creation times for sorting
        }

        // Get first page with size 2
        mvc.perform(get("/groups/" + gid + "/confirmed-transfers?page=0&size=2&sort=createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(2));
    }

    @Test
    @DisplayName("Get ledger explanation")
    void getLedgerExplanation() throws Exception {
        // Create group
        String group = "{\"name\":\"LedgerExplanationGroup\"}";
        String gresp = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long gid = mapper.readTree(gresp).get("id").asLong();

        // Add members
        String m1 = "{\"name\":\"user1\"}";
        Long user1Id = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();
        String m2 = "{\"name\":\"user2\"}";
        Long user2Id = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();

        // Create expense where user1 pays 20, split equally
        String exp = String.format("{\"description\":\"Dinner\",\"amount\":\"20.00\",\"payerUserId\":%d,\"participantUserIds\":[%d,%d]}", user1Id, user1Id, user2Id);
        mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON).content(exp))
                .andExpect(status().isCreated());

        // Confirm transfer from user2 to user1 for 5.00
        String confirmationId = "ledger-explain-confirm";
        String transferBody = String.format("{\"confirmationId\":\"%s\",\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":\"5.00\"}]}", confirmationId, user2Id, user1Id);
        mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(transferBody))
                .andExpect(status().isOk());

        // Get ledger explanation
        String response = mvc.perform(get("/groups/" + gid + "/explanations/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanations").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode explanationNode = mapper.readTree(response);
        assertThat(explanationNode.get("explanations").size()).isEqualTo(2);

        // Verify user1's explanation
        JsonNode user1Explanation = explanationNode.get("explanations").get(0);
        if (user1Explanation.get("userId").asLong() != user1Id) {
            user1Explanation = explanationNode.get("explanations").get(1);
        }
        assertThat(user1Explanation.get("userId").asLong()).isEqualTo(user1Id);
        assertThat(new BigDecimal(user1Explanation.get("netBalance").asText())).isEqualByComparingTo(new BigDecimal("5.00")); // Paid 20, owes 10, received 5 = 5
        assertThat(user1Explanation.get("contributions").isArray()).isTrue();
        assertThat(user1Explanation.get("contributions").size()).isEqualTo(3); // Paid expense, share expense, received transfer

        // Verify user2's explanation
        JsonNode user2Explanation = explanationNode.get("explanations").get(0);
        if (user2Explanation.get("userId").asLong() == user1Id) {
            user2Explanation = explanationNode.get("explanations").get(1);
        }
        assertThat(user2Explanation.get("userId").asLong()).isEqualTo(user2Id);
        assertThat(new BigDecimal(user2Explanation.get("netBalance").asText())).isEqualByComparingTo(new BigDecimal("-5.00")); // Owes 10, sent 5 = -5
        assertThat(user2Explanation.get("contributions").isArray()).isTrue();
        assertThat(user2Explanation.get("contributions").size()).isEqualTo(2); // Share expense, sent transfer
    }
}
