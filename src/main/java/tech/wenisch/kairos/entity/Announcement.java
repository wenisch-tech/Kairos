package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AnnouncementKind kind;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String createdBy;

    private boolean active;

    private LocalDateTime activeUntil;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
