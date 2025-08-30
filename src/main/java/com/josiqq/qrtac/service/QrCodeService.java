package com.josiqq.qrtac.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.josiqq.qrtac.model.QrCode;
import com.josiqq.qrtac.repository.QrCodeRepository;

@Service
public class QrCodeService {

    private final QrCodeRepository qrCodeRepository;

    public QrCodeService(QrCodeRepository qrCodeRepository) {
        this.qrCodeRepository = qrCodeRepository;
    }

    public byte[] generateQrCode(String content, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

	Optional<QrCode> existingQr = qrCodeRepository.findById(content);
        if (existingQr.isEmpty()) {
            QrCode qrCode = new QrCode();
            qrCode.setContent(content);
            qrCode.setScanned(false);
            qrCodeRepository.save(qrCode);
        }

        return pngOutputStream.toByteArray();
    }

    // Método para buscar un QR por su contenido
    public Optional<QrCode> findByContent(String content) {
        return qrCodeRepository.findById(content);
    }

    // Método para guardar un QR (marcar como escaneado)
    public QrCode save(QrCode qrCode) {
        return qrCodeRepository.save(qrCode);
    }
}
