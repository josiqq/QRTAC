package com.josiqq.qrtac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.josiqq.qrtac.model.QrCode;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, String> {
}
