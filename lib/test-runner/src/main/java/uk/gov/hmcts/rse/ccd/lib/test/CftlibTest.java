package uk.gov.hmcts.rse.ccd.lib.test;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import uk.gov.hmcts.rse.ccd.lib.ControlPlane;

public class CftlibTest {
    @SneakyThrows
    @BeforeAll
    void setup() {
        ControlPlane.waitForBoot();
    }
}
