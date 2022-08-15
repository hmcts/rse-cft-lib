package uk.gov.hmcts.rse.ccd.lib.repository;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseType;

import java.util.Map;
import java.util.Optional;

@Component
@ConfigurationProperties(prefix = "ccd.definition")
public class CaseTypeRepository {

  private Map<String, String> paths;

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
