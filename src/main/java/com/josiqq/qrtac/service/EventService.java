package com.josiqq.qrtac.service;

import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EventService {

    @Autowired
    private EventRepository eventRepository;

    public Event createEvent(Event event) {
        // Validaciones de negocio
        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("La fecha del evento debe ser futura");
        }
        
        if (event.getCapacity() <= 0) {
            throw new RuntimeException("La capacidad debe ser mayor a 0");
        }
        
        // Inicializar tickets disponibles
        event.setAvailableTickets(event.getCapacity());
        event.setStatus(Event.EventStatus.ACTIVE);
        
        return eventRepository.save(event);
    }
    
    public Event updateEvent(Event event) {
        Optional<Event> existingEvent = eventRepository.findById(event.getId());
        if (existingEvent.isEmpty()) {
            throw new RuntimeException("Evento no encontrado");
        }
        
        Event existing = existingEvent.get();
        
        // No permitir cambios si el evento ya pasó o está cancelado
        if (existing.getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("No se puede modificar un evento que ya pasó");
        }
        
        if (existing.getStatus() == Event.EventStatus.CANCELLED) {
            throw new RuntimeException("No se puede modificar un evento cancelado");
        }
        
        // Actualizar campos permitidos
        existing.setName(event.getName());
        existing.setDescription(event.getDescription());
        existing.setEventDate(event.getEventDate());
        existing.setVenue(event.getVenue());
        existing.setPrice(event.getPrice());
        
        // Solo permitir aumentar capacidad, no reducirla si ya hay tickets vendidos
        if (event.getCapacity() < existing.getCapacity()) {
            long soldTickets = existing.getCapacity() - existing.getAvailableTickets();
            if (event.getCapacity() < soldTickets) {
                throw new RuntimeException("No se puede reducir la capacidad por debajo de los tickets ya vendidos");
            }
            existing.setAvailableTickets(event.getCapacity() - (int)soldTickets);
        } else {
            int additionalCapacity = event.getCapacity() - existing.getCapacity();
            existing.setAvailableTickets(existing.getAvailableTickets() + additionalCapacity);
        }
        
        existing.setCapacity(event.getCapacity());
        
        return eventRepository.save(existing);
    }
    
    public Optional<Event> findById(Long id) {
        return eventRepository.findById(id);
    }
    
    public List<Event> findAllEvents() {
        return eventRepository.findAll();
    }
    
    public List<Event> findEventsByOrganizer(User organizer) {
        return eventRepository.findByOrganizerOrderByEventDateDesc(organizer);
    }
    
    public List<Event> findUpcomingEvents() {
        return eventRepository.findUpcomingActiveEvents(LocalDateTime.now());
    }
    
    public List<Event> findAvailableEvents() {
        return eventRepository.findAvailableEvents(LocalDateTime.now());
    }
    
    public Event cancelEvent(Long eventId, User organizer) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            throw new RuntimeException("Evento no encontrado");
        }
        
        Event event = eventOpt.get();
        
        // Verificar que el usuario sea el organizador
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("No tienes permisos para cancelar este evento");
        }
        
        // No permitir cancelar eventos que ya pasaron
        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("No se puede cancelar un evento que ya pasó");
        }
        
        event.setStatus(Event.EventStatus.CANCELLED);
        return eventRepository.save(event);
    }
    
    public void deleteEvent(Long eventId, User organizer) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            throw new RuntimeException("Evento no encontrado");
        }
        
        Event event = eventOpt.get();
        
        // Verificar que el usuario sea el organizador
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("No tienes permisos para eliminar este evento");
        }
        
        // Solo permitir eliminar si no hay tickets vendidos
        if (event.getAvailableTickets() < event.getCapacity()) {
            throw new RuntimeException("No se puede eliminar un evento con tickets vendidos");
        }
        
        eventRepository.delete(event);
    }
    
    public boolean reserveTicket(Long eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            if (event.reserveTicket()) {
                eventRepository.save(event);
                return true;
            }
        }
        return false;
    }
    
    public void releaseTicket(Long eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isPresent()) {
            Event event = eventOpt.get();
            event.releaseTicket();
            eventRepository.save(event);
        }
    }
}