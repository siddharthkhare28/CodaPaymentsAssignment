package com.codapayments.echo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EchoController.class)
class EchoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldEchoJsonBody() throws Exception {
        // Arrange
        Map<String, Object> requestBody = Map.of(
                "game", "Mobile Legends",
                "gamerID", "GYUTDTE",
                "points", 20
        );

        String jsonRequest = objectMapper.writeValueAsString(requestBody);

        // Act & Assert
        mockMvc.perform(post("/api/v1/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(content().json(jsonRequest));
    }
}
