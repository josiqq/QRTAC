package com.josiqq.qrtac.repository;

import com.josiqq.qrtac.model.TicketRequest;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRequestRepository extends JpaRepository<TicketRequest, Long> {
    
    // Buscar solicitudes por evento
    List<TicketRequest> findByEventOrderByRequestDateDesc(Event event);
    
    // Buscar solicitudes por organizador (a través del evento)
    @Query("SELECT tr FROM TicketRequest tr WHERE tr.event.organizer = :organizer ORDER BY tr.requestDate DESC")
    List<TicketRequest> findByOrganizerOrderByRequestDateDesc(User organizer);
    
    // Buscar solicitudes pendientes por organizador
    @Query("SELECT tr FROM TicketRequest tr WHERE tr.event.organizer = :organizer AND tr.status = 'PENDING' ORDER BY tr.requestDate ASC")
    List<TicketRequest> findPendingByOrganizer(User organizer);
    
    // Buscar solicitudes por email (para que las personas puedan ver sus solicitudes)
    List<TicketRequest> findByEmailOrderByRequestDateDesc(String email);
    
    // Buscar solicitudes por estado
    List<TicketRequest> findByStatusOrderByRequestDateDesc(TicketRequest.RequestStatus status);
    
    // Buscar solicitudes por evento y estado
    List<TicketRequest> findByEventAndStatusOrderByRequestDateDesc(Event event, TicketRequest.RequestStatus status);
    
    // Contar solicitudes pendientes por organizador
    @Query("SELECT COUNT(tr) FROM TicketRequest tr WHERE tr.event.organizer = :organizer AND tr.status = 'PENDING'")
    long countPendingByOrganizer(User organizer);
    
    // Contar total de tickets solicitados para un evento por estado
    @Query("SELECT COALESCE(SUM(tr.quantity), 0) FROM TicketRequest tr WHERE tr.event = :event AND tr.status = :status")
    long sumQuantityByEventAndStatus(Event event, TicketRequest.RequestStatus status);
    
    // Buscar solicitudes recientes (últimas 24 horas)
    @Query("SELECT tr FROM TicketRequest tr WHERE tr.requestDate >= :since ORDER BY tr.requestDate DESC")
    List<TicketRequest> findRecentRequests(LocalDateTime since);
}