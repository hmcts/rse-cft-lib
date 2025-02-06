package uk.gov.hmcts.reform.roleassignment.domain.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.idam.client.models.UserDetails;
import uk.gov.hmcts.reform.roleassignment.oidc.IdamRepository;
import uk.gov.hmcts.reform.roleassignment.oidc.OIdcAdminConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
class IdamRoleServiceTest {

    @Mock
    private IdamRepository idamRepositoryMock = mock(IdamRepository.class);

    @Mock
    private OIdcAdminConfiguration oidcAdminConfiguration = mock(OIdcAdminConfiguration.class);

    @InjectMocks
    private IdamRoleService sut = new IdamRoleService(idamRepositoryMock, oidcAdminConfiguration);

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }


    @Test
    void getUserRoles() {
        String userId = "003352d0-e699-48bc-b6f5-5810411e60af";
        UserDetails userDetails = UserDetails.builder().email("black@betty.com").forename("ram").surname("jam").id(
            "1234567890123456")
            .roles(List.of("role1", "role2")).build();

        when(idamRepositoryMock.searchUserByUserId(any(), any()))
            .thenReturn(ResponseEntity.ok().body(new ArrayList<>() {
                {
                    add(new LinkedHashMap<String, Object>() {
                            {
                                put("id", "someId");
                                put("roles", new ArrayList<String>() {
                                    {
                                        add("role1");
                                        add("role2");
                                    }
                                });
                            }
                        }
                    );
                }
            }));

        assertNotNull(sut.getUserRoles(userId));
    }

    @Test
    void getUserRolesEmptyUserDetails() {
        String userId = "003352d0-e699-48bc-b6f5-5810411e60af";

        when(idamRepositoryMock.searchUserByUserId(any(), any()))
            .thenReturn(ResponseEntity.ok().body(new ArrayList<>()));

        assertNotNull(sut.getUserRoles(userId));
    }

    @Test
    void getUserRolesBlankResponse() {
        String userId = "003352d0-e699-48bc-b6f5-5810411e60af";
        UserDetails userDetails = UserDetails.builder().email("black@betty.com").forename("ram").surname("jam").id(
            "1234567890123456")
            .roles(null).build();

        when(idamRepositoryMock.getUserByUserId(any(), any())).thenReturn(userDetails);

        assertNotNull(sut.getUserRoles(userId));
    }
}
