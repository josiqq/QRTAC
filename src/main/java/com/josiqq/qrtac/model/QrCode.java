package com.josiqq.qrtac.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class QrCode {
    @Id
    private String content;

    private boolean scanned;
    private LocalDateTime scannedAt;
}
