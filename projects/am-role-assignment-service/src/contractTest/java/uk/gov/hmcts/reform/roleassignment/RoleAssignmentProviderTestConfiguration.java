package uk.gov.hmcts.reform.roleassignment;

import org.kie.api.KieServices;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ParseRequestService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PersistenceService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.PrepareResponseService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.RetrieveDataService;
import uk.gov.hmcts.reform.roleassignment.domain.service.common.ValidationModelService;
import uk.gov.hmcts.reform.roleassignment.domain.service.createroles.CreateRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.domain.service.deleteroles.DeleteRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.domain.service.getroles.RetrieveRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.domain.service.queryroles.QueryRoleAssignmentOrchestrator;
import uk.gov.hmcts.reform.roleassignment.feignclients.DataStoreApi;
import uk.gov.hmcts.reform.roleassignment.util.CorrelationInterceptorUtil;
import uk.gov.hmcts.reform.roleassignment.util.PersistenceUtil;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

@TestConfiguration
public class RoleAssignmentProviderTestConfiguration {

    @MockBean
    private PersistenceService persistenceService;

    @Bean
    @Primary
    public PrepareResponseService getPrepareResponseService() {
        return new PrepareResponseService();
    }

    @MockBean
    private CorrelationInterceptorUtil correlationInterceptorUtil;

    @Bean
    @Primary
    public ParseRequestService getParseRequestService() {
        return new ParseRequestService();
    }

    private KieServices kieServices = KieServices.Factory.get();

    @Bean
    public KieContainer kieContainer() {
        return kieServices.getKieClasspathContainer();
    }

    @MockBean
    SecurityUtils securityUtils;


    @Bean
    public StatelessKieSession getStatelessKieSession() {
        return kieContainer().newStatelessKieSession("role-assignment-validation-session");
    }

    @MockBean
    private DataStoreApi dataStoreApi;

    @MockBean
    private CacheManager cacheManager;

    @Bean
    @Primary
    public RetrieveDataService getRetrieveDataService() {
        return new RetrieveDataService(dataStoreApi, cacheManager);
    }

    @Bean
    @Primary
    public ValidationModelService getValidationModelService() {
        return new ValidationModelService(getStatelessKieSession(), getRetrieveDataService(), persistenceService);
    }

    @Bean
    @Primary
    public PersistenceUtil getPersistenceUtil() {
        return new PersistenceUtil();
    }

    @Bean
    @Primary
    public RetrieveRoleAssignmentOrchestrator getListOfRoles() {
        return new RetrieveRoleAssignmentOrchestrator(persistenceService, getPrepareResponseService());
    }

    @Bean
    @Primary
    public CreateRoleAssignmentOrchestrator createRoleAssignment() {
        return new CreateRoleAssignmentOrchestrator(getParseRequestService(), getPrepareResponseService(),
                                                    persistenceService, getValidationModelService(),
                                                    getPersistenceUtil()
        );
    }

    @Bean
    @Primary
    public QueryRoleAssignmentOrchestrator retrieveRoleAssignmentsByQueryRequest() {
        return new QueryRoleAssignmentOrchestrator(persistenceService);
    }

    @Bean
    @Primary
    public DeleteRoleAssignmentOrchestrator deleteRoleAssignment() {
        return new DeleteRoleAssignmentOrchestrator(persistenceService, getParseRequestService(),
                                                    getValidationModelService(), getPersistenceUtil()
        );
    }

}
