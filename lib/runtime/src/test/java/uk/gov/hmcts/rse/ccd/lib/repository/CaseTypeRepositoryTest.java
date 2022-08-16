package uk.gov.hmcts.rse.ccd.lib.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
class CaseTypeRepositoryTest {

    @Autowired
    private CaseTypeRepository repository;

    @Test
    void findByCaseTypeId() {
        var caseType = repository.findByCaseTypeId("NFD").orElseThrow();

        assertEquals("NFD", caseType.getId());
    }
}