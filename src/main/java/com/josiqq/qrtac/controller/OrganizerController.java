package com.josiqq.qrtac.controller;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.model.TicketRequest;
import jakarta.validation.Valid;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.service.EmailService;
import com.josiqq.qrtac.service.EventService;
import com.josiqq.qrtac.service.TicketRequestService;
import com.josiqq.qrtac.service.TicketService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/organizer")
public class OrganizerController {

    @Autowired
    private EventService eventService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TicketRequestService ticketRequestService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal User organizer, Model model) {
        List<Event> events = eventService.findEventsByOrganizer(organizer);
        List<TicketRequest> pendingRequests = ticketRequestService.findPendingRequestsByOrganizer(organizer);

        // Calcular estadísticas
        long activeEvents = events.stream()
                .filter(e -> e.getStatus() == Event.EventStatus.ACTIVE)
                .count();

        long totalApprovedTickets = events.stream()
                .mapToLong(e -> ticketRequestService.getApprovedTicketsCount(e))
                .sum();

        BigDecimal totalRevenue = events.stream()
                .map(e -> {
                    long approvedTickets = ticketRequestService.getApprovedTicketsCount(e);
                    return e.getPrice().multiply(BigDecimal.valueOf(approvedTickets));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long pendingRequestsCount = ticketRequestService.countPendingRequestsByOrganizer(organizer);

        // Agregar todo al modelo
        model.addAttribute("events", events);
        model.addAttribute("organizer", organizer);
        model.addAttribute("activeEventsCount", activeEvents);
        model.addAttribute("approvedTicketsCount", totalApprovedTickets);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("pendingRequestsCount", pendingRequestsCount);
        model.addAttribute("recentRequests",
                pendingRequests.size() > 5 ? pendingRequests.subList(0, 5) : pendingRequests);

        return "organizer/dashboard";
    }

    @GetMapping("/requests")
    public String viewAllRequests(@AuthenticationPrincipal User organizer, Model model) {
        List<TicketRequest> allRequests = ticketRequestService.findRequestsByOrganizer(organizer);
        List<TicketRequest> pendingRequests = ticketRequestService.findPendingRequestsByOrganizer(organizer);

        model.addAttribute("allRequests", allRequests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("organizer", organizer);

        return "organizer/requests";
    }

    @GetMapping("/requests/{id}")
    public String viewRequest(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            Model model) {
        Optional<TicketRequest> requestOpt = ticketRequestService.findById(id);
        if (requestOpt.isEmpty()) {
            return "redirect:/organizer/requests";
        }

        TicketRequest request = requestOpt.get();
        if (!request.getEvent().getOrganizer().getId().equals(organizer.getId())) {
            return "redirect:/organizer/requests";
        }

        model.addAttribute("request", request);
        return "organizer/request-detail";
    }

    @PostMapping("/requests/{id}/approve")
    public String approveRequest(@PathVariable Long id,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal User organizer,
            RedirectAttributes redirectAttributes) {
        try {
            ticketRequestService.approveRequest(id, organizer, notes);
            redirectAttributes.addFlashAttribute("success",
                    "Solicitud aprobada exitosamente. Se ha enviado un email al solicitante.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/organizer/requests/" + id;
    }

    @PostMapping("/requests/{id}/reject")
    public String rejectRequest(@PathVariable Long id,
            @RequestParam(required = false) String notes,
            @AuthenticationPrincipal User organizer,
            RedirectAttributes redirectAttributes) {
        try {
            ticketRequestService.rejectRequest(id, organizer, notes);
            redirectAttributes.addFlashAttribute("success",
                    "Solicitud rechazada. Se ha enviado un email al solicitante.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/organizer/requests/" + id;
    }

    @GetMapping("/events/create")
    public String createEventForm(Model model) {
        model.addAttribute("event", new Event());
        return "organizer/create-event";
    }

    @PostMapping("/events/create")
    public String createEvent(@Valid @ModelAttribute Event event,
            BindingResult result,
            @AuthenticationPrincipal User organizer,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "organizer/create-event";
        }

        try {
            event.setOrganizer(organizer);
            eventService.createEvent(event);
            redirectAttributes.addFlashAttribute("success", "Evento creado exitosamente");
            return "redirect:/organizer/dashboard";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "organizer/create-event";
        }
    }

    @GetMapping("/events/{id}/edit")
    public String editEventForm(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            Model model) {
        Optional<Event> eventOpt = eventService.findById(id);
        if (eventOpt.isEmpty()) {
            return "redirect:/organizer/dashboard";
        }

        Event event = eventOpt.get();
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            return "redirect:/organizer/dashboard";
        }

        model.addAttribute("event", event);
        return "organizer/edit-event";
    }

    @PostMapping("/events/{id}/edit")
    public String updateEvent(@PathVariable Long id,
            @Valid @ModelAttribute Event event,
            BindingResult result,
            @AuthenticationPrincipal User organizer,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "organizer/edit-event";
        }

        try {
            event.setId(id);
            event.setOrganizer(organizer);
            eventService.updateEvent(event);
            redirectAttributes.addFlashAttribute("success", "Evento actualizado exitosamente");
            return "redirect:/organizer/dashboard";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "organizer/edit-event";
        }
    }

    @PostMapping("/events/{id}/cancel")
    public String cancelEvent(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            RedirectAttributes redirectAttributes) {
        try {
            eventService.cancelEvent(id, organizer);
            redirectAttributes.addFlashAttribute("success", "Evento cancelado exitosamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/organizer/dashboard";
    }

    @PostMapping("/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            RedirectAttributes redirectAttributes) {
        try {
            eventService.deleteEvent(id, organizer);
            redirectAttributes.addFlashAttribute("success", "Evento eliminado exitosamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/organizer/dashboard";
    }

    @GetMapping("/events/{id}/requests")
    public String viewEventRequests(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            Model model) {
        Optional<Event> eventOpt = eventService.findById(id);
        if (eventOpt.isEmpty()) {
            return "redirect:/organizer/dashboard";
        }

        Event event = eventOpt.get();
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            return "redirect:/organizer/dashboard";
        }

        List<TicketRequest> requests = ticketRequestService.findRequestsByEvent(event);
        long approvedTickets = ticketRequestService.getApprovedTicketsCount(event);
        long pendingTickets = ticketRequestService.getPendingTicketsCount(event);

        model.addAttribute("event", event);
        model.addAttribute("requests", requests);
        model.addAttribute("approvedTickets", approvedTickets);
        model.addAttribute("pendingTickets", pendingTickets);
        model.addAttribute("availableCapacity", event.getCapacity() - approvedTickets);

        return "organizer/event-requests";
    }

    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal User organizer, Model model) {
        model.addAttribute("organizer", organizer);
        return "organizer/profile";
    }

    /**
     * Generar tickets manualmente para una solicitud aprobada
     */
    @PostMapping("/requests/{id}/generate-tickets")
    public String generateTicketsForRequest(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            RedirectAttributes redirectAttributes) {
        try {
            Optional<TicketRequest> requestOpt = ticketRequestService.findById(id);
            if (requestOpt.isEmpty()) {
                throw new RuntimeException("Solicitud no encontrada");
            }

            TicketRequest request = requestOpt.get();

            // Verificar permisos
            if (!request.getEvent().getOrganizer().getId().equals(organizer.getId())) {
                throw new RuntimeException("No tienes permisos para generar tickets de esta solicitud");
            }

            if (request.getStatus() != TicketRequest.RequestStatus.APPROVED) {
                throw new RuntimeException("Solo se pueden generar tickets de solicitudes aprobadas");
            }

            // Generar tickets
            List<Ticket> tickets = ticketService.generateTicketsFromRequest(request);

            // Enviar por email
            emailService.sendRequestApprovalWithTickets(request, tickets);

            redirectAttributes.addFlashAttribute("success",
                    "Se generaron " + tickets.size() + " tickets y se enviaron por email a " + request.getEmail());

        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/organizer/requests/" + id;
    }

    /**
     * Descargar QR individual de un ticket
     */
    @GetMapping("/tickets/{ticketCode}/qr-download")
    public ResponseEntity<byte[]> downloadTicketQR(@PathVariable String ticketCode,
            @AuthenticationPrincipal User organizer) {
        try {
            Optional<Ticket> ticketOpt = ticketService.findByTicketCode(ticketCode);
            if (ticketOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Ticket ticket = ticketOpt.get();

            // Verificar que el organizador tenga permisos
            if (!ticket.getEvent().getOrganizer().getId().equals(organizer.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            byte[] qrImage = ticketService.generateQrCode(ticket.getQrToken(), 400, 400);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentDispositionFormData("attachment", "Ticket_QR_" + ticketCode + ".png");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(qrImage);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Ver tickets generados para una solicitud
     */
    @GetMapping("/requests/{id}/tickets")
    public String viewRequestTickets(@PathVariable Long id,
            @AuthenticationPrincipal User organizer,
            Model model) {
        Optional<TicketRequest> requestOpt = ticketRequestService.findById(id);
        if (requestOpt.isEmpty()) {
            return "redirect:/organizer/requests";
        }

        TicketRequest request = requestOpt.get();

        // Verificar permisos
        if (!request.getEvent().getOrganizer().getId().equals(organizer.getId())) {
            return "redirect:/organizer/requests";
        }

        // Buscar tickets generados para este cliente y evento
        // (Esto requeriría un método adicional en TicketService)
        // List<Ticket> tickets =
        // ticketService.findTicketsByRequestEmail(request.getEmail(),
        // request.getEvent());

        model.addAttribute("request", request);
        // model.addAttribute("tickets", tickets);

        return "organizer/request-tickets";
    }
}