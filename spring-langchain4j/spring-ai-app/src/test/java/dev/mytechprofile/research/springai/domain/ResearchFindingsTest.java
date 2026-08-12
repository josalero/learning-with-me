package dev.mytechprofile.research.springai.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ResearchFindingsTest {

    @Test
    void holdsFindingsForStructuredResearcherOutput() {
        ResearchFindings findingsDoc = new ResearchFindings(List.of(
                new Finding("What is Spring AI?", "A Spring project for AI apps")));

        assertThat(findingsDoc.findings()).hasSize(1);
        assertThat(findingsDoc.findings().getFirst().question()).isEqualTo("What is Spring AI?");
        assertThat(findingsDoc.findings().getFirst().answer()).contains("Spring project");
    }
}
