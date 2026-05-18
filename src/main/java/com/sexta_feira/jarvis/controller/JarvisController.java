package com.sexta_feira.jarvis.controller;

import com.sexta_feira.jarvis.services.JarvisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jarvis")
public class JarvisController {

    private final JarvisService jarvisService;

    public JarvisController(JarvisService jarvisService) {
        this.jarvisService = jarvisService;
    }

    @GetMapping("/falar")
    public String falarComJarvis(@RequestParam String mensagem) {
        return jarvisService.conversar(mensagem);
    }
}