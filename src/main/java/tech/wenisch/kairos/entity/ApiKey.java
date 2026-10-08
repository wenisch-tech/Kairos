package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String keyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false, length = 128)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_permission", joinColumns = @JoinColumn(name = "api_key_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 64)
    @Builder.Default
    private Set<ApiKeyPermission> permissions = EnumSet.noneOf(ApiKeyPermission.class);

}
