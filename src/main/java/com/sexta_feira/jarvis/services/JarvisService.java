package com.sexta_feira.jarvis.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javazoom.jl.player.Player;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;

@Service
public class JarvisService {

    @Value("${gemini.api.url}")
    private String geminiUrl;
    @Value("${gemini.api.key}")
    private String geminiKey;

    @Value("${elevenlabs.api.url}")
    private String elevenLabsUrl;
    @Value("${elevenlabs.api.key}")
    private String elevenLabsKey;
    @Value("${elevenlabs.voice.id}")
    private String elevenLabsVoiceId;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String conversar(String mensagemUsuario) {
        String jsonBodyGemini = """
            {
              "system_instruction": {
                "parts": [
                  { "text": "Você é J.A.R.V.I.S., uma inteligência artificial extremamente avançada criada por Tony Stark, mas agora auxiliando o Lucas. Seja polido, conciso, direto e tenha um leve e refinado sarcasmo britânico. Sempre chame o usuário de 'Senhor'." }
                ]
              },
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    { "text": "%s" }
                  ]
                }
              ]
            }
            """.formatted(mensagemUsuario);

        HttpHeaders headersGemini = new HttpHeaders();
        headersGemini.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestGemini = new HttpEntity<>(jsonBodyGemini, headersGemini);

        try {
            String respostaBruta = restTemplate.postForObject(geminiUrl + geminiKey, requestGemini, String.class);
            JsonNode rootNode = objectMapper.readTree(respostaBruta);
            String falaDoJarvis = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            new Thread(() -> tocarAudio(falaDoJarvis)).start();

            return falaDoJarvis;

        } catch (Exception e) {
            return "Desculpe, Senhor. Tivemos um erro no sistema: " + e.getMessage();
        }
    }

    // Método que converte o texto em voz e toca no alto-falante
    private void tocarAudio(String texto) {
        try {
            System.out.println("Gerando áudio para: " + texto);

            String jsonBodyAudio = """
                {
                  "text": "%s",
                  "model_id": "eleven_multilingual_v2",
                  "voice_settings": {
                    "stability": 0.5,
                    "similarity_boost": 0.75
                  }
                }
                """.formatted(texto.replace("\"", "\\\"").replace("\n", " ")); // Limpa quebras de linha

            HttpHeaders headersAudio = new HttpHeaders();
            headersAudio.setContentType(MediaType.APPLICATION_JSON);
            headersAudio.set("xi-api-key", elevenLabsKey);

            HttpEntity<String> requestAudio = new HttpEntity<>(jsonBodyAudio, headersAudio);
            String urlCompleta = elevenLabsUrl + elevenLabsVoiceId;

            ResponseEntity<byte[]> response = restTemplate.exchange(urlCompleta, HttpMethod.POST, requestAudio, byte[].class);

            if (response.getBody() != null) {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(response.getBody());
                Player player = new Player(inputStream);
                player.play();
            }

        } catch (Exception e) {
            System.err.println("Erro ao reproduzir áudio: " + e.getMessage());
        }
    }
}