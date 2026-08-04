/**
 * A realistic Spring AI customer-support assistant that a team might actually ship, containing
 * token-efficiency problems that emerge from ordinary design decisions rather than contrived names.
 *
 * <p>The waste here is idiomatic, not staged:
 * <ul>
 *   <li>{@link com.example.support.SupportAssistantConfig} attaches a large evergreen system prompt
 *       to <em>every</em> request via {@code defaultSystem(...)}.</li>
 *   <li>{@link com.example.support.SupportTools} is a single tool bean exposing many
 *       {@code @Tool} methods — including privileged, destructive ones — all registered on the
 *       general assistant via {@code defaultTools(...)}.</li>
 *   <li>{@link com.example.support.SupportAssistantService} retrieves a large fixed
 *       {@code topK} with no similarity threshold and stuffs every chunk plus the full transcript
 *       into a single user message.</li>
 *   <li>The same service keeps conversation state in an unbounded {@code List<Message>} that is
 *       replayed on every turn.</li>
 *   <li>{@link com.example.support.EscalationCoordinator} hands the entire transcript to a second
 *       agent instead of a task-scoped brief.</li>
 * </ul>
 *
 * <p>Run the token audit against this module to see each issue reported with a stable finding id.
 */
package com.example.support;
