package com.aicommerce.platform.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc;

    @Autowired
    GlobalExceptionHandlerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void validationErrorIncludesPathFieldsAndRequestId() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .header(RequestIdFilter.HEADER_NAME, "validation-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, "validation-request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").value("validation-request"))
                .andExpect(jsonPath("$.path").value("/test/validate"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void unexpectedErrorDoesNotExposeStackTrace() throws Exception {
        mockMvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/test/failure"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void missingResourceRemainsNotFound() throws Exception {
        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/missing"));
    }

    @RestController
    @RequestMapping("/test")
    public static class TestController {

        @PostMapping("/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("sensitive internal detail");
        }
    }

    public record TestRequest(@NotBlank String name) {
    }
}
