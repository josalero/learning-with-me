package dev.mytechprofile.tokenaudit.estimate;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import dev.mytechprofile.tokenaudit.spi.TokenEstimator;

/**
 * Offline token counter backed by <a href="https://github.com/knuddelsgmbh/jtokkit">jtokkit</a>,
 * using OpenAI's {@code cl100k_base} BPE (GPT-3.5 / GPT-4 family).
 *
 * <p>Counts are deterministic and require no network. They approximate OpenAI billing
 * for {@code cl100k_base} models; other model families (for example {@code o200k_base}
 * for GPT-4o) will differ slightly.
 *
 * <pre>{@code
 * TokenEstimator estimator = JtokkitTokenEstimator.cl100k();
 * int tokens = estimator.estimate("Hello, world");
 * }</pre>
 */
public final class JtokkitTokenEstimator implements TokenEstimator {

	private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();

	private final Encoding encoding;
	private final String encodingName;

	private JtokkitTokenEstimator(EncodingType type) {
		this.encoding = REGISTRY.getEncoding(type);
		this.encodingName = type.getName();
	}

	/**
	 * Shared {@code cl100k_base} estimator used by the built-in prompt analyzer.
	 *
	 * @return estimator singleton
	 */
	public static JtokkitTokenEstimator cl100k() {
		return Holder.CL100K;
	}

	/**
	 * Shared {@code o200k_base} estimator (GPT-4o family).
	 *
	 * @return estimator singleton
	 */
	public static JtokkitTokenEstimator o200k() {
		return Holder.O200K;
	}

	/**
	 * Returns the jtokkit encoding name this estimator uses (for report messages).
	 *
	 * @return encoding name such as {@code cl100k_base}
	 */
	public String encodingName() {
		return encodingName;
	}

	@Override
	public int estimate(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return encoding.countTokens(text);
	}

	private static final class Holder {
		private static final JtokkitTokenEstimator CL100K =
				new JtokkitTokenEstimator(EncodingType.CL100K_BASE);
		private static final JtokkitTokenEstimator O200K =
				new JtokkitTokenEstimator(EncodingType.O200K_BASE);
	}
}
