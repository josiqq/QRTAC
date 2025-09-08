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
import java.net.URI;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Base64;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import org.json.JSONArray;
import org.json.JSONObject;

@Service
public class EmailService {

    private final Resend resend;

    @Autowired
    private TicketService ticketService;

    @Value("${app.email.from:noreply@qrtac.com}")
    private String fromEmail;

    @Value("${app.base-url:https://qrtac.store}")
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
                .subject("🎉 ¡Solicitud aprobada! - " + request.getEvent().getName())
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

        contact.append("<h3>📞 INFORMACIÓN DE CONTACTO DEL ORGANIZADOR:</h3>");
        contact.append("<ul>");
        contact.append("<li><b>Nombre:</b> ").append(request.getEvent().getOrganizer().getFullName()).append("</li>");
        contact.append("<li><b>Email:</b> <a href=\"mailto:").append(organizerEmail).append("\">")
                .append(organizerEmail).append("</a></li>");

        if (organizerPhone != null && !organizerPhone.isEmpty()) {
            contact.append("<li><b>Teléfono/WhatsApp:</b> <a href=\"https://wa.me/")
                    .append(organizerPhone.replaceAll("[^0-9]", "")) // limpia caracteres no numéricos
                    .append("\" target=\"_blank\">")
                    .append(organizerPhone)
                    .append("</a></li>");
        }
        contact.append("</ul>");

        if ("WHATSAPP".equalsIgnoreCase(contactMethod) && organizerPhone != null) {
            contact.append("<p>📲 Tus tickets serán enviados también por <b>WhatsApp</b> al número: <b>")
                    .append(request.getPhone())
                    .append("</b></p>");
        }

        return contact.toString();
    }

    /**
     * Envía email de aprobación con tickets como PDFs individuales adjuntos
     */
    public void sendRequestApprovalWithTickets(TicketRequest request, List<Ticket> tickets) {
        try {
            String subject = "🎉 ¡Solicitud aprobada! Aquí tienes tus tickets - " + request.getEvent().getName();

            // Generar PDFs individuales para cada ticket
            Map<String, byte[]> ticketPdfs = ticketService.generateIndividualTicketPDFs(tickets);

            // Convertir PDFs en adjuntos (filename + base64 content)
            JSONArray attachments = new JSONArray(
                    ticketPdfs.entrySet().stream().map(entry -> new JSONObject()
                            .put("filename", "Ticket_" + entry.getKey() + ".pdf")
                            .put("content", Base64.getEncoder().encodeToString(entry.getValue())))
                            .collect(Collectors.toList()));

            // Construir el JSON para Resend API
            JSONObject payload = new JSONObject()
                    .put("from", fromEmail)
                    .put("to", request.getEmail())
                    .put("subject", subject)
                    .put("html", buildApprovalEmailContentHtml(request, tickets))
                    .put("attachments", attachments);

            // Construir la request HTTP
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            // Enviar
            HttpResponse<String> httpResp = HttpClient.newHttpClient()
                    .send(httpReq, HttpResponse.BodyHandlers.ofString());

            // Log del resultado
            if (httpResp.statusCode() == 200 || httpResp.statusCode() == 201) {
                JSONObject jsonResp = new JSONObject(httpResp.body());
                System.out.println("✅ Email con tickets PDF enviado. ID: " + jsonResp.getString("id"));
                System.out.println("PDFs generados: " + ticketPdfs.size());
            } else {
                System.err.println("❌ Error al enviar email: " + httpResp.statusCode() + " -> " + httpResp.body());
                // Fallback sin adjuntos
                sendRequestApproval(request);
            }

        } catch (Exception e) {
            System.err.println("❌ Error enviando email con tickets PDF: " + e.getMessage());
            // Fallback: enviar email normal sin attachments
            sendRequestApproval(request);
        }
    }

    /**
     * Versión alternativa que simula el envío con PDFs cuando no hay API real
     */
    public void sendRequestApprovalWithTicketsSimulation(TicketRequest request, List<Ticket> tickets) {
        if (resend == null || resendApiKey.isEmpty()) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud aprobada con tickets PDF:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("Evento: " + request.getEvent().getName());
            System.out.println("Cantidad de tickets: " + tickets.size());

            // Generar PDFs individuales
            Map<String, byte[]> ticketPdfs = ticketService.generateIndividualTicketPDFs(tickets);

            System.out.println("PDFs generados como attachments:");
            ticketPdfs.keySet().forEach(ticketCode -> {
                System.out.println("- Ticket_" + ticketCode + ".pdf");
            });

            return;
        }

        // Si tienes API real, usar sendRequestApprovalWithTickets
        sendRequestApprovalWithTickets(request, tickets);
    }

    private String buildApprovalEmailContentHtml(TicketRequest request, List<Ticket> tickets) {
        StringBuilder content = new StringBuilder();

        content.append("<p>¡Excelente noticia <strong>").append(request.getFullName()).append("</strong>!</p>");
        content.append("<p>Tu solicitud de tickets ha sido <b>APROBADA</b> y aquí tienes tus tickets digitales:</p>");

        content.append("<h3>📋 DETALLES DEL EVENTO:</h3>");
        content.append("<ul>");
        content.append("<li><b>Evento:</b> ").append(request.getEvent().getName()).append("</li>");
        content.append("<li><b>Fecha:</b> ").append(request.getEvent().getEventDate()).append("</li>");
        content.append("<li><b>Lugar:</b> ").append(request.getEvent().getVenue()).append("</li>");
        content.append("<li><b>Cantidad de tickets:</b> ").append(request.getQuantity()).append("</li>");
        content.append("<li><b>Precio por ticket:</b> Gs. ").append(request.getEvent().getPrice()).append("</li>");
        content.append("<li><b>Total:</b> Gs. ")
                .append(request.getEvent().getPrice().multiply(java.math.BigDecimal.valueOf(request.getQuantity())))
                .append("</li>");
        content.append("</ul>");

        if (request.getOrganizerNotes() != null && !request.getOrganizerNotes().isEmpty()) {
            content.append("<h3>💬 NOTAS DEL ORGANIZADOR:</h3>");
            content.append("<p>").append(request.getOrganizerNotes()).append("</p>");
        }

        content.append("<h3>🎫 TUS TICKETS:</h3><ul>");
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            content.append("<li>Ticket ").append(i + 1).append(": <code>").append(ticket.getTicketCode())
                    .append("</code></li>");
        }
        content.append("</ul>");

        content.append("<h3>📎 ARCHIVOS ADJUNTOS:</h3>");
        content.append("<ul>");
        content.append("<li>Cada ticket viene en un PDF individual adjunto a este email</li>");
        content.append("<li>Cada PDF contiene el código QR único del ticket</li>");
        content.append("<li>Guarda bien estos archivos PDF, los necesitarás para ingresar al evento</li>");
        content.append("</ul>");

        content.append("<h3>📱 IMPORTANTE:</h3>");
        content.append("<ul>");
        content.append("<li>Presenta el código QR de cada PDF en la entrada del evento</li>");
        content.append("<li>Puedes imprimir los PDFs o mostrarlos desde tu celular</li>");
        content.append("<li>Cada ticket es único e intransferible</li>");
        content.append("<li>Si tienes problemas, contacta al organizador</li>");
        content.append("</ul>");

        content.append("<p>").append(getOrganizerContactInfo(request)).append("</p>");
        content.append("<p>¡Nos vemos en el evento!<br>Equipo QRTAC</p>");

        return content.toString();
    }

}