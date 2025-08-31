package com.josiqq.qrtac.repository;

import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketCode(String ticketCode);
    Optional<Ticket> findByQrToken(String qrToken);
    List<Ticket> findByClientOrderByPurchaseDateDesc(User client);
    List<Ticket> findByEventOrderByPurchaseDateDesc(Event event);
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.event = :event AND t.status != 'CANCELLED'")
    long countSoldTicketsByEvent(Event event);
    
    @Query("SELECT t FROM Ticket t WHERE t.event = :event AND t.status = :status")
    List<Ticket> findByEventAndStatus(Event event, Ticket.TicketStatus status);
}