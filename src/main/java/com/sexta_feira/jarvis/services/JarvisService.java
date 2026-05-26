package com.sexta_feira.jarvis.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sexta_feira.jarvis.model.Mensagem;
import com.sexta_feira.jarvis.repository.MensagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class JarvisService {

    private final String geminiKey = "";
    private final String brainUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";
    private final String voiceUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-tts-preview:generateContent?key=";
    private final String embeddingUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Autowired
    private MensagemRepository mensagemRepository;

    @Autowired
    private SystemControlService systemControlService;

    private String comandoPendenteDeAutorizacao = null;

    public String conversar(String mensagemUsuario) {
        try {
            if (comandoPendenteDeAutorizacao != null) {
                String confirmacao = mensagemUsuario.toLowerCase();
                if (confirmacao.contains("autorizado") || confirmacao.contains("pode executar") || confirmacao.contains("sim")) {
                    String cmdToRun = comandoPendenteDeAutorizacao;
                    comandoPendenteDeAutorizacao = null;

                    systemControlService.dispararNoWindows(cmdToRun);

                    String resposta = "Comando executado com sucesso nos seus sistemas, Senhor.";
                    new Thread(() -> falar(resposta)).start();
                    return resposta;
                } else {
                    comandoPendenteDeAutorizacao = null;
                    String resposta = "Entendido, Senhor. Protocolo cancelado. O comando foi descartado por segurança.";
                    new Thread(() -> falar(resposta)).start();
                    return resposta;
                }
            }

            String textoResposta = pensar(mensagemUsuario);

            if (textoResposta.contains("[ABRIR_PROGRAMA:") || textoResposta.contains("[CMD_LIVRE:")) {
                String tipoTag = textoResposta.contains("[ABRIR_PROGRAMA:") ? "[ABRIR_PROGRAMA:" : "[CMD_LIVRE:";
                int inicio = textoResposta.indexOf(tipoTag) + tipoTag.length();
                int fim = textoResposta.indexOf("]", inicio);
                String comandoExtraido = textoResposta.substring(inicio, fim).trim();

                String textoLimpoParaOFala = textoResposta.substring(0, textoResposta.indexOf(tipoTag)).trim();

                String acaoTipo = tipoTag.replace("[", "").replace(":", "");
                String resultadoTriagem = systemControlService.triarEExecutar(acaoTipo, comandoExtraido);

                if ("REQUER_CONFIRMACAO_VERBAL".equals(resultadoTriagem)) {
                    this.comandoPendenteDeAutorizacao = comandoExtraido;

                    String respostaDeEspera = textoLimpoParaOFala + " No entanto, por não estar listado na minha whitelist direta, necessito da sua autorização verbal de segurança para prosseguir com a execução do comando.";
                    new Thread(() -> falar(respostaDeEspera)).start();
                    return respostaDeEspera;
                } else if ("BLOQUEADO".equals(resultadoTriagem)) {
                    String respostaErro = "Sinto muito, Senhor. Meus sistemas de auditoria neural detectaram comportamento potencialmente destrutivo no comando solicitado e bloquearam o acesso.";
                    new Thread(() -> falar(respostaErro)).start();
                    return respostaErro;
                }

                final String falaFinal = textoLimpoParaOFala;
                new Thread(() -> falar(falaFinal)).start();
                return falaFinal;
            }

            final String falaNormal = textoResposta;
            new Thread(() -> falar(falaNormal)).start();
            return falaNormal;

        } catch (Exception e) {
            e.printStackTrace();
            return "Desculpe, Senhor. Ocorreu um colapso no meu reator principal: " + e.getMessage();
        }
    }

    private String pensar(String mensagem) throws Exception {
        String vetorUsuarioStr = gerarEmbedding(mensagem);
        mensagemRepository.save(new Mensagem("user", mensagem, vetorUsuarioStr));

        double[] vetorPerguntaAtual = converterStringParaVetor(vetorUsuarioStr);

        List<Mensagem> todasAsMensagens = mensagemRepository.findAll();

        Map<Mensagem, Double> notasDeSimilaridade = new HashMap<>();

        for (Mensagem msgBanco : todasAsMensagens) {
            if (msgBanco.getEmbedding() != null && !msgBanco.getEmbedding().equals("[]")) {
                double[] vetorBanco = converterStringParaVetor(msgBanco.getEmbedding());
                double nota = calcularSimilaridadeCosseno(vetorPerguntaAtual, vetorBanco);

                if (nota > 0.65) {
                    notasDeSimilaridade.put(msgBanco, nota);
                }
            }
        }

        List<Mensagem> top3Memorias = notasDeSimilaridade.entrySet().stream()
                .sorted(Map.Entry.<Mensagem, Double>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        top3Memorias.sort(Comparator.comparing(Mensagem::getDataCriacao));

        List<String> historicoFormatado = new ArrayList<>();
        for (Mensagem msg : top3Memorias) {
            String txtLimpo = msg.getTexto().replace("\"", "\\\"").replace("\n", " ");
            historicoFormatado.add("""
                { "role": "%s", "parts": [{ "text": "%s" }] }
                """.formatted(msg.getRole(), txtLimpo));
        }
        String historicoCompletoJson = String.join(", ", historicoFormatado);

        String jsonBody = """
            {
              "system_instruction": {
                "parts": [
                  { "text": "Você é J.A.R.V.I.S., uma inteligência artificial extremamente avançada auxiliando o Lucas. Seja polido, conciso e com sarcasmo britânico. Sempre chame o usuário de 'Senhor'. REGRAS DE MEMÓRIA: Use o contexto fornecido apenas se for 100%% vital para a pergunta. Se o assunto mudar, ignore o passado. DIRETRIZ DE SISTEMA OPERACIONAL: 1. Se o usuário pedir para abrir ferramentas, substitua dinamicamente o nome do programa na tag. Exemplo: [ABRIR_PROGRAMA: spotify] ou [ABRIR_PROGRAMA: bloco_notas]. 2. Se pedir comandos complexos de CMD (derrubar processos, limpar cache, etc), gere a tag [CMD_LIVRE: comando]. Exemplo: [CMD_LIVRE: taskkill /F /IM node.exe]. NÃO misture viagens ou memórias antigas ao confirmar ações de sistema." }
                ]
              },
              "contents": [ %s ]
            }
            """.formatted(historicoCompletoJson);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

        String respostaBruta = restTemplate.postForObject(brainUrl + geminiKey, request, String.class);
        JsonNode rootNode = objectMapper.readTree(respostaBruta);
        String respostaJarvis = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

        String vetorJarvis = gerarEmbedding(respostaJarvis);
        mensagemRepository.save(new Mensagem("model", respostaJarvis, vetorJarvis));

        return respostaJarvis;
    }

    private void falar(String textoParaLer) {
        try {
            String textoLimpo = textoParaLer.replace("\"", "\\\"").replace("\n", " ");

            String jsonBody = """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [
                        { "text": "%s" }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "responseModalities": ["AUDIO"],
                    "speechConfig": {
                      "voiceConfig": {
                        "prebuiltVoiceConfig": {
                          "voiceName": "Puck"
                        }
                      }
                    }
                  }
                }
                """.formatted(textoLimpo);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            String respostaBruta = restTemplate.postForObject(voiceUrl + geminiKey, request, String.class);
            JsonNode rootNode = objectMapper.readTree(respostaBruta);
            JsonNode parts = rootNode.path("candidates").get(0).path("content").path("parts");

            for (JsonNode part : parts) {
                if (part.has("inlineData")) {
                    String base64Audio = part.path("inlineData").path("data").asText();
                    byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
                    tocarAudio(audioBytes);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro na geração de voz: " + e.getMessage());
        }
    }

    private void tocarAudio(byte[] audioData) {
        try {
            javax.sound.sampled.AudioFormat formatoGoogle = new javax.sound.sampled.AudioFormat(24000f, 16, 1, true, false);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(audioData);
            javax.sound.sampled.AudioInputStream pcmStream = new javax.sound.sampled.AudioInputStream(bais, formatoGoogle, audioData.length / 2);

            java.io.File arquivoWav = new java.io.File("voz_do_jarvis.wav");
            javax.sound.sampled.AudioSystem.write(pcmStream, javax.sound.sampled.AudioFileFormat.Type.WAVE, arquivoWav);

            javax.sound.sampled.AudioInputStream audioStreamParaTocar = javax.sound.sampled.AudioSystem.getAudioInputStream(arquivoWav);
            javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            clip.open(audioStreamParaTocar);
            clip.start();

            Thread.sleep(clip.getMicrosecondLength() / 1000);

        } catch (Exception e) {
            System.err.println("Erro na placa de som do JARVIS: " + e.getMessage());
        }
    }

    private String gerarEmbedding(String texto) {
        try {
            String txtLimpo = texto.replace("\"", "\\\"").replace("\n", " ");
            String jsonBody = """
                {
                  "model": "models/gemini-embedding-001",
                  "content": {
                    "parts": [{ "text": "%s" }]
                  }
                }
                """.formatted(txtLimpo);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            String respostaBruta = restTemplate.postForObject(embeddingUrl + geminiKey, request, String.class);
            JsonNode rootNode = objectMapper.readTree(respostaBruta);
            return rootNode.path("embedding").path("values").toString();
        } catch (Exception e) {
            System.err.println("Falha ao gerar vetor: " + e.getMessage());
            return "[]";
        }
    }

    private double[] converterStringParaVetor(String stringVetor) {
        if (stringVetor == null || stringVetor.equals("[]") || stringVetor.isEmpty()) return new double[0];
        String limpo = stringVetor.replace("[", "").replace("]", "");
        String[] partes = limpo.split(",");
        double[] vetor = new double[partes.length];
        for (int i = 0; i < partes.length; i++) {
            vetor[i] = Double.parseDouble(partes[i].trim());
        }
        return vetor;
    }

    private double calcularSimilaridadeCosseno(double[] vetorA, double[] vetorB) {
        if (vetorA.length == 0 || vetorB.length == 0 || vetorA.length != vetorB.length) return 0.0;
        double produtoEscalar = 0.0;
        double normaA = 0.0;
        double normaB = 0.0;
        for (int i = 0; i < vetorA.length; i++) {
            produtoEscalar += vetorA[i] * vetorB[i];
            normaA += Math.pow(vetorA[i], 2);
            normaB += Math.pow(vetorB[i], 2);
        }
        if (normaA == 0.0 || normaB == 0.0) return 0.0;
        return produtoEscalar / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}