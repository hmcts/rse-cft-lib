package uk.gov.hmcts.rse.ccd.lib.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class CaseTypeRepository {

    @Autowired
    private final Map<String, String> paths = new HashMap<>();

    public Optional<CaseType> findByCaseTypeId(String id) {
        return Optional
                .ofNullable(paths.get(id))
                .map(this::mapToCaseType);
    }

    private CaseType mapToCaseType(String path) {
        var caseType = new CaseType();

        return caseType;
    }
}
