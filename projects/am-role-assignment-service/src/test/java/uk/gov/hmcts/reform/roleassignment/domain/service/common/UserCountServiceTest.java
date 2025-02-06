package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentRepository;

import java.math.BigInteger;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;

@RunWith(MockitoJUnitRunner.class)
class UserCountServiceTest {

    @Mock
    private TelemetryClient telemetryClientMock;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepositoryMock;

    @InjectMocks
    private UserCountService sut = new UserCountService();

    RoleAssignmentRepository.JurisdictionRoleCategoryAndCount userCountCategoryIa;
    RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount userCountCategoryNameIa;
    RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount userCountCategoryNameCivil;
    RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount userCountCategoryNameCivil2;
    RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount userCountCategoryNameNull;
    RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount userCountCategoryNameNull2;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        userCountCategoryIa =
            new RoleAssignmentRepository.JurisdictionRoleCategoryAndCount() {

                @Override
                public String getJurisdiction() {
                    return "IA";
                }

                @Override
                public String getRoleCategory() {
                    return "some role category";
                }

                @Override
                public BigInteger getCount() {
                    return BigInteger.TEN;
                }
            };

        userCountCategoryNameIa =
            new RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount() {

                @Override
                public String getJurisdiction() {
                    return "IA";
                }

                @Override
                public String getRoleCategory() {
                    return "some role category";
                }

                @Override
                public String getRoleName() {
                    return "some role name";
                }

                @Override
                public BigInteger getCount() {
                    return BigInteger.TWO;
                }
            };

        userCountCategoryNameCivil =
            new RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount() {

                @Override
                public String getJurisdiction() {
                    return "CIVIL";
                }

                @Override
                public String getRoleCategory() {
                    return "some role category";
                }

                @Override
                public String getRoleName() {
                    return "some role name";
                }

                @Override
                public BigInteger getCount() {
                    return BigInteger.ONE;
                }
            };
        userCountCategoryNameCivil2 =
            new RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount() {

                @Override
                public String getJurisdiction() {
                    return "CIVIL";
                }

                @Override
                public String getRoleCategory() {
                    return "some role category";
                }

                @Override
                public String getRoleName() {
                    return "some role name";
                }

                @Override
                public BigInteger getCount() {
                    return BigInteger.valueOf(4);
                }
            };

        userCountCategoryNameNull =
            new RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount() {

                @Override
                public String getJurisdiction() {
                    return null;
                }

                @Override
                public String getRoleCategory() {
                    return "some role category";
                }

                @Override
                public String getRoleName() {
                    return "some role name";
                }

                @Override
                public BigInteger getCount() {
                    return BigInteger.valueOf(3);
                }
            };

        userCountCategoryNameNull2 =
            new RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount() {

                @Override
                public String getJurisdiction() {
                    return null;
                }

                @Override
                public String getRoleCategory() {
                    return "some role category2";
                }

                @Override
                public String getRoleName() {
                    return "some role name";
                }

                @Override
                public BigInteger getCount() {
                    return BigInteger.valueOf(5);
                }
            };
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetUserCountResponse() throws SQLException, JsonProcessingException {

        doReturn(List.of(userCountCategoryIa)).when(roleAssignmentRepositoryMock).getOrgUserCountByJurisdiction();
        doReturn(List.of(userCountCategoryNameIa)).when(roleAssignmentRepositoryMock)
            .getOrgUserCountByJurisdictionAndRoleName();

        Map<String, Object> response = sut.getOrgUserCount();
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount> responseOrgUserCountByJurisdiction =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount>)
                response.get("OrgUserCountByJurisdiction");
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>
            responseOrgUserCountByJurisdictionAndRoleName =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>)
                response.get("OrgUserCountByJurisdictionAndRoleName");

        assertNotNull(response);
        assertEquals(BigInteger.TEN, responseOrgUserCountByJurisdiction.get(0).getCount());
        assertEquals("some role category", responseOrgUserCountByJurisdiction.get(0).getRoleCategory());
        assertEquals(BigInteger.TWO, responseOrgUserCountByJurisdictionAndRoleName.get(0).getCount());
        assertEquals("IA", responseOrgUserCountByJurisdictionAndRoleName.get(0).getJurisdiction());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetUserCountResponseJurisdictionCivil() throws SQLException, JsonProcessingException {

        doReturn(List.of(userCountCategoryIa)).when(roleAssignmentRepositoryMock).getOrgUserCountByJurisdiction();
        doReturn(List.of(userCountCategoryNameCivil)).when(roleAssignmentRepositoryMock)
            .getOrgUserCountByJurisdictionAndRoleName();

        Map<String, Object> response = sut.getOrgUserCount();
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount> responseOrgUserCountByJurisdiction =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount>)
                response.get("OrgUserCountByJurisdiction");
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>
            responseOrgUserCountByJurisdictionAndRoleName =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>)
                response.get("OrgUserCountByJurisdictionAndRoleName");

        assertNotNull(response);
        assertEquals(BigInteger.TEN, responseOrgUserCountByJurisdiction.get(0).getCount());
        assertEquals("some role category", responseOrgUserCountByJurisdiction.get(0).getRoleCategory());
        assertEquals(BigInteger.ONE, responseOrgUserCountByJurisdictionAndRoleName.get(0).getCount());
        assertEquals("CIVIL", responseOrgUserCountByJurisdictionAndRoleName.get(0).getJurisdiction());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetUserCountResponseJurisdictionNull() throws SQLException, JsonProcessingException {

        doReturn(List.of(userCountCategoryIa)).when(roleAssignmentRepositoryMock).getOrgUserCountByJurisdiction();
        doReturn(List.of(userCountCategoryNameNull)).when(roleAssignmentRepositoryMock)
            .getOrgUserCountByJurisdictionAndRoleName();

        Map<String, Object> response = sut.getOrgUserCount();
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount> responseOrgUserCountByJurisdiction =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount>)
                response.get("OrgUserCountByJurisdiction");
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>
            responseOrgUserCountByJurisdictionAndRoleName =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>)
                response.get("OrgUserCountByJurisdictionAndRoleName");

        assertNotNull(response);
        assertEquals(BigInteger.TEN, responseOrgUserCountByJurisdiction.get(0).getCount());
        assertEquals("some role category", responseOrgUserCountByJurisdiction.get(0).getRoleCategory());
        assertEquals(BigInteger.valueOf(3), responseOrgUserCountByJurisdictionAndRoleName.get(0).getCount());
        assertEquals(null, responseOrgUserCountByJurisdictionAndRoleName.get(0).getJurisdiction());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetUserCountResponseList() throws SQLException, JsonProcessingException {

        doReturn(List.of(userCountCategoryIa)).when(roleAssignmentRepositoryMock).getOrgUserCountByJurisdiction();
        doReturn(List.of(userCountCategoryNameIa,
                         userCountCategoryNameCivil, userCountCategoryNameNull)).when(roleAssignmentRepositoryMock)
            .getOrgUserCountByJurisdictionAndRoleName();

        Map<String, Object> response = sut.getOrgUserCount();
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount> responseOrgUserCountByJurisdiction =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryAndCount>)
                response.get("OrgUserCountByJurisdiction");
        final List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>
            responseOrgUserCountByJurisdictionAndRoleName =
            (List<RoleAssignmentRepository.JurisdictionRoleCategoryNameAndCount>)
                response.get("OrgUserCountByJurisdictionAndRoleName");

        assertNotNull(response);
        assertEquals(BigInteger.TEN, responseOrgUserCountByJurisdiction.get(0).getCount());
        assertEquals("some role category", responseOrgUserCountByJurisdiction.get(0).getRoleCategory());
        assertEquals(BigInteger.TWO, responseOrgUserCountByJurisdictionAndRoleName.get(0).getCount());
        assertEquals("IA", responseOrgUserCountByJurisdictionAndRoleName.get(0).getJurisdiction());
        assertEquals(BigInteger.ONE, responseOrgUserCountByJurisdictionAndRoleName.get(1).getCount());
        assertEquals("CIVIL", responseOrgUserCountByJurisdictionAndRoleName.get(1).getJurisdiction());
        assertEquals(BigInteger.valueOf(3), responseOrgUserCountByJurisdictionAndRoleName.get(2).getCount());
        assertEquals(null, responseOrgUserCountByJurisdictionAndRoleName.get(2).getJurisdiction());
    }

    @Test
    void getEventListTest() throws JsonProcessingException {
        String localDateTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        List<Map<String, String>> mapList = sut.getEventMapList(List.of(
            userCountCategoryNameIa,
            userCountCategoryNameCivil,
            userCountCategoryNameCivil2,
            userCountCategoryNameNull,
            userCountCategoryNameNull2), localDateTime);

        assertEquals(3, mapList.size());
        assertEquals(localDateTime, mapList.get(0).get("timestamp"));
        assertEquals(localDateTime, mapList.get(1).get("timestamp"));
        assertEquals(localDateTime, mapList.get(2).get("timestamp"));
    }
}
