package com.securetrack.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DeviceSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testDeviceAccessOwnershipEnforcement() throws Exception {
        // 1. Register User A
        RegisterRequest userA = new RegisterRequest();
        userA.setEmail("usera@example.com");
        userA.setPassword("PasswordA123");
        userA.setFullName("User A");

        MvcResult resA = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userA)))
                .andExpect(status().isOk())
                .andReturn();

        String tokenA = objectMapper.readTree(resA.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // 2. Register User B
        RegisterRequest userB = new RegisterRequest();
        userB.setEmail("userb@example.com");
        userB.setPassword("PasswordB123");
        userB.setFullName("User B");

        MvcResult resB = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userB)))
                .andExpect(status().isOk())
                .andReturn();

        String tokenB = objectMapper.readTree(resB.getResponse().getContentAsString())
                .get("data").get("accessToken").asText();

        // 3. User A registers device
        DeviceDto deviceDto = new DeviceDto();
        deviceDto.setDeviceName("User A Phone");
        deviceDto.setModel("Pixel 8");
        deviceDto.setInstallationId("install-id-usera-device1");

        MvcResult devRes = mockMvc.perform(post("/api/devices")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deviceDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        long deviceId = objectMapper.readTree(devRes.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 4. User B attempts to access User A's device -> MUST FAIL
        mockMvc.perform(get("/api/devices/" + deviceId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
