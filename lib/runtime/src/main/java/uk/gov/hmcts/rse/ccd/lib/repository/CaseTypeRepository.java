package uk.gov.hmcts.rse.ccd.lib.repository;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.asList;

@Component
public class CaseTypeRepository {

    @Autowired
    private final Map<String, String> paths = new HashMap<>();

    private final List<String> files = List.of(
        "AuthorisationCaseEvent",
        "AuthorisationCaseField",
        "AuthorisationCaseState",
        "AuthorisationCaseType",
        "CaseEvent",
        "CaseEventToComplexTypes",
        "CaseEventToFields",
        "CaseField",
        "CaseRoles",
        "CaseType",
        "CaseTypeTab",
        "ComplexTypes",
        "FixedLists",
        "Jurisdiction",
        "SearchCasesResultFields",
        "SearchInputFields",
        "SearchResultFields",
        "State",
        "WorkBasketInputFields",
        "WorkBasketResultFields"
    );

    final ObjectMapper mapper = new ObjectMapper();

    public Optional<CaseType> findByCaseTypeId(String id) {
        return Optional
                .ofNullable(paths.get(id))
                .map(this::toJson)
                .map(this::mapToCaseType);
    }

    @SneakyThrows
    private Map<String, List<Map<String, String>>> toJson(String path) {
        List<Map<String, String>> map = asList(mapper.readValue(Paths.get(path + "/CaseType.json").toFile(), Map[].class));

        return Map.of("CaseType", map);
    }

    private CaseType mapToCaseType(Map<String, List<Map<String, String>>> json) {
        var caseType = new CaseType();

        return caseType;
    }
}
