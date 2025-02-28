package uk.gov.hmcts.divorce.solicitor.event;

import uk.gov.hmcts.divorce.divorcecase.model.UserRole;

public enum SolicitorRoles {

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
    SOLICITORJ("38a2499c-0c65-3fb0-9342-e47091c766f6", UserRole.SOLICITOR_J.getRole());

    private final String id;
    private final String role;

    SolicitorRoles(String id, String role) {
        this.id = id;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }
}
