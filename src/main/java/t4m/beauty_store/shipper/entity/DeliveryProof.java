package t4m.beauty_store.shipper.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Private, normalized image. Never serialize this entity into an order response. */
@Entity
@Table(name = "delivery_proofs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryProof {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Lob
    @Column(name = "image_bytes", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageBytes;
}
