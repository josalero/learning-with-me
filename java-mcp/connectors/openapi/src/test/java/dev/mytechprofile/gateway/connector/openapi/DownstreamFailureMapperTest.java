package dev.mytechprofile.gateway.connector.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.mytechprofile.gateway.connector.ExecutionResult;

class DownstreamFailureMapperTest {

    @Test
    void fromHttp_maps500ToPermanent() {
        ExecutionResult result = DownstreamFailureMapper.fromHttp("searchCandidates", 500, "Internal Server Error");
        assertThat(result).isInstanceOf(ExecutionResult.Failure.class);
        ExecutionResult.Failure failure = (ExecutionResult.Failure) result;
        assertThat(failure.kind()).isEqualTo(ExecutionResult.FailureKind.PERMANENT);
        assertThat(failure.message()).contains("permanent");
    }

    @Test
    void fromHttp_maps503ToTemporary() {
        ExecutionResult result = DownstreamFailureMapper.fromHttp("searchCandidates", 503, "Unavailable");
        ExecutionResult.Failure failure = (ExecutionResult.Failure) result;
        assertThat(failure.kind()).isEqualTo(ExecutionResult.FailureKind.TEMPORARY);
    }

    @Test
    void fromHttp_maps401ToUnauthorized() {
        ExecutionResult result = DownstreamFailureMapper.fromHttp("securePing", 401, "Unauthorized");
        ExecutionResult.Failure failure = (ExecutionResult.Failure) result;
        assertThat(failure.kind()).isEqualTo(ExecutionResult.FailureKind.UNAUTHORIZED);
    }

    @Test
    void fromTransport_mapsTimeoutMessage() {
        ExecutionResult result =
                DownstreamFailureMapper.fromTransport("searchCandidates", "Read timed out");
        ExecutionResult.Failure failure = (ExecutionResult.Failure) result;
        assertThat(failure.kind()).isEqualTo(ExecutionResult.FailureKind.TIMEOUT);
    }
}
