package uk.gov.hmcts.reform.roleassignment.data;

import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.LocalDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class RoleAssignmentEntitySpecificationsTest {

    @Mock
    Specification<RoleAssignmentEntity> mockSpec;
    @Mock
    Root<RoleAssignmentEntity> root;
    @Mock
    CriteriaQuery<RoleAssignmentEntity> query;
    @Mock
    CriteriaBuilder builder;

    @Mock
    Path path;

    @Mock
    Predicate predicate;


    @Before
    public void setUp() {
        when(mockSpec.toPredicate(root, query, builder)).thenReturn(predicate);
        Mockito.doReturn(path).when(root).get(anyString());
        when(path.isNull()).thenReturn(predicate);


    }

    @Test
    public void shouldReturnPredicate_WhileSearchByActorIds() {
        List<String> actorId = List.of(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );

        Specification<RoleAssignmentEntity> spec = RoleAssignmentEntitySpecifications.searchByActorIds(actorId);
        spec = spec.and(mockSpec);
        assertThat(spec).isNotNull();
        MatcherAssert.assertThat(spec.toPredicate(root, query, builder), is(predicate));


    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByActorIdsWithoutMock() {
        List<String> actorId = List.of(
            "123e4567-e89b-42d3-a456-556642445678",
            "4dc7dd3c-3fb5-4611-bbde-5101a97681e1"
        );

        Specification<RoleAssignmentEntity> spec = RoleAssignmentEntitySpecifications.searchByActorIds(actorId);
        assertThat(spec).isNotNull();
        MatcherAssert.assertThat(spec.toPredicate(root, query, builder), is(nullValue()));


    }

    @Test
    public void shouldReturnNullValue_WhileSearchByActorIdsWithoutMock() {
        List<String> actorId = null;

        Specification<RoleAssignmentEntity> spec = RoleAssignmentEntitySpecifications.searchByActorIds(actorId);
        assertThat(spec).isNotNull();


    }

    @Test
    public void shouldReturnNull_WhileSearchByActorIdsWithoutMock() {
        List<String> actorId = Collections.emptyList();

        Specification<RoleAssignmentEntity> spec = RoleAssignmentEntitySpecifications.searchByActorIds(actorId);
        assertThat(spec).isNotNull();



    }


    @Test
    public void shouldNotReturnPredicate_WhileSearchByValidDateWithoutMock() {


        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByValidDate(
            now());
        assertThat(specification).isNotNull();


    }

    @Test
    public void shouldReturnNull_WhileSearchByValidDateWithoutMock() {


        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByValidDate(
            null);
        assertThat(specification).isNull();


    }

    @Test
    public void shouldReturnPredicate_WhileSearchByRoleName() {
        List<String> roleNames = List.of("judge", "senior judge");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleName(
            roleNames);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByRoleNameWithoutMock() {
        List<String> roleNames = List.of("judge", "senior judge");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleName(
            roleNames);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByRoleNameWithoutMock() {
        List<String> roleNames = Collections.emptyList();
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleName(
            roleNames);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnNullValue_WhileSearchByRoleNameWithoutMock() {
        List<String> roleNames = null;
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleName(
            roleNames);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnPredicate_WhileSearchByRoleType() {
        List<String> roleTypes = List.of("CASE", "ORGANISATION");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleType(
            roleTypes);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByRoleTypeWithoutMock() {
        List<String> roleTypes = List.of("CASE", "ORGANISATION");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleType(
            roleTypes);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByRoleTypeWithoutMock() {
        List<String> roleTypes = Collections.emptyList();
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleType(
            roleTypes);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnPredicate_WhileSearchByClassification() {
        List<String> classifications = List.of("PUBLIC", "PRIVATE");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByClassification(
            classifications);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByClassificationWithoutMock() {
        List<String> classifications = List.of("PUBLIC", "PRIVATE");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByClassification(
            classifications);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByClassificationWithoutMock() {
        List<String> classifications = Collections.emptyList();
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByClassification(
            classifications);
        assertThat(specification).isNull();


    }

    @Test
    public void shouldReturnNullValue_WhileSearchByClassificationWithoutMock() {
        List<String> classifications = null;
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByClassification(
            classifications);
        assertThat(specification).isNull();


    }

    @Test
    public void shouldReturnPredicate_WhileSearchByGrantType() {
        List<String> grantTypes = List.of("SPECIFIC", "STANDARD");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByGrantType(
            grantTypes);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByGrantTypeWithoutMock() {
        List<String> grantTypes = List.of("SPECIFIC", "STANDARD");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByGrantType(
            grantTypes);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByGrantTypeWithoutMock() {
        List<String> grantTypes = Collections.emptyList();
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByGrantType(
            grantTypes);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnNullValue_WhileSearchByGrantTypeWithoutMock() {
        List<String> grantTypes = null;
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByGrantType(
            grantTypes);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnPredicate_WhileSearchByRoleCategories() {
        List<String> roleCategories = List.of("JUDICIAL");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleCategories(
            roleCategories);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByRoleCategoriesWithoutMock() {
        List<String> roleCategories = List.of("JUDICIAL");
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleCategories(
            roleCategories);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByRoleCategoriesWithoutMock() {
        List<String> roleCategories = Collections.emptyList();
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleCategories(
            roleCategories);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnNullValue_WhileSearchByRoleCategoriesWithoutMock() {
        List<String> roleCategories = null;
        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByRoleCategories(
            roleCategories);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnPredicate_WhileSearchByAttributes() {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = List.of("London", "JAPAN");
        List<String> contractTypes = List.of("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);

        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAttributes(
            attributes);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByAttributesWithoutMock() {
        Map<String, List<String>> attributes = new HashMap<>();
        List<String> regions = List.of("London", "JAPAN");
        List<String> contractTypes = List.of("SALARIED", "Non SALARIED");
        attributes.put("region", regions);
        attributes.put("contractType", contractTypes);

        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAttributes(
            attributes);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByAttributesWithoutMock() {
        Map<String, List<String>> attributes = new HashMap<>();


        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAttributes(
            attributes);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnNullValue_WhileSearchByAttributesWithoutMock() {
        Map<String, List<String>> attributes = null;


        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAttributes(
            attributes);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnPredicate_WhileSearchByAuthorisations() {
        List<String> authorisations = List.of(
            "dev",
            "tester"
        );

        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAuthorisations(
            authorisations);
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));

    }

    @Test
    public void shouldNotReturnPredicate_WhileSearchByAuthorisationsWithoutMock() {
        List<String> authorisations = List.of(
            "dev",
            "tester"
        );

        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAuthorisations(
            authorisations);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(nullValue()));

    }

    @Test
    public void shouldReturnNull_WhileSearchByAuthorisationsWithoutMock() {
        List<String> authorisations = Collections.emptyList();

        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAuthorisations(
            authorisations);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnNullValue_WhileSearchByAuthorisationsWithoutMock() {
        List<String> authorisations = null;

        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByAuthorisations(
            authorisations);
        assertThat(specification).isNull();

    }

    @Test
    public void shouldReturnPredicate_WhileSearchByValidDateWithMock() {


        Specification<RoleAssignmentEntity> specification = RoleAssignmentEntitySpecifications.searchByValidDate(now());
        specification = specification.and(mockSpec);
        assertThat(specification).isNotNull();
        MatcherAssert.assertThat(specification.toPredicate(root, query, builder), is(predicate));


    }

}
