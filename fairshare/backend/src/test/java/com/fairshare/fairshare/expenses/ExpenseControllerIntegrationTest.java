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
public class ExpenseControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Create expense validations and confirm settlements")
    void createExpenseAndConfirm() throws Exception {
        // create group
        String group = "{\"name\":\"ExpGroup\"}";
        String gresp = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode gnode = mapper.readTree(gresp);
        Long gid = gnode.get("id").asLong();

        // add two members
        String m1 = "{\"userName\":\"alice\"}";
        String r1 = mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode rn1 = mapper.readTree(r1);
        Long aliceId = rn1.get("userId").asLong();

        String m2 = "{\"userName\":\"bob\"}";
        String r2 = mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode rn2 = mapper.readTree(r2);
        Long bobId = rn2.get("userId").asLong();

        // create expense with payer alice and participants omitted (defaults to all members)
        String exp = String.format("{\"description\":\"Lunch\",\"amount\":30.00,\"paidByUserId\":%d}", aliceId);
        String eresp = mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON).content(exp))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode enode = mapper.readTree(eresp);
        assertThat(enode.get("amount").decimalValue()).isEqualByComparingTo(new BigDecimal("30.00"));

        // list ledger
        String ledger = mvc.perform(get("/groups/" + gid + "/ledger")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode lnode = mapper.readTree(ledger);
        assertThat(lnode.get("entries").isArray()).isTrue();

        // get settlement suggestions
        String sresp = mvc.perform(get("/groups/" + gid + "/settlements")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode sn = mapper.readTree(sresp);
        assertThat(sn.get("transfers").isArray()).isTrue();

        // confirm a settlement (if any suggested)
        if (sn.get("transfers").size() > 0) {
            JsonNode first = sn.get("transfers").get(0);
            Long from = first.get("fromUserId").asLong();
            Long to = first.get("toUserId").asLong();
            String amt = first.get("amount").decimalValue().toString();
            String confirm = String.format("{\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":%s}]}", from, to, amt);
            mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(confirm))
                    .andExpect(status().isNoContent());

            // outstanding owed historical should now reflect confirmed transfer
            String owes = mvc.perform(get("/groups/" + gid + "/owes/historical?fromUserId=" + from + "&toUserId=" + to)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode ow = mapper.readTree(owes);
            // amount should be >= 0
            assertThat(ow.get("amount").decimalValue().signum()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("Precision and leftover distribution for percentages")
    void precisionAndLeftoverDistribution() throws Exception {
        // create group
        String group = "{\"name\":\"PrecGroup\"}";
        String gresp = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(group))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode gnode = mapper.readTree(gresp);
        Long gid = gnode.get("id").asLong();

        // add three members
        String m1 = "{\"userName\":\"a\"}";
        String r1 = mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long a = mapper.readTree(r1).get("userId").asLong();

        String m2 = "{\"userName\":\"b\"}";
        String r2 = mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long b = mapper.readTree(r2).get("userId").asLong();

        String m3 = "{\"userName\":\"c\"}";
        String r3 = mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m3)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long c = mapper.readTree(r3).get("userId").asLong();

        // create expense of 100.00 with percentages [33.33,33.33,33.34]
        String exp = String.format("{\"description\":\"Bill\",\"amount\":100.00,\"paidByUserId\":%d,\"participantUserIds\":[%d,%d,%d],\"percentages\":[33.33,33.33,33.34]}", a, a, b, c);
        String eresp = mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON).content(exp))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode enode = mapper.readTree(eresp);

        // verify amount and splits sum to total
        BigDecimal total = enode.get("amount").decimalValue();
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode s : enode.get("splits")) {
            sum = sum.add(s.get("shareAmount").decimalValue());
        }
        assertThat(total).isEqualByComparingTo(sum);

        // verify each split has scale 2
        for (JsonNode s : enode.get("splits")) {
            BigDecimal v = s.get("shareAmount").decimalValue();
            assertThat(v.scale()).isEqualTo(2);
        }
    }
}
