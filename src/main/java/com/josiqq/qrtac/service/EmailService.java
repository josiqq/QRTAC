package com.josiqq.qrtac.service;

import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.model.TicketRequest;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Base64;

@Service
public class EmailService {

    private final Resend resend;

    @Autowired
    private TicketService ticketService;

    @Value("${app.email.from:noreply@qrtac.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    public EmailService(@Value("${resend.api-key:}") String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            this.resend = new Resend(apiKey);
        } else {
            this.resend = null;
        }
    }

    public void sendNewRequestNotification(TicketRequest request) {
        if (resend == null || resendApiKey.isEmpty()) {
            System.out.println("SIMULACIÓN EMAIL - Nueva solicitud de ticket:");
            System.out.println("Para: " + request.getEvent().getOrganizer().getEmail());
            System.out.println("Evento: " + request.getEvent().getName());
            System.out.println("Solicitante: " + request.getFullName() + " (" + request.getEmail() + ")");
            System.out.println("Cantidad: " + request.getQuantity() + " tickets");
            return;
        }

        String content = String.format(
                "Hola %s,\n\n" +
                        "Has recibido una nueva solicitud de ticket para tu evento:\n\n" +
                        "Evento: %s\n" +
                        "Solicitante: %s\n" +
                        "Email: %s\n" +
                        "Teléfono: %s\n" +
                        "Cantidad de tickets: %d\n" +
                        "Método de contacto preferido: %s\n\n" +
                        "%s\n\n" +
                        "Puedes gestionar esta solicitud ingresando a tu panel de organizador:\n" +
                        "%s/organizer/dashboard\n\n" +
                        "Saludos,\n" +
                        "Equipo QRTAC",
                request.getEvent().getOrganizer().getFullName(),
                request.getEvent().getName(),
                request.getFullName(),
                request.getEmail(),
                request.getPhone(),
                request.getQuantity(),
                request.getPreferredContactMethod(),
                request.getMessage() != null ? "Mensaje: " + request.getMessage() : "",
                baseUrl);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(request.getEvent().getOrganizer().getEmail())
                .subject("Nueva solicitud de ticket - " + request.getEvent().getName())
                .text(content)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email enviado con ID: " + data.getId());
        } catch (ResendException e) {
            System.err.println("Error enviando email: " + e.getMessage());
        }
    }

    public void sendRequestConfirmation(TicketRequest request) {
        if (resend == null || resendApiKey.isEmpty()) {
            System.out.println("SIMULACIÓN EMAIL - Confirmación de solicitud:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("Solicitud ID: " + request.getId());
            return;
        }

        String content = String.format(
                "Hola %s,\n\n" +
                        "Hemos recibido tu solicitud de ticket para el evento:\n\n" +
                        "Evento: %s\n" +
                        "Fecha del evento: %s\n" +
                        "Lugar: %s\n" +
                        "Cantidad solicitada: %d tickets\n" +
                        "Número de solicitud: #%d\n\n" +
                        "Tu solicitud está siendo revisada por el organizador. " +
                        "Te contactaremos pronto con más información.\n\n" +
                        "Puedes consultar el estado de tu solicitud en:\n" +
                        "%s/requests/status?id=%d&email=%s\n\n" +
                        "Gracias por tu interés,\n" +
                        "Equipo QRTAC",
                request.getFullName(),
                request.getEvent().getName(),
                request.getEvent().getEventDate(),
                request.getEvent().getVenue(),
                request.getQuantity(),
                request.getId(),
                baseUrl,
                request.getId(),
                request.getEmail());

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(request.getEmail())
                .subject("Confirmación de solicitud de ticket - " + request.getEvent().getName())
                .text(content)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email enviado con ID: " + data.getId());
        } catch (ResendException e) {
            System.err.println("Error enviando email: " + e.getMessage());
        }
    }

    public void sendRequestApproval(TicketRequest request) {
        if (resend == null || resendApiKey.isEmpty()) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud aprobada:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("El organizador debe enviar los tickets por " + request.getPreferredContactMethod());
            return;
        }

        String contactInfo = getOrganizerContactInfo(request);

        String content = String.format(
                "¡Excelente noticia %s!\n\n" +
                        "Tu solicitud de tickets ha sido APROBADA:\n\n" +
                        "Evento: %s\n" +
                        "Fecha: %s\n" +
                        "Lugar: %s\n" +
                        "Cantidad de tickets: %d\n" +
                        "Precio por ticket: $%.2f\n" +
                        "Total a pagar: $%.2f\n\n" +
                        "%s\n\n" +
                        "INSTRUCCIONES DE PAGO Y ENTREGA:\n" +
                        "%s\n\n" +
                        "El organizador se pondrá en contacto contigo pronto para " +
                        "coordinar el pago y la entrega de los tickets.\n\n" +
                        "¡Nos vemos en el evento!\n" +
                        "Equipo QRTAC",
                request.getFullName(),
                request.getEvent().getName(),
                request.getEvent().getEventDate(),
                request.getEvent().getVenue(),
                request.getQuantity(),
                request.getEvent().getPrice(),
                request.getEvent().getPrice().multiply(java.math.BigDecimal.valueOf(request.getQuantity())),
                request.getOrganizerNotes() != null ? "Notas del organizador: " + request.getOrganizerNotes() : "",
                contactInfo);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(request.getEmail())
                .subject("¡Solicitud aprobada! - " + request.getEvent().getName())
                .text(content)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email enviado con ID: " + data.getId());
        } catch (ResendException e) {
            System.err.println("Error enviando email: " + e.getMessage());
        }
    }

    public void sendRequestRejection(TicketRequest request) {
        if (resend == null || resendApiKey.isEmpty()) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud rechazada:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("Motivo: " + request.getOrganizerNotes());
            return;
        }

        String content = String.format(
                "Hola %s,\n\n" +
                        "Lamentablemente, tu solicitud de tickets para el evento \"%s\" " +
                        "no pudo ser procesada en esta ocasión.\n\n" +
                        "%s\n\n" +
                        "Te invitamos a estar atento a futuros eventos.\n\n" +
                        "Gracias por tu interés,\n" +
                        "Equipo QRTAC",
                request.getFullName(),
                request.getEvent().getName(),
                request.getOrganizerNotes() != null ? "Motivo: " + request.getOrganizerNotes() : "");

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(request.getEmail())
                .subject("Actualización de tu solicitud - " + request.getEvent().getName())
                .text(content)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email enviado con ID: " + data.getId());
        } catch (ResendException e) {
            System.err.println("Error enviando email: " + e.getMessage());
        }
    }

    public void sendRequestCancellation(TicketRequest request) {
        if (resend == null || resendApiKey.isEmpty()) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud cancelada por cliente:");
            System.out.println("Para organizador: " + request.getEvent().getOrganizer().getEmail());
            return;
        }

        String content = String.format(
                "Hola %s,\n\n" +
                        "El solicitante %s ha cancelado su solicitud de tickets:\n\n" +
                        "Evento: %s\n" +
                        "Cantidad que había solicitado: %d tickets\n" +
                        "Email del solicitante: %s\n\n" +
                        "Esta cancelación libera cupos para otras solicitudes.\n\n" +
                        "Saludos,\n" +
                        "Equipo QRTAC",
                request.getEvent().getOrganizer().getFullName(),
                request.getFullName(),
                request.getEvent().getName(),
                request.getQuantity(),
                request.getEmail());

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(request.getEvent().getOrganizer().getEmail())
                .subject("Solicitud cancelada - " + request.getEvent().getName())
                .text(content)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email enviado con ID: " + data.getId());
        } catch (ResendException e) {
            System.err.println("Error enviando email: " + e.getMessage());
        }
    }

    private String getOrganizerContactInfo(TicketRequest request) {
        String contactMethod = request.getPreferredContactMethod();
        String organizerPhone = request.getEvent().getOrganizer().getPhone();
        String organizerEmail = request.getEvent().getOrganizer().getEmail();

        StringBuilder contact = new StringBuilder();
        contact.append("INFORMACIÓN DE CONTACTO DEL ORGANIZADOR:\n");
        contact.append("Nombre: ").append(request.getEvent().getOrganizer().getFullName()).append("\n");
        contact.append("Email: ").append(organizerEmail).append("\n");

        if (organizerPhone != null && !organizerPhone.isEmpty()) {
            contact.append("Teléfono/WhatsApp: ").append(organizerPhone).append("\n");
        }

        if ("WHATSAPP".equals(contactMethod) && organizerPhone != null) {
            contact.append("\nTus tickets serán enviados por WhatsApp al número: ").append(request.getPhone());
        } else if ("EMAIL".equals(contactMethod)) {
            contact.append("\nTus tickets serán enviados por email a: ").append(request.getEmail());
        }

        return contact.toString();
    }

    /**
     * Envía email de aprobación con tickets adjuntos
     */
    public void sendRequestApprovalWithTickets(TicketRequest request, List<Ticket> tickets) {
        try {
            String subject = "🎉 ¡Solicitud aprobada! Aquí tienes tus tickets - " + request.getEvent().getName();
            String content = buildApprovalEmailContent(request, tickets);

            Map<String, byte[]> qrImages = ticketService.generateQRImagesForTickets(tickets);

            List<Map<String, Object>> attachments = qrImages.entrySet().stream()
                    .map(entry -> Map.<String, Object>of(
                            "filename", "Ticket_QR_" + entry.getKey() + ".png",
                            "content", Base64.getEncoder().encodeToString(entry.getValue())))
                    .collect(Collectors.toList());

            // Si quieres incluir un PDF:
            // byte[] pdf = ticketService.generateTicketsPDF(tickets);
            // attachments.add(Map.of(
            // "filename", "Tickets_" + request.getEvent().getName() + ".pdf",
            // "content", Base64.getEncoder().encodeToString(pdf)
            // ));

            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(request.getEmail())
                    .subject(subject)
                    .text(content)
                    .build();

            CreateEmailResponse response = resend.emails().send(options);
            System.out.println("Email con tickets enviado. ID: " + response.getId());

        } catch (Exception e) {
            System.err.println("Error enviando email con tickets: " + e.getMessage());
            sendRequestApproval(request); // fallback
        }
    }

    private String buildApprovalEmailContent(TicketRequest request, List<Ticket> tickets) {
        StringBuilder content = new StringBuilder();

        content.append("¡Excelente noticia ").append(request.getFullName()).append("!\n\n");
        content.append("Tu solicitud de tickets ha sido APROBADA y aquí tienes tus tickets digitales:\n\n");

        content.append("📋 DETALLES DEL EVENTO:\n");
        content.append("Evento: ").append(request.getEvent().getName()).append("\n");
        content.append("Fecha: ").append(request.getEvent().getEventDate()).append("\n");
        content.append("Lugar: ").append(request.getEvent().getVenue()).append("\n");
        content.append("Cantidad de tickets: ").append(request.getQuantity()).append("\n");
        content.append("Precio por ticket: $").append(request.getEvent().getPrice()).append("\n");
        content.append("Total: $")
                .append(request.getEvent().getPrice().multiply(java.math.BigDecimal.valueOf(request.getQuantity())))
                .append("\n\n");

        if (request.getOrganizerNotes() != null && !request.getOrganizerNotes().isEmpty()) {
            content.append("💬 NOTAS DEL ORGANIZADOR:\n");
            content.append(request.getOrganizerNotes()).append("\n\n");
        }

        content.append("🎫 TUS TICKETS:\n");
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            content.append("Ticket ").append(i + 1).append(": ").append(ticket.getTicketCode()).append("\n");
        }
        content.append("\n");

        content.append("📱 IMPORTANTE:\n");
        content.append("• Cada ticket tiene un código QR único adjunto como imagen\n");
        content.append("• Presenta el QR en la entrada del evento para validación\n");
        content.append("• Guarda bien estos tickets, los necesitarás para ingresar\n");
        content.append("• Si tienes problemas, contacta al organizador\n\n");

        content.append(getOrganizerContactInfo(request)).append("\n\n");

        content.append("¡Nos vemos en el evento!\n");
        content.append("Equipo QRTAC");

        return content.toString();
    }
}