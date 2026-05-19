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

    // NOVO: O Córtex Matemático!
    // Vai guardar um array gigante em formato de String: "[0.012, -0.045, ...]"
    @Column(columnDefinition = "TEXT")
    private String embedding;

    private LocalDateTime dataCriacao;

    public Mensagem() {
        this.dataCriacao = LocalDateTime.now();
    }

    // Atualizamos o construtor para receber o vetor
    public Mensagem(String role, String texto, String embedding) {
        this.role = role;
        this.texto = texto;
        this.embedding = embedding;
        this.dataCriacao = LocalDateTime.now();
    }

    // --- GETTERS E SETTERS ---
    public Long getId() { return id; }
    public String getRole() { return role; }
    public String getTexto() { return texto; }
    public String getEmbedding() { return embedding; } // Novo Getter
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}