package com.josiqq.qrtac.controller;

import com.google.zxing.WriterException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import com.josiqq.qrtac.model.QrCode;
import com.josiqq.qrtac.service.QrCodeService;

@RestController
public class QrCodeController {

    private final QrCodeService qrCodeService;

    public QrCodeController(QrCodeService qrCodeService) {
        this.qrCodeService = qrCodeService;
    }

    // Endpoint para generar el QR
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getUniqueQrCode() throws WriterException, IOException {
        String uniqueContent = UUID.randomUUID().toString();
        // Lógica del servicio para generar y guardar el QR
        return qrCodeService.generateQrCode(uniqueContent, 200, 200);
    }

    // Endpoint para validar si el QR fue escaneado
    @GetMapping("/qr/scan/{content}")
    public ResponseEntity<String> scanQrCode(@PathVariable String content) {
        Optional<QrCode> qrCodeOpt = qrCodeService.findByContent(content);

        if (qrCodeOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        QrCode qrCode = qrCodeOpt.get();
        if (qrCode.isScanned()) {
            return ResponseEntity.badRequest().body("Este código QR ya ha sido escaneado.");
        }

        // Marcar como escaneado y guardar en la base de datos
        qrCode.setScanned(true);
        qrCode.setScannedAt(LocalDateTime.now());
        qrCodeService.save(qrCode);

        return ResponseEntity.ok("Código QR escaneado con éxito.");
    }
}
