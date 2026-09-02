package com.cart.exception;

import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void illegalArgumentExceptionReturns400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad input"));
    }

    @Test
    void illegalStateExceptionReturns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict occurred"));
    }

    @Test
    void genericExceptionReturns500() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
    }

    @Test
    void validationExceptionReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of());

        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}

@RestController
class TestController {

    @GetMapping("/test/bad-request")
    public void badRequest() {
        throw new IllegalArgumentException("Bad input");
    }

    @GetMapping("/test/conflict")
    public void conflict() {
        throw new IllegalStateException("Conflict occurred");
    }

    @GetMapping("/test/server-error")
    public void serverError() {
        throw new RuntimeException("Unexpected failure");
    }

    @PostMapping("/test/validation-error")
    public void validationError(@RequestBody @jakarta.validation.Valid TestDto dto) {
        throw new RuntimeException("Should not reach here");
    }
}

class TestDto {
    @NotBlank
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
