
package uk.gov.hmcts.reform.roleassignment.data;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Builder(toBuilder = true)
@Getter
@Setter
@Entity(name = "role_assignment_request")
@NoArgsConstructor
@AllArgsConstructor
public class RequestEntity {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    private UUID id;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "authenticated_user_id", nullable = false)
    private String authenticatedUserId;

    @Column(name = "assigner_id", nullable = false)
    private String assignerId;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "process")
    private String process;

    @Column(name = "reference")
    private String reference;

    @Column(name = "replace_existing")
    private Boolean replaceExisting;

    @Column(name = "role_assignment_id", nullable = true)
    private UUID roleAssignmentId;

    @Column(name = "log")
    private String log;

    @CreationTimestamp
    @Column(name = "created", nullable = false)
    private LocalDateTime created;

    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "requestEntity")
    private Set<HistoryEntity> historyEntities;


}

