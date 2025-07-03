package uk.gov.hmcts.divorce.sow014.nfd;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static java.lang.System.getenv;
import static java.util.Collections.singletonList;
import static org.jooq.nfdiv.civil.Tables.SOLICITORS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static uk.gov.hmcts.divorce.divorcecase.model.PaymentStatus.SUCCESS;
import static uk.gov.hmcts.divorce.divorcecase.model.State.Draft;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.APPLICANT_1_SOLICITOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.CASE_WORKER;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.CITIZEN;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.JUDGE;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.LEGAL_ADVISOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.SOLICITOR;
import static uk.gov.hmcts.divorce.divorcecase.model.UserRole.SUPER_USER;
import static uk.gov.hmcts.divorce.divorcecase.model.access.Permissions.CREATE_READ_UPDATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jooq.DSLContext;
import org.jooq.nfdiv.civil.tables.Payment;
import org.jooq.nfdiv.civil.tables.records.PaymentRecord;
import org.jooq.nfdiv.civil.tables.records.SolicitorsRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.ConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.YesOrNo;
import uk.gov.hmcts.divorce.common.ccd.PageBuilder;
import uk.gov.hmcts.divorce.divorcecase.model.Applicant;
import uk.gov.hmcts.divorce.divorcecase.model.Application;
import uk.gov.hmcts.divorce.divorcecase.model.ApplicationType;
import uk.gov.hmcts.divorce.divorcecase.model.CaseData;
import uk.gov.hmcts.divorce.divorcecase.model.CaseInvite;
import uk.gov.hmcts.divorce.divorcecase.model.State;
import uk.gov.hmcts.divorce.divorcecase.model.UserRole;
import uk.gov.hmcts.divorce.idam.IdamService;
import uk.gov.hmcts.divorce.idam.User;
import uk.gov.hmcts.divorce.solicitor.service.CcdAccessService;
import uk.gov.hmcts.reform.ccd.client.model.SubmittedCallbackResponse;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

@Slf4j
@Component
public class CreateTestCase implements CCDConfig<CaseData, State, UserRole> {
    private static final String ENVIRONMENT_AAT = "aat";
    private static final String TEST_CREATE = "create-test-application";
    private static final String SOLE_APPLICATION = "classpath:data/sole.json";
    private static final String JOINT_APPLICATION = "classpath:data/joint.json";
    public static volatile boolean submittedCallbackTriggered = false;

    @Getter
    enum SolicitorRoles {

        CREATOR("ecb8fff1-e033-3846-b15e-c01ff10cb4bb", UserRole.CREATOR.getRole()),
        APPLICANT2("6e508b49-1fa8-3d3c-8b53-ec466637315b", UserRole.APPLICANT_2.getRole()),
        SOLICITORA("b980e249-d65c-3f9e-b3a9-409077b8e3bb", UserRole.SOLICITOR_A.getRole()),
        SOLICITORB("38079360-70af-39c6-87eb-007c7a17ad42", UserRole.SOLICITOR_B.getRole()),
        SOLICITORC("d6fb5531-677a-3b89-8d6d-53a687d38bfd", UserRole.SOLICITOR_C.getRole()),
        SOLICITORD("55495ad4-cfab-33d2-bdcc-e5f951071545", UserRole.SOLICITOR_D.getRole()),
        SOLICITORE("d4cf0594-f628-3309-85bf-69fe22cf6199", UserRole.SOLICITOR_E.getRole()),
        SOLICITORF("c7593885-1206-3780-b656-a1d2f0b3817a", UserRole.SOLICITOR_F.getRole()),
        SOLICITORG("33153390-cdb9-3c66-8562-c2242a67800d", UserRole.SOLICITOR_G.getRole()),
        SOLICITORH("6c23b66f-5282-3ed8-a2c4-58ae418581e8", UserRole.SOLICITOR_H.getRole()),
        SOLICITORI("cb3c3109-5d92-374e-b551-3cb72d6dad9d", UserRole.SOLICITOR_I.getRole()),
        SOLICITORJ("38a2499c-0c65-3fb0-9342-e47091c766f6", UserRole.SOLICITOR_J.getRole()),
        APPLICANT_2_SOLICITOR("b81df946-87c4-3eb8-95e0-2da70727aec8", UserRole.APPLICANT_2_SOLICITOR.getRole()),
        APPLICANT_1_SOLICITOR("74779774-2fc4-32c9-a842-f8d0aa6e770a",UserRole.APPLICANT_1_SOLICITOR.getRole()),
        CITIZEN("20fa35c5-167f-3d6f-b8ab-5c487d16f29d", UserRole.CITIZEN.getRole());

        private final String id;
        private final String role;

        SolicitorRoles(String id, String role) {
            this.id = id;
            this.role = role;
        }
    }

    @Autowired
    private CcdAccessService ccdAccessService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdamService idamService;

    @Autowired
    private DSLContext db;

    @Override
    public void configure(ConfigBuilder<CaseData, State, UserRole> configBuilder) {
        var roles = new ArrayList<UserRole>();
        var env = getenv().getOrDefault("S2S_URL_BASE", "aat");

        if (env.contains(ENVIRONMENT_AAT)) {
            roles.add(SOLICITOR);
            roles.add(CASE_WORKER);
        }

        new PageBuilder(configBuilder
            .event(TEST_CREATE)
            .initialState(Draft)
            .name("Create test case")
            .aboutToStartCallback(this::start)
            .aboutToSubmitCallback(this::aboutToSubmit)
            .submittedCallback(this::submitted)
            .grant(CREATE_READ_UPDATE, roles.toArray(UserRole[]::new))
            .grantHistoryOnly(SUPER_USER, CASE_WORKER, LEGAL_ADVISOR, SOLICITOR, CITIZEN, JUDGE))
            .page("Create test case", this::midEvent)
            .mandatory(CaseData::getApplicationType)
            .complex(CaseData::getApplicant1)
            .mandatoryWithLabel(Applicant::getSolicitorRepresented, "Is applicant 1 represented")
            .done()
            .complex(CaseData::getApplicant2)
            .mandatoryWithLabel(Applicant::getSolicitorRepresented, "Is applicant 2 represented")
            .done()
            .complex(CaseData::getCaseInvite)
            .label("userIdLabel", "<pre>Use ./bin/get-user-id-by-email.sh [email] to get an ID"
                + ".\n\nTEST_SOLICITOR@mailinator.com is 93b108b7-4b26-41bf-ae8f-6e356efb11b3 in AAT.\n</pre>")
            .mandatoryWithLabel(CaseInvite::applicant2UserId, "Applicant 2 user ID")
            .done()
            .complex(CaseData::getApplication)
            .mandatoryWithLabel(Application::getStateToTransitionApplicationTo, "Case state")
            .done();
    }

    private AboutToStartOrSubmitResponse<CaseData, State> start(CaseDetails<CaseData, State> caseDetails) {
        var data = caseDetails.getData();
        data.setApplicationType(ApplicationType.SOLE_APPLICATION);
        data.getApplicant1().setSolicitorRepresented(YesOrNo.NO);
        data.getApplicant2().setSolicitorRepresented(YesOrNo.NO);
        data.setCaseInvite(
            CaseInvite.builder()
                .applicant2InviteEmailAddress("TEST_SOLICITOR@mailinator.com")
                .applicant2UserId("6e508b49-1fa8-3d3c-8b53-ec466637315b")
                .build()
        );
        data.getApplication().setStateToTransitionApplicationTo(State.AwaitingApplicant1Response);
        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(caseDetails.getData())
            .build();
    }

    public AboutToStartOrSubmitResponse<CaseData, State> midEvent(
        CaseDetails<CaseData, State> details,
        CaseDetails<CaseData, State> detailsBefore
    ) {

        final CaseData data = details.getData();
        try {
            UUID uuid = UUID.fromString(data.getCaseInvite().applicant2UserId());
            log.info("{}", uuid);
            return AboutToStartOrSubmitResponse.<CaseData, State>builder()
                .data(data)
                .build();
        } catch (IllegalArgumentException e) {
            return AboutToStartOrSubmitResponse.<CaseData, State>builder()
                .errors(singletonList("User ID entered for applicant 2 is an invalid UUID"))
                .build();
        }
    }

    @SneakyThrows
    public AboutToStartOrSubmitResponse<CaseData, State> aboutToSubmit(CaseDetails<CaseData, State> details,
                                                                       CaseDetails<CaseData, State> beforeDetails) {

        var file = details.getData().getApplicationType().isSole() ? SOLE_APPLICATION : JOINT_APPLICATION;
        var resourceLoader = new DefaultResourceLoader();
        var json = IOUtils.toString(resourceLoader.getResource(file).getInputStream(), Charset.defaultCharset());
        var fixture = objectMapper.readValue(json, CaseData.class);

        fixture.getApplicant1().setSolicitorRepresented(details.getData().getApplicant1().getSolicitorRepresented());
        fixture.getApplicant2().setSolicitorRepresented(details.getData().getApplicant2().getSolicitorRepresented());
        fixture.setCaseInvite(details.getData().getCaseInvite());
        fixture.setHyphenatedCaseRef(fixture.formatCaseRef(details.getId()));

        return AboutToStartOrSubmitResponse.<CaseData, State>builder()
            .data(fixture)
            .state(details.getData().getApplication().getStateToTransitionApplicationTo())
            .build();
    }

    private void createPayment(CaseDetails<CaseData, State> details) {
        PaymentRecord paymentRecord = db.newRecord(Payment.PAYMENT);
        paymentRecord.setAmount(new BigDecimal(550));
        paymentRecord.setChannel("online");
        paymentRecord.setFeeCode("FEE0001");
        paymentRecord.setReference("paymentRef");
        paymentRecord.setStatus(SUCCESS.getLabel());
        String paymentId = UUID.randomUUID().toString();
        paymentRecord.setId(paymentId);
        paymentRecord.setCaseReference(details.getId());
        paymentRecord.setTransactionId("ge7po9h5bhbtbd466424src9tk");
        paymentRecord.setCreated(LocalDateTime.now());
        paymentRecord.store();
    }


    public SubmittedCallbackResponse submitted(CaseDetails<CaseData, State> details, CaseDetails<CaseData, State> before) {
        submittedCallbackTriggered = true;
        var data = details.getData();
        var caseId = details.getId();
        var app2Id = data.getCaseInvite().applicant2UserId();
        var auth = httpServletRequest.getHeader(AUTHORIZATION);

       createPayment(details);

        if (data.getApplicant1().isRepresented()) {
            var orgId = details
                .getData()
                .getApplicant1()
                .getSolicitor()
                .getOrganisationPolicy()
                .getOrganisation()
                .getOrganisationId();

            ccdAccessService.addApplicant1SolicitorRole(auth, caseId, orgId);
        }

        if (data.getCaseInvite().applicant2UserId() != null && data.getApplicant2().isRepresented()) {
            var orgId = details
                .getData()
                .getApplicant2()
                .getSolicitor()
                .getOrganisationPolicy()
                .getOrganisation()
                .getOrganisationId();

            ccdAccessService.addRoleToCase(app2Id, caseId, orgId, APPLICANT_1_SOLICITOR);
        } else if (data.getCaseInvite().applicant2UserId() != null) {
            ccdAccessService.linkRespondentToApplication(auth, caseId, app2Id, details, UserRole.APPLICANT_2.getRole());
        }

        Arrays.stream(SolicitorRoles.values()).toList().forEach(org -> {

            if (org != SolicitorRoles.CREATOR
                || org != SolicitorRoles.APPLICANT2
                || org != SolicitorRoles.APPLICANT_1_SOLICITOR
                || org != SolicitorRoles.APPLICANT_2_SOLICITOR
            ) {
                ccdAccessService.linkRespondentToApplication(auth, caseId, org.getId(), details, org.getRole());
            }
            log.info("Adding user role to org {}", org.role);
            User user = idamService.retrieveUser(auth);
            UserInfo userDetails = user.getUserDetails();

            createSolicitor(details, org.getRole(), userDetails);
        });

        return SubmittedCallbackResponse.builder().build();
    }

    private void createSolicitor(CaseDetails<CaseData, State> details,
                                 String role, UserInfo userDetails) {
        SolicitorsRecord solicitorsRecord = db.newRecord(SOLICITORS);
        solicitorsRecord.setReference(details.getId());
        solicitorsRecord.setRole(role);
        solicitorsRecord.setForename(userDetails.getGivenName());
        solicitorsRecord.setSurname(userDetails.getFamilyName());
        solicitorsRecord.store();
    }
}
