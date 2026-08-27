package com.moe.myfamilybudget.server.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur ultra-léger pour le Heartbeat / Ping de l'application MyFamilyBudget.
 * Utilisé par le Splashscreen HTML autonome (file:// ou http://) pour détecter la disponibilité du serveur.
 */
@RestController
@RequestMapping
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class HeartbeatController {

    @GetMapping(value = {"/heartbeat", "/ping"})
    public ResponseEntity<Map<String, Object>> heartbeat() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("app", "MyFamilyBudget");
        status.put("version", "1.0.0");
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }
}
