/**
 * Domain model for compiled tools, stable errors, and MCP-facing results.
 *
 * <p>These types are transport-neutral. The MCP adapter maps them to Spring AI
 * {@code ToolCallback} instances without leaking connector details.
 */
package dev.mytechprofile.gateway.model;
