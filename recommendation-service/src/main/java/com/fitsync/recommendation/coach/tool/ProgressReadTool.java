package com.fitsync.recommendation.coach.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;

/**
 * The steps every progress tool takes: read the period from the arguments, read one endpoint with
 * the caller's token and zone, and write what came back as plain lines.
 *
 * <p>An endpoint that cannot be read, or that answers with something that is not JSON, gives the
 * model an observation saying so, never a failed turn. A field the endpoint does not send is left out
 * of the lines rather than written as zero, because the services behind these tools are built
 * separately and a figure that is absent is not a figure that is nothing.
 */
abstract class ProgressReadTool implements CoachTool {

    private final ObjectReader reader;
    private final UserCalendar calendar;

    protected ProgressReadTool(ObjectMapper objectMapper, UserCalendar calendar) {
        this.reader = objectMapper.reader();
        this.calendar = calendar;
    }

    @Override
    public final ToolClass toolClass() {
        return ToolClass.READ;
    }

    /** What the tool reads, as the observation names it, such as "Training progress". */
    protected abstract String subject();

    /**
     * Reads the endpoint.
     *
     * @return the body, or null when it could not be read
     * @throws IllegalArgumentException when an argument other than the period is wrong
     */
    protected abstract String fetch(CoachCallContext context, JsonNode args, ProgressToolSupport.Period period);

    /** Writes the body as lines; returns an empty string when there is nothing in it to state. */
    protected abstract String render(JsonNode body, ProgressToolSupport.Period period);

    @Override
    public final ToolResult invoke(CoachCallContext context, JsonNode args) {
        // The endpoints slice days in the zone they are sent and fall back to UTC without one. A turn
        // whose client sent no zone still speaks in the user's stored zone, so that zone is sent on.
        CoachCallContext zoned = context.withTimezone(UserCalendar.zoneName(calendar.zone(context)));
        ProgressToolSupport.Period period = ProgressToolSupport.period(args, zoned, calendar);
        String body = fetch(zoned, args, period);
        if (body == null) {
            return unavailable(period);
        }
        JsonNode root;
        try {
            root = reader.readTree(body);
        } catch (JsonProcessingException ex) {
            return unavailable(period);
        }
        if (root == null || !root.isObject()) {
            return unavailable(period);
        }
        String lines = render(root, period);
        if (lines.isBlank()) {
            return ToolResult.of("nothing recorded",
                    subject() + " for " + period.describe() + " holds nothing recorded yet.");
        }
        return ToolResult.of(subject().toLowerCase(java.util.Locale.ROOT) + " " + period.describe(), lines);
    }

    private ToolResult unavailable(ProgressToolSupport.Period period) {
        return ToolResult.of("unavailable",
                subject() + " for " + period.describe() + " could not be read. Answer without it, or say so.");
    }
}
