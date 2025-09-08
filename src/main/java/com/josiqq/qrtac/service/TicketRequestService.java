package com.josiqq.qrtac.service;

import com.josiqq.qrtac.model.TicketRequest;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.repository.TicketRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TicketRequestService {

    @Autowired
    private TicketRequestRepository ticketRequestRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TicketService ticketService;

    public TicketRequest createRequest(TicketRequest request, Long eventId) {
        // Verificar que el evento existe y está disponible
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            throw new RuntimeException("Evento no encontrado");
        }

        Event event = eventOpt.get();

        // Validaciones de negocio
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            throw new RuntimeException("El evento no está disponible para solicitudes");
        }

        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El evento ya ha pasado");
        }

        // Verificar si hay capacidad suficiente (considerando solicitudes aprobadas)
        long approvedTickets = ticketRequestRepository.sumQuantityByEventAndStatus(event,
                TicketRequest.RequestStatus.APPROVED);
        long pendingTickets = ticketRequestRepository.sumQuantityByEventAndStatus(event,
                TicketRequest.RequestStatus.PENDING);

        if ((approvedTickets + pendingTickets + request.getQuantity()) > event.getCapacity()) {
            throw new RuntimeException("No hay suficiente capacidad disponible para esta cantidad de tickets");
        }
        request.setId(null);
        request.setEvent(event);
        request.setRequestDate(LocalDateTime.now());
        request.setStatus(TicketRequest.RequestStatus.PENDING);

        TicketRequest savedRequest = ticketRequestRepository.save(request);

        // Enviar notificación por email al organizador
        try {
            emailService.sendNewRequestNotification(savedRequest);
        } catch (Exception e) {
            // Log error but don't fail the request creation
            System.err.println("Error enviando notificación de nueva solicitud: " + e.getMessage());
        }

        // Enviar confirmación al solicitante
        try {
            emailService.sendRequestConfirmation(savedRequest);
        } catch (Exception e) {
            System.err.println("Error enviando confirmación de solicitud: " + e.getMessage());
        }

        return savedRequest;
    }

    public TicketRequest approveRequest(Long requestId, User organizer, String notes) {
        Optional<TicketRequest> requestOpt = ticketRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new RuntimeException("Solicitud no encontrada");
        }

        TicketRequest request = requestOpt.get();

        if (!request.getEvent().getOrganizer().getId().equals(organizer.getId())) {
            throw new RuntimeException("No tienes permisos para procesar esta solicitud");
        }

        if (!request.canBeProcessed()) {
            throw new RuntimeException("Esta solicitud ya ha sido procesada");
        }

        // Verificar capacidad disponible
        long approvedTickets = ticketRequestRepository.sumQuantityByEventAndStatus(request.getEvent(),
                TicketRequest.RequestStatus.APPROVED);
        if ((approvedTickets + request.getQuantity()) > request.getEvent().getCapacity()) {
            throw new RuntimeException("No hay suficiente capacidad disponible");
        }

        // Aprobar la solicitud
        request.approve(organizer, notes);
        TicketRequest savedRequest = ticketRequestRepository.save(request);

        try {
            // 🎫 GENERAR TICKETS AUTOMÁTICAMENTE
            List<Ticket> generatedTickets = ticketService.generateTicketsFromRequest(savedRequest);

            // 📧 Enviar email de aprobación con PDFs individuales
            emailService.sendRequestApprovalWithTicketsSimulation(savedRequest, generatedTickets);

            System.out.println("✅ Solicitud aprobada exitosamente:");
            System.out.println("   - Tickets generados: " + generatedTickets.size());
            System.out.println("   - Email enviado a: " + savedRequest.getEmail());
            System.out.println("   - PDFs individuales generados para cada ticket");

        } catch (Exception e) {
            System.err.println("❌ Error generando tickets o enviando email: " + e.getMessage());
            e.printStackTrace();
            
            // Enviar email normal sin tickets como fallback
            try {
                emailService.sendRequestApproval(savedRequest);
                System.out.println("📧 Email de fallback enviado sin tickets");
            } catch (Exception fallbackError) {
                System.err.println("❌ Error enviando email de fallback: " + fallbackError.getMessage());
            }
        }

        return savedRequest;
    }

    public TicketRequest rejectRequest(Long requestId, User organizer, String notes) {
        Optional<TicketRequest> requestOpt = ticketRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new RuntimeException("Solicitud no encontrada");
        }

        TicketRequest request = requestOpt.get();

        // Verificar que el organizador tiene permisos
        if (!request.getEvent().getOrganizer().getId().equals(organizer.getId()) &&
                organizer.getRole() != User.Role.ORGANIZER) {
            throw new RuntimeException("No tienes permisos para procesar esta solicitud");
        }

        if (!request.canBeProcessed()) {
            throw new RuntimeException("Esta solicitud ya ha sido procesada");
        }

        request.reject(organizer, notes);
        TicketRequest savedRequest = ticketRequestRepository.save(request);

        // Enviar email de rechazo al solicitante
        try {
            emailService.sendRequestRejection(savedRequest);
        } catch (Exception e) {
            System.err.println("Error enviando email de rechazo: " + e.getMessage());
        }

        return savedRequest;
    }

    public Optional<TicketRequest> findById(Long id) {
        return ticketRequestRepository.findById(id);
    }

    public List<TicketRequest> findRequestsByOrganizer(User organizer) {
        return ticketRequestRepository.findByOrganizerOrderByRequestDateDesc(organizer);
    }

    public List<TicketRequest> findPendingRequestsByOrganizer(User organizer) {
        return ticketRequestRepository.findPendingByOrganizer(organizer);
    }

    public List<TicketRequest> findRequestsByEvent(Event event) {
        return ticketRequestRepository.findByEventOrderByRequestDateDesc(event);
    }

    public List<TicketRequest> findRequestsByEmail(String email) {
        return ticketRequestRepository.findByEmailOrderByRequestDateDesc(email);
    }

    public long countPendingRequestsByOrganizer(User organizer) {
        return ticketRequestRepository.countPendingByOrganizer(organizer);
    }

    public List<TicketRequest> getRecentRequests() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return ticketRequestRepository.findRecentRequests(since);
    }

    // Estadísticas para el dashboard del organizador
    public long getApprovedTicketsCount(Event event) {
        return ticketRequestRepository.sumQuantityByEventAndStatus(event, TicketRequest.RequestStatus.APPROVED);
    }

    public long getPendingTicketsCount(Event event) {
        return ticketRequestRepository.sumQuantityByEventAndStatus(event, TicketRequest.RequestStatus.PENDING);
    }

    public void cancelRequest(Long requestId, String email) {
        Optional<TicketRequest> requestOpt = ticketRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            throw new RuntimeException("Solicitud no encontrada");
        }

        TicketRequest request = requestOpt.get();

        // Verificar que el email coincide
        if (!request.getEmail().equals(email)) {
            throw new RuntimeException("No tienes permisos para cancelar esta solicitud");
        }

        if (!request.canBeProcessed()) {
            throw new RuntimeException("Esta solicitud ya ha sido procesada y no puede cancelarse");
        }

        request.cancel();
        ticketRequestRepository.save(request);

        // Notificar al organizador de la cancelación
        try {
            emailService.sendRequestCancellation(request);
        } catch (Exception e) {
            System.err.println("Error enviando notificación de cancelación: " + e.getMessage());
        }
    }
}