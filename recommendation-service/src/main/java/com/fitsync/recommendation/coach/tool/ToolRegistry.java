package com.fitsync.recommendation.coach.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The one place a tool is looked up, described to the model, and invoked.
 *
 * <p>Two of the tools here read catalogues owned by other bounded contexts. Funnelling them through
 * a registry rather than letting the orchestrator call clients directly means cross-context access
 * is mediated in a single place and audited without anybody remembering to: every invocation leaves
 * a row saying which tool was asked, with what arguments, how long it took, and what came back in
 * outline.
 *
 * <p>The prompt block is generated from the registered tools rather than written out separately, so
 * a tool cannot be added without the model learning about it.
 */
@Component
public class ToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, CoachTool> byName = new LinkedHashMap<>();
    private final CoachThreadService threadService;
    private final ObjectMapper objectMapper;

    public ToolRegistry(List<CoachTool> tools, CoachThreadService threadService, ObjectMapper objectMapper) {
        for (CoachTool tool : tools) {
            byName.put(tool.name(), tool);
        }
        this.threadService = threadService;
        this.objectMapper = objectMapper;
    }

    public boolean has(String name) {
        return name != null && byName.containsKey(name);
    }

    public List<String> names() {
        return List.copyOf(byName.keySet());
    }

    /** The tool catalogue as the model sees it. */
    public String promptBlock() {
        StringBuilder block = new StringBuilder();
        for (CoachTool tool : byName.values()) {
            if (tool.toolClass() == ToolClass.READ) {
                block.append(tool.promptSignature()).append("\n\n");
            }
        }
        return block.toString().strip();
    }

    /**
     * Invokes a tool and records it against the turn.
     *
     * <p>Nothing here throws. A missing tool, bad arguments or a failing catalogue all come back as
     * an observation the model can work around, because a turn that dies because a model guessed a
     * tool name is a worse outcome than a turn that is told the guess was wrong. The failure is
     * still recorded, so the trace and the evaluation harness both see it.
     */
    public ToolResult invoke(CoachCallContext context, String toolName, JsonNode args) {
        CoachTool tool = byName.get(toolName);
        long startedAt = System.nanoTime();

        if (tool == null) {
            ToolResult unknown = ToolResult.of("unknown tool",
                    "There is no tool called \"" + toolName + "\". Available: " + String.join(", ", names()));
            record(context, toolName, args, unknown, startedAt, false, "unknown tool");
            return unknown;
        }

        try {
            ToolResult result = tool.invoke(context, args == null ? objectMapper.createObjectNode() : args);
            record(context, toolName, args, result, startedAt, true, null);
            return result;
        } catch (IllegalArgumentException ex) {
            ToolResult bad = ToolResult.of("bad arguments", "That call was rejected: " + ex.getMessage());
            record(context, toolName, args, bad, startedAt, false, ex.getMessage());
            return bad;
        } catch (RuntimeException ex) {
            // The tool name came from the model, so it is untrusted input on a log line.
            LOG.warn("Tool {} failed", safeForLog(toolName), ex);
            ToolResult failed = ToolResult.of("tool failed",
                    "The " + toolName + " tool failed. Answer without it, or say so.");
            record(context, toolName, args, failed, startedAt, false, ex.getClass().getSimpleName());
            return failed;
        }
    }

    private void record(CoachCallContext context,
                        String toolName,
                        JsonNode args,
                        ToolResult result,
                        long startedAt,
                        boolean ok,
                        String error) {
        if (context.turnId() == null) {
            return;
        }
        int latencyMs = (int) ((System.nanoTime() - startedAt) / 1_000_000L);
        threadService.recordToolInvocation(
                context.turnId(), toolName, args == null ? "{}" : args.toString(),
                result.summary(), latencyMs, ok, error);
    }

    /** Strips anything that could end a log line, for the same reason the shared error handler does. */
    private static String safeForLog(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_");
    }

    Optional<CoachTool> lookup(String name) {
        return Optional.ofNullable(byName.get(name));
    }
}
