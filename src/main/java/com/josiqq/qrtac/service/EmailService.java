package com.josiqq.qrtac.service;

import com.josiqq.qrtac.model.TicketRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;
    
    @Value("${app.email.from:noreply@qrtac.com}")
    private String fromEmail;
    
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendNewRequestNotification(TicketRequest request) {
        if (mailSender == null) {
            System.out.println("SIMULACIÓN EMAIL - Nueva solicitud de ticket:");
            System.out.println("Para: " + request.getEvent().getOrganizer().getEmail());
            System.out.println("Evento: " + request.getEvent().getName());
            System.out.println("Solicitante: " + request.getFullName() + " (" + request.getEmail() + ")");
            System.out.println("Cantidad: " + request.getQuantity() + " tickets");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(request.getEvent().getOrganizer().getEmail());
        message.setSubject("Nueva solicitud de ticket - " + request.getEvent().getName());
        
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
            baseUrl
        );
        
        message.setText(content);
        mailSender.send(message);
    }

    public void sendRequestConfirmation(TicketRequest request) {
        if (mailSender == null) {
            System.out.println("SIMULACIÓN EMAIL - Confirmación de solicitud:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("Solicitud ID: " + request.getId());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(request.getEmail());
        message.setSubject("Confirmación de solicitud de ticket - " + request.getEvent().getName());
        
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
            request.getEmail()
        );
        
        message.setText(content);
        mailSender.send(message);
    }

    public void sendRequestApproval(TicketRequest request) {
        if (mailSender == null) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud aprobada:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("El organizador debe enviar los tickets por " + request.getPreferredContactMethod());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(request.getEmail());
        message.setSubject("¡Solicitud aprobada! - " + request.getEvent().getName());
        
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
            contactInfo
        );
        
        message.setText(content);
        mailSender.send(message);
    }

    public void sendRequestRejection(TicketRequest request) {
        if (mailSender == null) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud rechazada:");
            System.out.println("Para: " + request.getEmail());
            System.out.println("Motivo: " + request.getOrganizerNotes());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(request.getEmail());
        message.setSubject("Actualización de tu solicitud - " + request.getEvent().getName());
        
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
            request.getOrganizerNotes() != null ? "Motivo: " + request.getOrganizerNotes() : ""
        );
        
        message.setText(content);
        mailSender.send(message);
    }

    public void sendRequestCancellation(TicketRequest request) {
        if (mailSender == null) {
            System.out.println("SIMULACIÓN EMAIL - Solicitud cancelada por cliente:");
            System.out.println("Para organizador: " + request.getEvent().getOrganizer().getEmail());
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(request.getEvent().getOrganizer().getEmail());
        message.setSubject("Solicitud cancelada - " + request.getEvent().getName());
        
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
            request.getEmail()
        );
        
        message.setText(content);
        mailSender.send(message);
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
}