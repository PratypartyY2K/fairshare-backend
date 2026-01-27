package com.fairshare.fairshare.expenses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairshare.fairshare.expenses.api.ConfirmSettlementsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ConfirmSettlementsIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Confirm settlements is idempotent by confirmationId")
    void confirmSettlementsIdempotent() throws Exception {
        // create group
        String group = "{\"name\":\"ConfirmGroup\"}";
        String gresp = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode gnode = mapper.readTree(gresp);
        Long gid = gnode.get("id").asLong();

        // add two members
        String m1 = "{\"name\":\"x\"}"; // Changed userName to name
        Long x = Long.valueOf(mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong());
        String m2 = "{\"name\":\"y\"}"; // Changed userName to name
        Long y = Long.valueOf(mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong());

        // manually apply a transfer between them using confirm endpoint with confirmationId
        String confirmationId = "confirm-abc-123";
        String body = String.format("{\"confirmationId\":\"%s\",\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":\"10.00\"}]}", confirmationId, x, y); // Changed amount to string

        // first confirm
        MvcResult result1 = mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()) // Changed to isOk()
                .andExpect(jsonPath("$.confirmationId").value(confirmationId))
                .andExpect(jsonPath("$.appliedTransfersCount").value(1))
                .andReturn();
        ConfirmSettlementsResponse resp1 = mapper.readValue(result1.getResponse().getContentAsString(), ConfirmSettlementsResponse.class);
        assertThat(resp1.confirmationId()).isEqualTo(confirmationId);
        assertThat(resp1.appliedTransfersCount()).isEqualTo(1);


        // check owes (historical) x owes y should be -10? Actually amountOwedHistorical computes obligations minus payments; since this is a confirmed transfer from x to y, payments increased, so owed decreases.
        String owes1 = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + x + "&toUserId=" + y)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amt1 = mapper.readTree(owes1).get("amount").decimalValue();

        // second confirm with same confirmationId should be idempotent (no double-apply)
        MvcResult result2 = mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()) // Changed to isOk()
                .andExpect(jsonPath("$.confirmationId").value(confirmationId))
                .andExpect(jsonPath("$.appliedTransfersCount").value(1)) // Still 1 as it's idempotent
                .andReturn();
        ConfirmSettlementsResponse resp2 = mapper.readValue(result2.getResponse().getContentAsString(), ConfirmSettlementsResponse.class);
        assertThat(resp2.confirmationId()).isEqualTo(confirmationId);
        assertThat(resp2.appliedTransfersCount()).isEqualTo(1); // Should still be 1 due to idempotency


        String owes2 = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + x + "&toUserId=" + y)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amt2 = mapper.readTree(owes2).get("amount").decimalValue();

        assertThat(amt2).isEqualByComparingTo(amt1);

        // Use header-based confirmation flow
        String confirmationIdHeader = "confirm-header-123";
        String bodyNoId = String.format("{\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":\"5.00\"}]}", x, y); // Changed amount to string

        // first confirm using header
        MvcResult result3 = mvc.perform(post("/groups/" + gid + "/settlements/confirm").header("Confirmation-Id", confirmationIdHeader).contentType(MediaType.APPLICATION_JSON).content(bodyNoId))
                .andExpect(status().isOk()) // Changed to isOk()
                .andExpect(jsonPath("$.confirmationId").value(confirmationIdHeader))
                .andExpect(jsonPath("$.appliedTransfersCount").value(1))
                .andReturn();
        ConfirmSettlementsResponse resp3 = mapper.readValue(result3.getResponse().getContentAsString(), ConfirmSettlementsResponse.class);
        assertThat(resp3.confirmationId()).isEqualTo(confirmationIdHeader);
        assertThat(resp3.appliedTransfersCount()).isEqualTo(1);


        String owes1Header = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + x + "&toUserId=" + y)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amt1Header = mapper.readTree(owes1Header).get("amount").decimalValue();

        // second confirm with same header should be idempotent
        MvcResult result4 = mvc.perform(post("/groups/" + gid + "/settlements/confirm").header("Confirmation-Id", confirmationIdHeader).contentType(MediaType.APPLICATION_JSON).content(bodyNoId))
                .andExpect(status().isOk()) // Changed to isOk()
                .andExpect(jsonPath("$.confirmationId").value(confirmationIdHeader))
                .andExpect(jsonPath("$.appliedTransfersCount").value(1)) // Still 1 as it's idempotent
                .andReturn();
        ConfirmSettlementsResponse resp4 = mapper.readValue(result4.getResponse().getContentAsString(), ConfirmSettlementsResponse.class);
        assertThat(resp4.confirmationId()).isEqualTo(confirmationIdHeader);
        assertThat(resp4.appliedTransfersCount()).isEqualTo(1); // Should still be 1 due to idempotency


        String owes2Header = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + x + "&toUserId=" + y)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amt2Header = mapper.readTree(owes2Header).get("amount").decimalValue();

        assertThat(amt2Header).isEqualByComparingTo(amt1Header);
    }
}
