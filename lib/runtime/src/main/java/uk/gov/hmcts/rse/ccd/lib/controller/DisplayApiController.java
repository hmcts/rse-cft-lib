package uk.gov.hmcts.rse.ccd.lib.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.ccd.definition.store.domain.service.JurisdictionUiConfigService;
import uk.gov.hmcts.ccd.definition.store.domain.service.banner.BannerService;
import uk.gov.hmcts.ccd.definition.store.domain.service.display.DisplayService;
import uk.gov.hmcts.ccd.definition.store.domain.service.question.ChallengeQuestionTabService;
import uk.gov.hmcts.ccd.definition.store.repository.model.Banner;
import uk.gov.hmcts.ccd.definition.store.repository.model.BannersResult;
import uk.gov.hmcts.ccd.definition.store.repository.model.CaseTabCollection;
import uk.gov.hmcts.ccd.definition.store.repository.model.ChallengeQuestion;
import uk.gov.hmcts.ccd.definition.store.repository.model.ChallengeQuestionsResult;
import uk.gov.hmcts.ccd.definition.store.repository.model.JurisdictionUiConfig;
import uk.gov.hmcts.ccd.definition.store.repository.model.JurisdictionUiConfigResult;
import uk.gov.hmcts.ccd.definition.store.repository.model.SearchCasesResult;
import uk.gov.hmcts.ccd.definition.store.repository.model.SearchInputDefinition;
import uk.gov.hmcts.ccd.definition.store.repository.model.SearchResultDefinition;
import uk.gov.hmcts.ccd.definition.store.repository.model.WizardPageCollection;
import uk.gov.hmcts.ccd.definition.store.repository.model.WorkBasketResult;
import uk.gov.hmcts.ccd.definition.store.repository.model.WorkbasketInputDefinition;
import uk.gov.hmcts.rse.ccd.lib.repository.CaseTypeRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequestMapping(value = "/api")
@RestController
public class DisplayApiController {


    @Autowired
    CaseTypeRepository repository;

    @GetMapping(value = "/display/search-input-definition/{id}", produces = {"application/json"})
    public SearchInputDefinition displaySearchInputDefinitionIdGet(
        @PathVariable("id") String id) {
        return repository.getSearchInputs(id);
    }

    @GetMapping(value = "/display/search-result-definition/{id}", produces = {"application/json"})
    public SearchResultDefinition displaySearchResultDefinitionIdGet(
        @PathVariable("id") String id) {
        return repository.getSearchResults(id);
    }

    @GetMapping(value = "/display/tab-structure/{id}", produces = {"application/json"})
    public CaseTabCollection displayTabStructureIdGet(
        @PathVariable("id") String id) {
        return repository.getTabs(id);
    }
//
//    @GetMapping(path = "/display/wizard-page-structure/case-types/{ctid}/event-triggers/{etid}")
//    public WizardPageCollection displayWizardPageStructureIdGet(
//        @PathVariable("ctid") String caseTypeId,
//        @PathVariable("etid") String eventReference) {
//        return this.displayService.findWizardPageForCaseType(caseTypeId, eventReference);
//    }
//
    @GetMapping(value = "/display/work-basket-input-definition/{id}", produces = {"application/json"})
    public WorkbasketInputDefinition displayWorkBasketInputDefinitionIdGet(
        @PathVariable("id") String id) {
        return this.repository.getWorkbasketInputs(id);
    }

    @GetMapping(value = "/display/work-basket-definition/{id}", produces = {"application/json"})
    public WorkBasketResult displayWorkBasketDefinitionIdGet(
        @PathVariable("id") String id) {
        return repository.getWorkbasketResult(id);
    }

    @GetMapping(path = "/display/search-cases-result-fields/{id}")
    public SearchCasesResult displaySearchCasesResultIdGet(
        @PathVariable("id") String id) {
            return repository.findSearchCasesResultDefinitionForCaseType(id);
    }
//
//    @GetMapping(value = "/display/banners", produces = {"application/json"})
//    public BannersResult getBanners(
//        @RequestParam("ids") Optional<List<String>> referencesOptional) {
//        List<Banner> banners = referencesOptional.map(
//            references -> bannerService.getAll(references)).orElse(Collections.emptyList());
//        return new BannersResult(banners);
//    }
//
//    @GetMapping(value = "/display/jurisdiction-ui-configs", produces = {"application/json"})
//    public JurisdictionUiConfigResult getJurisdictionUiConfigs(
//        @RequestParam("ids") Optional<List<String>> referencesOptional) {
//        List<JurisdictionUiConfig> configs = referencesOptional
//            .map(references -> jurisdictionUiConfigService.getAll(references)).orElse(Collections.emptyList());
//        return new JurisdictionUiConfigResult(configs);
//    }
//
//    @GetMapping(path = "/display/challenge-questions/case-type/{ctid}/question-groups/{id}")
//    public ChallengeQuestionsResult getChallengeQuestions(
//        @PathVariable("ctid") String caseTypeId,
//        @PathVariable("id") String challengeQuestionId) {
//        List<ChallengeQuestion> questions = challengeQuestionTabService
//            .getChallengeQuestions(caseTypeId, challengeQuestionId);
//        return new ChallengeQuestionsResult(questions);
//    }
}
