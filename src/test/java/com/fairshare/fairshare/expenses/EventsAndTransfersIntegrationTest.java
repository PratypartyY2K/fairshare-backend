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
public class EventsAndTransfersIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Events and confirmed transfers endpoints")
    void eventsAndTransfers() throws Exception {
        // create group and members
        String group = "{\"name\":\"AuditGroup\"}";
        String gresp = mvc.perform(post("/groups").contentType(MediaType.APPLICATION_JSON).content(group)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long gid = mapper.readTree(gresp).get("id").asLong();

        String m1 = "{\"name\":\"p\"}";
        Long p = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();

        String m2 = "{\"name\":\"q\"}";
        Long q = mapper.readTree(mvc.perform(post("/groups/" + gid + "/members").contentType(MediaType.APPLICATION_JSON).content(m2)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("userId").asLong();

        // create expense
        String exp = String.format("{\"description\":\"Snack\",\"amount\":\"5.00\",\"payerUserId\":%d}", p);
        String eres = mvc.perform(post("/groups/" + gid + "/expenses").contentType(MediaType.APPLICATION_JSON).content(exp)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode en = mapper.readTree(eres);
        assertThat(new BigDecimal(en.get("amount").asText())).isEqualByComparingTo(new BigDecimal("5.00"));

        // query events
        String events = mvc.perform(get("/groups/" + gid + "/events")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode ev = mapper.readTree(events).get("items");
        assertThat(ev.isArray()).isTrue();
        assertThat(ev.size()).isGreaterThanOrEqualTo(1);

        // confirm a transfer
        String confirmationId = "audit-confirm-1";
        String body = String.format("{\"confirmationId\":\"%s\",\"transfers\":[{\"fromUserId\":%d,\"toUserId\":%d,\"amount\":\"1.00\"}]}", confirmationId, p, q);
        MvcResult result = mvc.perform(post("/groups/" + gid + "/settlements/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        ConfirmSettlementsResponse resp = mapper.readValue(result.getResponse().getContentAsString(), ConfirmSettlementsResponse.class);
        assertThat(resp.confirmationId()).isEqualTo(confirmationId);
        assertThat(resp.appliedTransfersCount()).isEqualTo(1);


        // query confirmed transfers
        String cts = mvc.perform(get("/groups/" + gid + "/confirmed-transfers")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode ctNode = mapper.readTree(cts).get("items");
        assertThat(ctNode.isArray()).isTrue();
        assertThat(ctNode.size()).isGreaterThanOrEqualTo(1);

        // query by confirmationId
        String cts2 = mvc.perform(get("/groups/" + gid + "/confirmed-transfers?confirmationId=" + confirmationId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode ctNode2 = mapper.readTree(cts2).get("items");
        assertThat(ctNode2.isArray()).isTrue();
        assertThat(ctNode2.size()).isGreaterThanOrEqualTo(1);
    }
}
