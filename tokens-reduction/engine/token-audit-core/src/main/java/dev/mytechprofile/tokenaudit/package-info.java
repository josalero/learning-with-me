/**
 * Token audit core API: project scanning, findings, and analyzer SPI.
 *
 * <p>Sample:
 * <pre>{@code
 * TokenAuditResult result = TokenAuditor.builder()
 *     .projectPath(Path.of("."))
 *     .frameworks(Framework.SPRING_AI)
 *     .analyze();
 * }</pre>
 */
package dev.mytechprofile.tokenaudit;
