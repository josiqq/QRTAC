package com.josiqq.qrtac.controller;

import com.google.zxing.WriterException;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.service.EventService;
import com.josiqq.qrtac.service.TicketService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Controller
public class TicketController {

    @Autowired
    private TicketService ticketService;
    
    @Autowired
    private EventService eventService;

    // Cliente - Dashboard
    @GetMapping("/client/dashboard")
    public String clientDashboard(@AuthenticationPrincipal User client, Model model) {
        model.addAttribute("tickets", ticketService.findTicketsByClient(client));
        model.addAttribute("availableEvents", eventService.findAvailableEvents());
        return "client/dashboard";
    }

    // Cliente - Comprar ticket
    @PostMapping("/client/tickets/purchase/{eventId}")
    public String purchaseTicket(@PathVariable Long eventId, 
                                @AuthenticationPrincipal User client, 
                                RedirectAttributes redirectAttributes) {
        try {
            Ticket ticket = ticketService.purchaseTicket(eventId, client);
            redirectAttributes.addFlashAttribute("success", 
                "Ticket comprado exitosamente. Código: " + ticket.getTicketCode());
            return "redirect:/client/dashboard";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/client/dashboard";
        }
    }

    // Cliente - Ver detalles del ticket
    @GetMapping("/client/tickets/{id}")
    public String viewTicket(@PathVariable Long id, 
                           @AuthenticationPrincipal User client, 
                           Model model) {
        Optional<Ticket> ticketOpt = ticketService.findByTicketCode(id.toString());
        if (ticketOpt.isEmpty()) {
            return "redirect:/client/dashboard";
        }
        
        Ticket ticket = ticketOpt.get();
        if (!ticket.getClient().getId().equals(client.getId())) {
            return "redirect:/client/dashboard";
        }
        
        model.addAttribute("ticket", ticket);
        return "client/ticket-detail";
    }

    // Cliente - Cancelar ticket
    @PostMapping("/client/tickets/{id}/cancel")
    public String cancelTicket(@PathVariable Long id, 
                              @AuthenticationPrincipal User client, 
                              RedirectAttributes redirectAttributes) {
        try {
            ticketService.cancelTicket(id, client);
            redirectAttributes.addFlashAttribute("success", "Ticket cancelado exitosamente");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/client/dashboard";
    }

    // Generar código QR para ticket
    @GetMapping("/tickets/{ticketCode}/qr")
    public ResponseEntity<byte[]> generateTicketQR(@PathVariable String ticketCode, 
                                                  @AuthenticationPrincipal User user) {
        try {
            Optional<Ticket> ticketOpt = ticketService.findByTicketCode(ticketCode);
            if (ticketOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            Ticket ticket = ticketOpt.get();
            
            // Verificar que el usuario sea el propietario del ticket
            if (!ticket.getClient().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            byte[] qrImage = ticketService.generateQrCode(ticket.getQrToken(), 300, 300);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(qrImage.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(qrImage);
                    
        } catch (WriterException | IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // API REST para validación de tickets (para escáner móvil)
    @GetMapping("/validate/{qrToken}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateTicketInfo(@PathVariable String qrToken) {
        Map<String, Object> response = ticketService.getTicketValidationInfo(qrToken);
        return ResponseEntity.ok(response);
    }

    // Organizador - Validar ticket (marcar como usado)
    @PostMapping("/organizer/tickets/validate/{qrToken}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateAndUseTicket(@PathVariable String qrToken, 
                                                                   @AuthenticationPrincipal User organizer) {
        try {
            Ticket ticket = ticketService.validateTicket(qrToken, organizer);
            
            Map<String, Object> response = Map.of(
                "status", "SUCCESS",
                "message", "Ticket validado exitosamente",
                "ticketCode", ticket.getTicketCode(),
                "clientName", ticket.getClient().getFullName(),
                "eventName", ticket.getEvent().getName(),
                "usedAt", ticket.getUsedAt()
            );
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = Map.of(
                "status", "ERROR",
                "message", e.getMessage()
            );
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Organizador - Página de escáner
    @GetMapping("/organizer/scanner")
    public String scannerPage(@AuthenticationPrincipal User organizer, Model model) {
        model.addAttribute("organizer", organizer);
        return "organizer/scanner";
    }

    // Organizador - Página de escáner para evento específico
    @GetMapping("/organizer/events/{eventId}/scanner")
    public String eventScannerPage(@PathVariable Long eventId, 
                                  @AuthenticationPrincipal User organizer, 
                                  Model model) {
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            return "redirect:/organizer/dashboard";
        }
        
        Event event = eventOpt.get();
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            return "redirect:/organizer/dashboard";
        }
        
        model.addAttribute("event", event);
        model.addAttribute("organizer", organizer);
        return "organizer/event-scanner";
    }
}

