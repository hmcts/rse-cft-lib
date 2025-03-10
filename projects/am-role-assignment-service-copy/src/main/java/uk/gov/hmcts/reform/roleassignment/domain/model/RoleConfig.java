package uk.gov.hmcts.reform.roleassignment.domain.model;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.gov.hmcts.reform.roleassignment.util.JacksonUtils.getRoleConfigs;

@Slf4j
public class RoleConfig {

    @Getter
    private static final RoleConfig roleConfig = buildRoleConfig();

    private final Map<String,RoleConfigRole> roleConfigByRoleName = new HashMap<>();

    private RoleConfig(Collection<RoleConfigRole> roles) {
        roles.forEach(r -> roleConfigByRoleName.put(String.join("_", r.getName(), r.getCategory().name(),
                                                                r.getType().name()), r));
    }

    public RoleConfigRole get(String roleName, RoleCategory roleCategory, RoleType roleType) {
        return roleConfigByRoleName.get(String.join("_", roleName, roleCategory.name(), roleType.name()));
    }

    /**
     * Copy the role name and category into each of the patterns
     * for the given role.
     */
    private static void setCommonFields(RoleConfigRole role) {
        var roleName = role.getName();
        var roleCategory = role.getCategory();
        role.getPatterns().forEach(
            p -> {
                p.setRoleName(roleName);
                p.setRoleCategory(roleCategory);
                p.setSubstantive(role.isSubstantive());
            });
    }

    private static RoleConfig buildRoleConfig() {
        List<RoleConfigRole> allRoles = getRoleConfigs();
        allRoles.forEach(RoleConfig::setCommonFields);
        return new RoleConfig(allRoles);
    }

}
