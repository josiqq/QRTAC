package com.josiqq.qrtac.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketRequest {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "El nombre completo es obligatorio")
    @Column(nullable = false)
    private String fullName;
    
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Debe ser un email válido")
    @Column(nullable = false)
    private String email;
    
    @NotBlank(message = "El teléfono es obligatorio")
    @Column(nullable = false)
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String message; // Mensaje adicional del solicitante
    
    @NotNull(message = "La cantidad de tickets es obligatoria")
    @Min(value = 1, message = "Debe solicitar al menos 1 ticket")
    @Max(value = 10, message = "Máximo 10 tickets por solicitud")
    @Column(nullable = false)
    private Integer quantity = 1;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status = RequestStatus.PENDING;
    
    @Column(nullable = false)
    private LocalDateTime requestDate = LocalDateTime.now();
    
    private LocalDateTime processedDate;
    
    @Column(columnDefinition = "TEXT")
    private String organizerNotes; // Notas del organizador
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy; // Organizador que procesó la solicitud
    
    // Información adicional para contacto
    @Column
    private String preferredContactMethod = "EMAIL"; // EMAIL, WHATSAPP, PHONE
    
    public enum RequestStatus {
        PENDING,    // Solicitud pendiente
        APPROVED,   // Solicitud aprobada - tickets enviados
        REJECTED,   // Solicitud rechazada
        CANCELLED   // Solicitud cancelada por el solicitante
    }
    
    public void approve(User organizer, String notes) {
        this.status = RequestStatus.APPROVED;
        this.processedDate = LocalDateTime.now();
        this.processedBy = organizer;
        this.organizerNotes = notes;
    }
    
    public void reject(User organizer, String notes) {
        this.status = RequestStatus.REJECTED;
        this.processedDate = LocalDateTime.now();
        this.processedBy = organizer;
        this.organizerNotes = notes;
    }
    
    public void cancel() {
        this.status = RequestStatus.CANCELLED;
    }
    
    public boolean canBeProcessed() {
        return this.status == RequestStatus.PENDING;
    }
    
    public boolean isPending() {
        return this.status == RequestStatus.PENDING;
    }
}