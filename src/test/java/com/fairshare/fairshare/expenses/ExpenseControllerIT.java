package com.fairshare.fairshare.expenses;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fairshare.fairshare.expenses.api.CreateExpenseRequest;
import com.fairshare.fairshare.expenses.api.ConfirmSettlementsRequest;
import com.fairshare.fairshare.groups.api.dto.CreateGroupRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ExpenseControllerIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void createExpenseAndConfirmSettlementsFlow() throws Exception {
        // create group
        var groupReq = new CreateGroupRequest("Household");
        var groupRes = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(groupReq)))
                .andExpect(status().isCreated())
                .andReturn();

        var groupJson = mapper.readTree(groupRes.getResponse().getContentAsString());
        Long gid = groupJson.get("id").asLong();

        // add two members
        var addA = new com.fairshare.fairshare.groups.api.dto.AddMemberRequest("Bob");
        var addB = new com.fairshare.fairshare.groups.api.dto.AddMemberRequest("Carol");

        var r1 = mvc.perform(post(String.format("/groups/%d/members", gid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(addA)))
                .andExpect(status().isCreated())
                .andReturn();
        var u1 = mapper.readTree(r1.getResponse().getContentAsString()).get("userId").asLong();

        var r2 = mvc.perform(post(String.format("/groups/%d/members", gid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(addB)))
                .andExpect(status().isCreated())
                .andReturn();
        var u2 = mapper.readTree(r2.getResponse().getContentAsString()).get("userId").asLong();

        // create expense paid by Bob for 30 split between both
        var expReq = new CreateExpenseRequest("Groceries", new BigDecimal("30.00"), u1, List.of(u1, u2));
        mvc.perform(post(String.format("/groups/%d/expenses", gid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(expReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(30.00))
                .andExpect(jsonPath("$.payerUserId").value(u1));

        // ledger should show balances: Bob +15, Carol -15
        mvc.perform(get(String.format("/groups/%d/ledger", gid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[?(@.userId==%d)].netBalance", u1).exists())
                .andExpect(jsonPath("$.entries[?(@.userId==%d)].netBalance", u2).exists());

        // compute settlements
        var settlement = mvc.perform(get(String.format("/groups/%d/settlements", gid)))
                .andExpect(status().isOk())
                .andReturn();

        var settlementJson = mapper.readTree(settlement.getResponse().getContentAsString());
        var transfers = settlementJson.get("transfers");
        // expect one transfer from Carol to Bob
        Long from = transfers.get(0).get("fromUserId").asLong();
        Long to = transfers.get(0).get("toUserId").asLong();
        BigDecimal amount = new BigDecimal(transfers.get(0).get("amount").asText());

        // verify owes endpoint before confirm
        mvc.perform(get(String.format("/groups/%d/owes?fromUserId=%d&toUserId=%d", gid, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(amount.doubleValue()));

        var confirm = new ConfirmSettlementsRequest(List.of(new ConfirmSettlementsRequest.Transfer(from, to, amount)));
        // confirm settlements
        mvc.perform(post(String.format("/groups/%d/settlements/confirm", gid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(confirm)))
                .andExpect(status().isNoContent());

        // owes should be zero after confirming
        mvc.perform(get(String.format("/groups/%d/owes?fromUserId=%d&toUserId=%d", gid, from, to)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(0.00));

        // ledger now should be all zeros or closer to zero
        mvc.perform(get(String.format("/groups/%d/ledger", gid)))
                .andExpect(status().isOk());
    }
}
