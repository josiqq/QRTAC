package com.josiqq.qrtac.repository;

import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByOrganizerOrderByEventDateDesc(User organizer);
    List<Event> findByStatusOrderByEventDateAsc(Event.EventStatus status);
    
    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findUpcomingActiveEvents(LocalDateTime now);
    
    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' AND e.availableTickets > 0 AND e.eventDate > :now ORDER BY e.eventDate ASC")
    List<Event> findAvailableEvents(LocalDateTime now);
}