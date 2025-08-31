package com.josiqq.qrtac.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String ticketCode; // Código único del ticket (UUID)
    
    @Column(unique = true, nullable = false)
    private String qrToken; // Token encriptado para el QR
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;
    
    @Column(nullable = false)
    private LocalDateTime purchaseDate = LocalDateTime.now();
    
    private LocalDateTime usedAt;
    
    private LocalDateTime cancelledAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy; // Usuario organizador que validó el ticket
    
    public enum TicketStatus {
        VALID,      // Ticket válido, no usado
        USED,       // Ticket ya utilizado
        CANCELLED,  // Ticket cancelado
        EXPIRED     // Ticket expirado
    }
    
    // Método para marcar el ticket como usado
    public void markAsUsed(User validator) {
        this.status = TicketStatus.USED;
        this.usedAt = LocalDateTime.now();
        this.validatedBy = validator;
    }
    
    // Método para cancelar el ticket
    public void cancel() {
        this.status = TicketStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
    
    // Método para verificar si el ticket es válido para usar
    public boolean isValidForUse() {
        return this.status == TicketStatus.VALID && 
               this.event.getEventDate().isAfter(LocalDateTime.now());
    }
    
    // Método para verificar si el ticket está expirado
    public boolean isExpired() {
        return this.event.getEventDate().isBefore(LocalDateTime.now()) && 
               this.status == TicketStatus.VALID;
    }
}