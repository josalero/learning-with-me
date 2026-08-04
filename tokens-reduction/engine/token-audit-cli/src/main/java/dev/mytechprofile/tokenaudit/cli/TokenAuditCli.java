package dev.mytechprofile.tokenaudit.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * CLI entry point for token-audit.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0 — success</li>
 *   <li>2 — usage / validation error</li>
 *   <li>1 — unexpected failure</li>
 * </ul>
 *
 * <p>Sample:
 * <pre>{@code
 * token-audit scan . --framework spring-ai
 * }</pre>
 */
@Command(
		name = "token-audit",
		mixinStandardHelpOptions = true,
		version = "token-audit 0.1.0-SNAPSHOT",
		description = "Token efficiency audit for Spring AI / LangChain4j projects.",
		subcommands = {ScanCommand.class}
)
public final class TokenAuditCli implements Runnable {

	@Override
	public void run() {
		CommandLine.usage(this, System.out);
	}

	/**
	 * Runs the CLI.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		int code = new CommandLine(new TokenAuditCli()).execute(args);
		System.exit(code);
	}
}
