package ping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PingServiceTest {

    @Test
    void statusIsOk() {
        assertEquals("ok", new PingService().status());
    }
}
