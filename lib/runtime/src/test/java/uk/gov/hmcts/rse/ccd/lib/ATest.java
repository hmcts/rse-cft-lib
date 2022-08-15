package uk.gov.hmcts.rse.ccd.lib;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.rse.ccd.lib.controller.CaseDefinitionController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ATest {
    @Autowired
    CaseDefinitionController controller;

    @Test
    public void foo() {
        assertThat(controller.dataCaseTypeIdGet("foo"))
                .isNotNull();
    }
}
