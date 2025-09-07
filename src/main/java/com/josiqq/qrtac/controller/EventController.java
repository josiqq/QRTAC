package com.josiqq.qrtac.controller;

import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.TicketRequest;
import com.josiqq.qrtac.service.EventService;
import com.josiqq.qrtac.service.TicketRequestService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class EventController {

    @Autowired
    private EventService eventService;
    
    @Autowired
    private TicketRequestService ticketRequestService;


    @GetMapping("/")
    public String home(Model model) {
        // Mostrar eventos destacados en la página principal
        List<Event> featuredEvents = eventService.findAvailableEvents();
        model.addAttribute("featuredEvents", featuredEvents.size() > 6 ? featuredEvents.subList(0, 6) : featuredEvents);
        return "index";
    }

    @GetMapping("/events")
    public String listEvents(Model model) {
        List<Event> events = eventService.findAvailableEvents();
        model.addAttribute("events", events);
        return "public/list";
    }

    @GetMapping("/events/{id}")
    public String viewEvent(@PathVariable Long id, Model model) {
        Optional<Event> eventOpt = eventService.findById(id);
        if (eventOpt.isEmpty() || eventOpt.get().getStatus() != Event.EventStatus.ACTIVE) {
            return "redirect:/events";
        }
        
        Event event = eventOpt.get();
        
        // Calcular tickets disponibles considerando solicitudes aprobadas
        long approvedTickets = ticketRequestService.getApprovedTicketsCount(event);
        long availableTickets = event.getCapacity() - approvedTickets;
        
        model.addAttribute("event", event);
        model.addAttribute("availableTickets", availableTickets);
        model.addAttribute("ticketRequest", new TicketRequest());
        
        return "public/event-detail";
    }

    @PostMapping("/events/{id}/request")
    public String requestTickets(@PathVariable Long id,
                                @Valid @ModelAttribute TicketRequest ticketRequest,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        
        if (result.hasErrors()) {
            Optional<Event> eventOpt = eventService.findById(id);
            if (eventOpt.isPresent()) {
                model.addAttribute("event", eventOpt.get());
                long approvedTickets = ticketRequestService.getApprovedTicketsCount(eventOpt.get());
                model.addAttribute("availableTickets", eventOpt.get().getCapacity() - approvedTickets);
            }
            return "public/event-detail";
        }

        try {
            TicketRequest savedRequest = ticketRequestService.createRequest(ticketRequest, id);
            redirectAttributes.addFlashAttribute("success", 
                "¡Solicitud enviada exitosamente! " +
                "Te contactaremos pronto. Número de solicitud: #" + savedRequest.getId());
            return "redirect:/events/" + id + "?success=true";
        } catch (RuntimeException e) {
            Optional<Event> eventOpt = eventService.findById(id);
            if (eventOpt.isPresent()) {
                model.addAttribute("event", eventOpt.get());
                long approvedTickets = ticketRequestService.getApprovedTicketsCount(eventOpt.get());
                model.addAttribute("availableTickets", eventOpt.get().getCapacity() - approvedTickets);
            }
            model.addAttribute("error", e.getMessage());
            return "public/event-detail";
        }
    }

    @GetMapping("/requests/status")
    public String checkRequestStatus(@RequestParam(required = false) Long id,
                                   @RequestParam(required = false) String email,
                                   Model model) {
        
        // Si no hay parámetros, mostrar el formulario de consulta
        if (id == null || email == null || email.trim().isEmpty()) {
            return "public/request-status";
        }
        
        try {
            Optional<TicketRequest> requestOpt = ticketRequestService.findById(id);
            if (requestOpt.isEmpty() || !requestOpt.get().getEmail().equalsIgnoreCase(email.trim())) {
                model.addAttribute("error", "Solicitud no encontrada o email incorrecto");
                return "public/request-status";
            }
            
            model.addAttribute("request", requestOpt.get());
            return "public/request-status";
        } catch (Exception e) {
            model.addAttribute("error", "Error al consultar la solicitud: " + e.getMessage());
            return "public/request-status";
        }
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id,
                               @RequestParam String email,
                               RedirectAttributes redirectAttributes) {
        try {
            ticketRequestService.cancelRequest(id, email.trim());
            redirectAttributes.addFlashAttribute("success", "Solicitud cancelada exitosamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/requests/status?id=" + id + "&email=" + email;
    }

    @GetMapping("/contact")
    public String contact() {
        return "public/contact";
    }

    @GetMapping("/about")
    public String about() {
        return "public/about";
    }
    
    @GetMapping("/requests/search")
    public String searchRequestsByEmail(@RequestParam(required = false) String email, Model model) {
        if (email != null && !email.trim().isEmpty()) {
            try {
                List<TicketRequest> requests = ticketRequestService.findRequestsByEmail(email.trim());
                model.addAttribute("requests", requests);
                model.addAttribute("searchEmail", email.trim());
            } catch (Exception e) {
                model.addAttribute("error", "Error al buscar solicitudes: " + e.getMessage());
            }
        }
        return "public/search-requests";
    }
}