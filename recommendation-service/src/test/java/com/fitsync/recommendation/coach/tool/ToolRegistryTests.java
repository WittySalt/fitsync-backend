package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ToolRegistryTests {

    private static final CoachCallContext CALL = new CoachCallContext(UUID.randomUUID(), "token", UUID.randomUUID());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoachThreadService threadService = mock(CoachThreadService.class);

    @Test
    @DisplayName("a record that cannot be written leaves the tool's result as it was")
    void keepsTheResultWhenTheRecordFails() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(threadService)
                .recordToolInvocation(any(), any(), any(), any(), anyInt(), anyBoolean(), any());
        ToolRegistry registry = new ToolRegistry(List.of(tool("readFacts")), threadService, objectMapper);

        ToolResult result = registry.invoke(CALL, "readFacts", objectMapper.createObjectNode());

        assertThat(result.summary()).isEqualTo("readFacts ran");
        assertThat(result.observation()).isEqualTo("the facts");
    }

    @Test
    @DisplayName("the records of one turn are written one after another")
    void writesTheRecordsOfOneTurnOneAtATime() throws Exception {
        AtomicInteger writing = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        doAnswer(invocation -> {
            mostAtOnce.accumulateAndGet(writing.incrementAndGet(), Math::max);
            Thread.sleep(40);
            writing.decrementAndGet();
            return null;
        }).when(threadService).recordToolInvocation(any(), any(), any(), any(), anyInt(), anyBoolean(), any());
        ToolRegistry registry = new ToolRegistry(
                List.of(tool("readFacts"), tool("readRecentTraining")), threadService, objectMapper);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<ToolResult> first = pool.submit(() -> {
                start.await();
                return registry.invoke(CALL, "readFacts", objectMapper.createObjectNode());
            });
            Future<ToolResult> second = pool.submit(() -> {
                start.await();
                return registry.invoke(CALL, "readRecentTraining", objectMapper.createObjectNode());
            });
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).summary()).isEqualTo("readFacts ran");
            assertThat(second.get(5, TimeUnit.SECONDS).summary()).isEqualTo("readRecentTraining ran");
        } finally {
            pool.shutdownNow();
        }

        assertThat(mostAtOnce.get()).isEqualTo(1);
    }

    private static CoachTool tool(String name) {
        return new CoachTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ToolClass toolClass() {
                return ToolClass.READ;
            }

            @Override
            public String promptSignature() {
                return name + "()";
            }

            @Override
            public ToolResult invoke(CoachCallContext context, JsonNode args) {
                return ToolResult.of(name + " ran", "the facts");
            }
        };
    }
}
