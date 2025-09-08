package com.josiqq.qrtac.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.model.Ticket;
import com.josiqq.qrtac.model.TicketRequest;
import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.repository.TicketRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.Image;

@Service
@Transactional
public class TicketService {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private EventService eventService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.jwt.secret:mySecretKey123456789012345678901234567890}")
    private String jwtSecret;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public Ticket purchaseTicket(Long eventId, User client) {
        // Verificar que el evento existe y está disponible
        Optional<Event> eventOpt = eventService.findById(eventId);
        if (eventOpt.isEmpty()) {
            throw new RuntimeException("Evento no encontrado");
        }

        Event event = eventOpt.get();

        // Validaciones de negocio
        if (event.getStatus() != Event.EventStatus.ACTIVE) {
            throw new RuntimeException("El evento no está disponible");
        }

        if (event.getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El evento ya ha pasado");
        }

        if (!event.hasAvailableTickets()) {
            throw new RuntimeException("No hay tickets disponibles");
        }

        // Reservar el ticket en el evento
        if (!eventService.reserveTicket(eventId)) {
            throw new RuntimeException("No se pudo reservar el ticket");
        }

        try {
            // Crear el ticket
            Ticket ticket = new Ticket();
            ticket.setTicketCode(UUID.randomUUID().toString());
            ticket.setEvent(event);
            ticket.setClient(client);
            ticket.setPrice(event.getPrice());
            ticket.setStatus(Ticket.TicketStatus.VALID);
            ticket.setPurchaseDate(LocalDateTime.now());

            // Generar token seguro para el QR
            String qrToken = generateSecureToken(ticket, event);
            ticket.setQrToken(qrToken);

            return ticketRepository.save(ticket);

        } catch (Exception e) {
            // Si algo falla, liberar el ticket reservado
            eventService.releaseTicket(eventId);
            throw new RuntimeException("Error al crear el ticket: " + e.getMessage());
        }
    }

    private String generateSecureToken(Ticket ticket, Event event) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("ticketId", ticket.getTicketCode());
        claims.put("eventId", event.getId());
        claims.put("clientId", ticket.getClient().getId());
        claims.put("purchaseDate", ticket.getPurchaseDate().toString());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(ticket.getTicketCode())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000 * 365)) // 1 año
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public byte[] generateQrCode(String qrToken, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);

        // El contenido del QR será una URL que apunte al endpoint de validación
        String qrContent = baseUrl + "/validate/" + qrToken;

        BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, width, height, hints);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

        return pngOutputStream.toByteArray();
    }

    /**
     * Genera tickets desde una solicitud aprobada
     */
    public List<Ticket> generateTicketsFromRequest(TicketRequest request) {
        if (request.getStatus() != TicketRequest.RequestStatus.APPROVED) {
            throw new RuntimeException("Solo se pueden generar tickets de solicitudes aprobadas");
        }

        Event event = request.getEvent();
        List<Ticket> tickets = new ArrayList<>();

        for (int i = 0; i < request.getQuantity(); i++) {
            // Crear cliente temporal si no existe
            User client = getOrCreateClientFromRequest(request);

            Ticket ticket = new Ticket();
            ticket.setTicketCode(UUID.randomUUID().toString());
            ticket.setEvent(event);
            ticket.setClient(client);
            ticket.setPrice(event.getPrice());
            ticket.setStatus(Ticket.TicketStatus.VALID);
            ticket.setPurchaseDate(LocalDateTime.now());

            // Generar token seguro para el QR
            String qrToken = generateSecureToken(ticket, event);
            ticket.setQrToken(qrToken);

            tickets.add(ticketRepository.save(ticket));
        }

        return tickets;
    }

    /**
     * Crea o busca un cliente basado en los datos de la solicitud
     */
    private User getOrCreateClientFromRequest(TicketRequest request) {
        // Buscar si ya existe un cliente con ese email
        Optional<User> existingClient = userService.findByEmail(request.getEmail());
        if (existingClient.isPresent()) {
            return existingClient.get();
        }

        // Crear cliente temporal
        User client = new User();
        client.setUsername(request.getEmail());
        client.setEmail(request.getEmail());
        client.setFullName(request.getFullName());
        client.setPhone(request.getPhone());
        client.setRole(User.Role.CLIENT);
        client.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        client.setEnabled(true);

        return userService.registerUser(client);
    }

    /**
     * Genera PDFs individuales para cada ticket
     */
    public Map<String, byte[]> generateIndividualTicketPDFs(List<Ticket> tickets) {
        Map<String, byte[]> ticketPdfs = new HashMap<>();

        for (Ticket ticket : tickets) {
            try {
                byte[] pdfBytes = generateSingleTicketPDF(ticket);
                ticketPdfs.put(ticket.getTicketCode(), pdfBytes);
            } catch (Exception e) {
                System.err.println("Error generando PDF para ticket " + ticket.getTicketCode() + ": " + e.getMessage());
                // Continúa con los demás tickets aunque uno falle
            }
        }

        return ticketPdfs;
    }

    /**
     * Genera un PDF individual para un ticket específico
     */
    public byte[] generateSingleTicketPDF(Ticket ticket) throws DocumentException, IOException, WriterException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        document.open();

        // Configurar fuentes
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BaseColor.BLACK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.BLACK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.GRAY);

        // Título principal
        Paragraph title = new Paragraph("TICKET DE EVENTO", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20f);
        document.add(title);

        // Información del evento
        document.add(new Paragraph("INFORMACIÓN DEL EVENTO", headerFont));
        document.add(new Paragraph("Evento: " + ticket.getEvent().getName(), normalFont));
        document.add(new Paragraph("Fecha: " + ticket.getEvent().getEventDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), normalFont));
        document.add(new Paragraph("Lugar: " + ticket.getEvent().getVenue(), normalFont));
        document.add(new Paragraph(" ", normalFont)); // Espacio

        // Información del ticket
        document.add(new Paragraph("INFORMACIÓN DEL TICKET", headerFont));
        document.add(new Paragraph("Código: " + ticket.getTicketCode(), normalFont));
        document.add(new Paragraph("Cliente: " + ticket.getClient().getFullName(), normalFont));
        document.add(new Paragraph("Email: " + ticket.getClient().getEmail(), normalFont));
        if (ticket.getClient().getPhone() != null) {
            document.add(new Paragraph("Teléfono: " + ticket.getClient().getPhone(), normalFont));
        }
        document.add(new Paragraph("Precio: Gs. " + ticket.getPrice(), normalFont));
        document.add(new Paragraph("Fecha de compra: " + ticket.getPurchaseDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), normalFont));
        document.add(new Paragraph(" ", normalFont)); // Espacio

        // Generar QR Code
        byte[] qrImageBytes = generateQrCode(ticket.getQrToken(), 200, 200);
        Image qrImage = Image.getInstance(qrImageBytes);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        qrImage.scaleToFit(150, 150);

        // Agregar QR al documento
        document.add(new Paragraph("CÓDIGO QR PARA VALIDACIÓN", headerFont));
        document.add(new Paragraph(" ", normalFont)); // Espacio
        document.add(qrImage);
        document.add(new Paragraph(" ", normalFont)); // Espacio

        // Instrucciones
        document.add(new Paragraph("INSTRUCCIONES IMPORTANTES", headerFont));
        document.add(new Paragraph("• Presenta este ticket (impreso o digital) en la entrada del evento", normalFont));
        document.add(new Paragraph("• El código QR será escaneado para validar tu entrada", normalFont));
        document.add(new Paragraph("• Este ticket es único e intransferible", normalFont));
        document.add(new Paragraph("• Conserva este documento hasta después del evento", normalFont));
        document.add(new Paragraph(" ", normalFont)); // Espacio

        // Información de contacto del organizador
        document.add(new Paragraph("CONTACTO DEL ORGANIZADOR", headerFont));
        document.add(new Paragraph("Organizador: " + ticket.getEvent().getOrganizer().getFullName(), normalFont));
        document.add(new Paragraph("Email: " + ticket.getEvent().getOrganizer().getEmail(), normalFont));
        if (ticket.getEvent().getOrganizer().getPhone() != null) {
            document.add(new Paragraph("Teléfono: " + ticket.getEvent().getOrganizer().getPhone(), normalFont));
        }
        document.add(new Paragraph(" ", normalFont)); // Espacio

        // Footer con información adicional
        document.add(new Paragraph("Generado el: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")), smallFont));
        document.add(new Paragraph("Sistema QRTAC - Gestión de Tickets con QR", smallFont));

        document.close();
        return baos.toByteArray();
    }

    /**
     * Genera un PDF con todos los tickets de una solicitud (método original mantenido para compatibilidad)
     */
    public byte[] generateTicketsPDF(List<Ticket> tickets) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);

            document.open();

            // Configurar fuentes
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);

            // Título principal
            Paragraph title = new Paragraph("TICKETS - EVENTO: " + tickets.get(0).getEvent().getName(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20f);
            document.add(title);

            for (int i = 0; i < tickets.size(); i++) {
                Ticket ticket = tickets.get(i);

                // Si no es el primer ticket, agregar una nueva página
                if (i > 0) {
                    document.newPage();
                }

                document.add(new Paragraph("TICKET " + (i + 1), headerFont));
                document.add(new Paragraph("Código: " + ticket.getTicketCode(), normalFont));
                document.add(new Paragraph("Cliente: " + ticket.getClient().getFullName(), normalFont));
                document.add(new Paragraph("Evento: " + ticket.getEvent().getName(), normalFont));
                document.add(new Paragraph("Fecha: " + ticket.getEvent().getEventDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), normalFont));
                document.add(new Paragraph("Lugar: " + ticket.getEvent().getVenue(), normalFont));
                document.add(new Paragraph("Precio: Gs. " + ticket.getPrice(), normalFont));
                document.add(new Paragraph(" ", normalFont));

                // Agregar QR Code
                try {
                    byte[] qrImageBytes = generateQrCode(ticket.getQrToken(), 150, 150);
                    Image qrImage = Image.getInstance(qrImageBytes);
                    qrImage.setAlignment(Element.ALIGN_CENTER);
                    document.add(qrImage);
                } catch (Exception e) {
                    document.add(new Paragraph("Error generando QR: " + ticket.getQrToken(), normalFont));
                }

                document.add(new Paragraph(" ", normalFont));
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de tickets: " + e.getMessage());
        }
    }

    /**
     * Genera imágenes QR individuales para cada ticket (método original mantenido)
     */
    public Map<String, byte[]> generateQRImagesForTickets(List<Ticket> tickets) {
        Map<String, byte[]> qrImages = new HashMap<>();

        for (Ticket ticket : tickets) {
            try {
                byte[] qrImage = generateQrCode(ticket.getQrToken(), 300, 300);
                qrImages.put(ticket.getTicketCode(), qrImage);
            } catch (Exception e) {
                System.err.println("Error generando QR para ticket " + ticket.getTicketCode() + ": " + e.getMessage());
            }
        }

        return qrImages;
    }

    public Optional<Ticket> findByTicketCode(String ticketCode) {
        return ticketRepository.findByTicketCode(ticketCode);
    }

    public Optional<Ticket> findByQrToken(String qrToken) {
        return ticketRepository.findByQrToken(qrToken);
    }

    public List<Ticket> findTicketsByClient(User client) {
        return ticketRepository.findByClientOrderByPurchaseDateDesc(client);
    }

    public List<Ticket> findTicketsByEvent(Event event) {
        return ticketRepository.findByEventOrderByPurchaseDateDesc(event);
    }

    public Ticket validateTicket(String qrToken, User validator) {
        // Buscar el ticket por el token QR
        Optional<Ticket> ticketOpt = ticketRepository.findByQrToken(qrToken);
        if (ticketOpt.isEmpty()) {
            throw new RuntimeException("Ticket no encontrado");
        }

        Ticket ticket = ticketOpt.get();

        // Verificar que el validador sea organizador del evento
        if (!ticket.getEvent().getOrganizer().getId().equals(validator.getId())) {
            throw new RuntimeException("No tienes permisos para validar este ticket");
        }

        // Validaciones de estado del ticket
        if (ticket.getStatus() == Ticket.TicketStatus.USED) {
            throw new RuntimeException("Este ticket ya ha sido utilizado el " + ticket.getUsedAt());
        }

        if (ticket.getStatus() == Ticket.TicketStatus.CANCELLED) {
            throw new RuntimeException("Este ticket ha sido cancelado");
        }

        if (ticket.getStatus() != Ticket.TicketStatus.VALID) {
            throw new RuntimeException("El ticket no es válido");
        }

        // Verificar que el evento no haya pasado (con margen de 1 hora después)
        if (ticket.getEvent().getEventDate().plusHours(1).isBefore(LocalDateTime.now())) {
            ticket.setStatus(Ticket.TicketStatus.EXPIRED);
            ticketRepository.save(ticket);
            throw new RuntimeException("El ticket ha expirado");
        }

        // Marcar el ticket como usado
        ticket.markAsUsed(validator);
        return ticketRepository.save(ticket);
    }

    public Ticket cancelTicket(Long ticketId, User user) {
        Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
        if (ticketOpt.isEmpty()) {
            throw new RuntimeException("Ticket no encontrado");
        }

        Ticket ticket = ticketOpt.get();

        // Verificar que el usuario sea el propietario del ticket o el organizador del
        // evento
        boolean isOwner = ticket.getClient().getId().equals(user.getId());
        boolean isOrganizer = ticket.getEvent().getOrganizer().getId().equals(user.getId());

        if (!isOwner && !isOrganizer) {
            throw new RuntimeException("No tienes permisos para cancelar este ticket");
        }

        // No permitir cancelar tickets ya usados
        if (ticket.getStatus() == Ticket.TicketStatus.USED) {
            throw new RuntimeException("No se puede cancelar un ticket que ya ha sido usado");
        }

        // No permitir cancelar si el evento ya pasó
        if (ticket.getEvent().getEventDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("No se puede cancelar un ticket de un evento que ya pasó");
        }

        // Cancelar el ticket
        ticket.cancel();

        // Liberar el cupo en el evento
        eventService.releaseTicket(ticket.getEvent().getId());

        return ticketRepository.save(ticket);
    }

    public Map<String, Object> getTicketValidationInfo(String qrToken) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<Ticket> ticketOpt = ticketRepository.findByQrToken(qrToken);
            if (ticketOpt.isEmpty()) {
                response.put("status", "NOT_FOUND");
                response.put("message", "Ticket no encontrado");
                return response;
            }

            Ticket ticket = ticketOpt.get();
            Event event = ticket.getEvent();

            response.put("ticketCode", ticket.getTicketCode());
            response.put("eventName", event.getName());
            response.put("eventDate", event.getEventDate());
            response.put("venue", event.getVenue());
            response.put("clientName", ticket.getClient().getFullName());
            response.put("purchaseDate", ticket.getPurchaseDate());
            response.put("price", ticket.getPrice());

            switch (ticket.getStatus()) {
                case VALID:
                    if (event.getEventDate().plusHours(1).isBefore(LocalDateTime.now())) {
                        response.put("status", "EXPIRED");
                        response.put("message", "El ticket ha expirado");
                    } else {
                        response.put("status", "VALID");
                        response.put("message", "Ticket válido - Listo para usar");
                    }
                    break;
                case USED:
                    response.put("status", "USED");
                    response.put("message", "Ticket ya utilizado");
                    response.put("usedAt", ticket.getUsedAt());
                    response.put("validatedBy", ticket.getValidatedBy().getFullName());
                    break;
                case CANCELLED:
                    response.put("status", "CANCELLED");
                    response.put("message", "Ticket cancelado");
                    response.put("cancelledAt", ticket.getCancelledAt());
                    break;
                case EXPIRED:
                    response.put("status", "EXPIRED");
                    response.put("message", "Ticket expirado");
                    break;
            }

        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Error al validar el ticket: " + e.getMessage());
        }

        return response;
    }
}