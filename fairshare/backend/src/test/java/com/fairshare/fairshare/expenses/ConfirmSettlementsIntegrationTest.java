package com.fairshare.fairshare.expenses;

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
        String m1 = "{\"userName\":\"x\"}";
        Long x = Long.valueOf(mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong());
        String m2 = "{\"userName\":\"y\"}";
        Long y = Long.valueOf(mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong());

        // manually apply a transfer between them using confirm endpoint with confirmationId
        String confirmationId = "confirm-abc-123";
        String body = String.format("{\"confirmationId\":\"%s\",\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":10.00}]}", confirmationId, x, y);

        // first confirm
        mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        // check owes (historical) x owes y should be -10? Actually amountOwedHistorical computes obligations minus payments; since this is a confirmed transfer from x to y, payments increased, so owed decreases.
        String owes1 = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + x + "&toUserId=" + y)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amt1 = mapper.readTree(owes1).get("amount").decimalValue();

        // second confirm with same confirmationId should be idempotent (no double-apply)
        mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());

        String owes2 = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + x + "&toUserId=" + y)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        BigDecimal amt2 = mapper.readTree(owes2).get("amount").decimalValue();

        assertThat(amt2).isEqualByComparingTo(amt1);

        // ensure only one confirmed transfer persisted with this confirmation id (query the confirmed transfers list endpoint not present; we can try creating a new confirmation with different id and see ledger changes differ)
        // For now, just assert idempotency via ledger amounts above.
    }
}
