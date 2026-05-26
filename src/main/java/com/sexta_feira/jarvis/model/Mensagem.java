package com.sexta_feira.jarvis.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String role;

    @Column(columnDefinition = "TEXT")
    private String texto;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    private LocalDateTime dataCriacao;

    public Mensagem() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Mensagem(String role, String texto, String embedding) {
        this.role = role;
        this.texto = texto;
        this.embedding = embedding;
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getTexto() { return texto; }
    public String getEmbedding() { return embedding; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}