package com.fitsync.recommendation.coach.tool;

/**
 * What a tool is allowed to do.
 *
 * <p>The distinction is load-bearing. {@link #READ} tools are free to call and are the only ones
 * the model may reach for while it is reasoning. {@link #DRAFT} tools produce a proposal that a
 * user must accept before anything enters the domain. {@link #MEMORY} writes, but only to what the
 * coach remembers about the user, never to their training or nutrition data.
 *
 * <p>No tool writes to the domain. That happens at the accept endpoint, behind a deliberate tap.
 */
public enum ToolClass {
    READ,
    DRAFT,
    MEMORY
}
