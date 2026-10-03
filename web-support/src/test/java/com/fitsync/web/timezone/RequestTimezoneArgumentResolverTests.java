package com.fitsync.web.timezone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class RequestTimezoneArgumentResolverTests {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setCustomArgumentResolvers(new RequestTimezoneArgumentResolver())
                .build();
    }

    @Test
    void headerIsHonoured() throws Exception {
        mvc.perform(get("/probe").header("X-Timezone", "Asia/Singapore"))
                .andExpect(status().isOk())
                .andExpect(content().string("Asia/Singapore"));
    }

    @Test
    void invalidHeaderIsIgnoredNotAnError() throws Exception {
        mvc.perform(get("/probe").header("X-Timezone", "+08:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("none"));
    }

    @Test
    void missingHeaderFallsBackToEmpty() throws Exception {
        mvc.perform(get("/probe")).andExpect(status().isOk()).andExpect(content().string("none"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe(@RequestTimezone Optional<ZoneId> zone) {
            return zone.map(ZoneId::getId).orElse("none");
        }
    }
}
