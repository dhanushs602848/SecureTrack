package com.securetrack.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetrack.backend.dto.CommandDto;
import com.securetrack.backend.dto.CommandStatusUpdateRequest;
import com.securetrack.backend.dto.DeviceDto;
import com.securetrack.backend.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CommandLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCommandLifecycleTransitions() throws Exception {
        // 1. Register User & Device
        RegisterRequest user = new RegisterRequest();
        user.setEmail("cmduser@example.com");
        user.setPassword("Password123!");
        user.setFullName("Cmd User");

        MvcResult userRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(userRes.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        DeviceDto deviceDto = new DeviceDto();
        deviceDto.setDeviceName("Command Device");
        deviceDto.setInstallationId("install-id-cmd-device");

        MvcResult devRes = mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceDto)))
                .andExpect(status().isOk())
                .andReturn();

        long deviceId = objectMapper.readTree(devRes.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 2. Dispatch command -> QUEUED
        CommandDto cmd = new CommandDto();
        cmd.setCommandType("LOCK_DEVICE");
        cmd.setPayload("Manual test lock");

        MvcResult cmdRes = mockMvc.perform(post("/api/devices/" + deviceId + "/commands")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cmd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andReturn();

        long commandId = objectMapper.readTree(cmdRes.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 3. Poll pending commands -> DELIVERED
        mockMvc.perform(get("/api/devices/" + deviceId + "/commands/pending")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("DELIVERED"));

        // 4. Endpoint updates status -> EXECUTED
        CommandStatusUpdateRequest statusUpdate = new CommandStatusUpdateRequest();
        statusUpdate.setStatus("EXECUTED");
        statusUpdate.setResult("Device locked by DevicePolicyManager");

        mockMvc.perform(put("/api/commands/" + commandId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXECUTED"))
                .andExpect(jsonPath("$.data.result").value("Device locked by DevicePolicyManager"));
    }
}
