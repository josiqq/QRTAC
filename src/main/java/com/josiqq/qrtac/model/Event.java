package com.josiqq.qrtac.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "El nombre del evento es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Column(nullable = false)
    private String name;
    
    @NotBlank(message = "La descripción es obligatoria")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    
    @NotNull(message = "La fecha del evento es obligatoria")
    @Future(message = "La fecha del evento debe ser futura")
    @Column(nullable = false)
    private LocalDateTime eventDate;
    
    @NotBlank(message = "El lugar es obligatorio")
    @Column(nullable = false)
    private String venue;
    
    @NotNull(message = "La capacidad es obligatoria")
    @Min(value = 1, message = "La capacidad debe ser al menos 1")
    @Column(nullable = false)
    private Integer capacity;
    
    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio debe ser mayor o igual a 0")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(nullable = false)
    private Integer availableTickets;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.ACTIVE;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;
    
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    private List<Ticket> tickets;
    
    @PrePersist
    public void prePersist() {
        if (availableTickets == null) {
            availableTickets = capacity;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    public enum EventStatus {
        ACTIVE, CANCELLED, COMPLETED
    }
    
    // Método auxiliar para verificar si hay cupos disponibles
    public boolean hasAvailableTickets() {
        return availableTickets > 0;
    }
    
    // Método para reservar un ticket
    public boolean reserveTicket() {
        if (hasAvailableTickets()) {
            availableTickets--;
            return true;
        }
        return false;
    }
    
    // Método para liberar un ticket (si se cancela)
    public void releaseTicket() {
        if (availableTickets < capacity) {
            availableTickets++;
        }
    }
}