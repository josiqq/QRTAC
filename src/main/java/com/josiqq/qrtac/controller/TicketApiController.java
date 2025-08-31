package com.josiqq.qrtac.controller;

import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.service.TicketService;
import com.josiqq.qrtac.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tickets")
public class TicketApiController {

    @Autowired
    private TicketService ticketService;

    // API para obtener información del ticket (para apps móviles)
    @GetMapping("/info/{qrToken}")
    public ResponseEntity<Map<String, Object>> getTicketInfo(@PathVariable String qrToken) {
        Map<String, Object> response = ticketService.getTicketValidationInfo(qrToken);
        return ResponseEntity.ok(response);
    }

    // API para validar y usar ticket
    @PostMapping("/validate/{qrToken}")
    public ResponseEntity<Map<String, Object>> validateTicket(@PathVariable String qrToken,
            @RequestParam Long organizerId) {
        try {
            // En una implementación real, validarías el organizerId con JWT o sesión
            // Por simplicidad, asumimos que el organizerId es válido
            Optional<User> organizerOpt = userService.findById(organizerId);
            if (organizerOpt.isEmpty()) {
                Map<String, Object> response = Map.of(
                        "status", "ERROR",
                        "message", "Organizador no encontrado");
                return ResponseEntity.badRequest().body(response);
            }

            Ticket ticket = ticketService.validateTicket(qrToken, organizerOpt.get());

            Map<String, Object> response = Map.of(
                    "status", "SUCCESS",
                    "message", "Ticket validado exitosamente",
                    "ticketCode", ticket.getTicketCode(),
                    "clientName", ticket.getClient().getFullName(),
                    "eventName", ticket.getEvent().getName(),
                    "usedAt", ticket.getUsedAt());

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = Map.of(
                    "status", "ERROR",
                    "message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @Autowired
    private UserService userService;
}