package uk.gov.hmcts.reform.roleassignment.auditlog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

class AuditRepositoryTest {

    private AuditRepository repository;

    @Mock
    private AuditLogFormatter logFormatter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new LoggerAuditRepository(logFormatter);
    }

    @Test
    void shouldSaveAuditEntry() {
        AuditEntry auditEntry = new AuditEntry();

        repository.save(auditEntry);

        verify(logFormatter).format(auditEntry);
    }
}
