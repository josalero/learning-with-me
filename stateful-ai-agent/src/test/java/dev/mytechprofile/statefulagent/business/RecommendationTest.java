package dev.mytechprofile.statefulagent.business;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecommendationTest {

    @Test
    void onlyYesRecommendationsPassAndScoresAreFixed() {
        assertThat(Recommendation.STRONG_YES.passing()).isTrue();
        assertThat(Recommendation.YES.passing()).isTrue();
        assertThat(Recommendation.NO.passing()).isFalse();
        assertThat(Recommendation.STRONG_NO.passing()).isFalse();
        assertThat(Recommendation.STRONG_YES.score()).isEqualTo(4);
        assertThat(Recommendation.YES.score()).isEqualTo(3);
        assertThat(Recommendation.NO.score()).isEqualTo(2);
        assertThat(Recommendation.STRONG_NO.score()).isEqualTo(1);
    }
}
