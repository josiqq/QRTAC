package com.josiqq.qrtac.controller;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.josiqq.qrtac.model.User;
import jakarta.validation.Valid;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.service.EventService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/organizer")
public class OrganizerController {

    @Autowired
    private EventService eventService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal User organizer, Model model) {
        List<Event> events = eventService.findEventsByOrganizer(organizer);

        // Calcular estadísticas aquí
        long activeEvents = events.stream()
                                  .filter(e -> e.getStatus() == Event.EventStatus.ACTIVE)
                                  .count();

        long soldTickets = events.stream()
                                 .mapToLong(e -> e.getCapacity() - e.getAvailableTickets())
                                 .sum();

        BigDecimal totalRevenue = events.stream()
                                        .map(e -> e.getPrice().multiply(new BigDecimal(e.getCapacity() - e.getAvailableTickets())))
                                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Agregar todo al modelo
        model.addAttribute("events", events);
        model.addAttribute("organizer", organizer);
        model.addAttribute("activeEventsCount", activeEvents);
        model.addAttribute("soldTicketsCount", soldTickets);
        model.addAttribute("totalRevenue", totalRevenue);

        return "organizer/dashboard";
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

    @GetMapping("/events/{id}/tickets")
    public String viewEventTickets(@PathVariable Long id, 
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
        return "organizer/event-tickets";
    }
}
