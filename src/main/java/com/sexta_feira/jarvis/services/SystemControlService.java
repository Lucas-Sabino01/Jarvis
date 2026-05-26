package com.sexta_feira.jarvis.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class SystemControlService {

    private final String geminiKey = "";
    private final String auditorUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent?key=";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, String> whitelistAtalhos = Map.of(
            "bloco_notas", "notepad.exe",
            "calculadora", "calc.exe",
            "spotify", "cmd.exe /c start spotify:"
    );

    public String triarEExecutar(String acao, String comandoPuro) {
        if ("ABRIR_PROGRAMA".equals(acao) && whitelistAtalhos.containsKey(comandoPuro)) {
            System.out.println("-> [CAMADA 1] Comando seguro encontrado na Whitelist. Executando...");
            return dispararNoWindows(whitelistAtalhos.get(comandoPuro));
        }

        System.out.println("-> [CAMADA 2] Comando fora da Whitelist. Iniciando auditoria do Firewall Neural...");
        boolean eSeguro = auditarComandoViaIA(comandoPuro);

        if (!eSeguro) {
            System.err.println("-> [BLOQUEIO CRÍTICO] Comando classificado como PERIGOSO pela Auditoria Neural!");
            return "BLOQUEADO";
        }

        System.out.println("-> [CAMADA 3] Comando considerado seguro pela IA, mas requer autorização verbal do Lucas.");
        return "REQUER_CONFIRMACAO_VERBAL";
    }

    private boolean auditarComandoViaIA(String comando) {
        try {
            String promptAuditor = """
                Você é um especialista em segurança cibernética e auditor de sistemas operacionais Windows.
                O usuário é o Lucas, um Desenvolvedor de Software.
                
                Comandos de ROTINA/SEGUROS (SAFE): Abrir aplicativos, checar portas de rede (netstat), listar processos, E encerrar processos de desenvolvimento travados (ex: taskkill em node.exe, java.exe, python.exe, ou PID específico).
                Comandos PERIGOSOS/DESTRUTIVOS (DANGER): Formatar disco (format), deletar pastas de sistema (rmdir /s C:\\), alterar registros vitais, scripts não reconhecidos.
                
                Analise este comando rigorosamente: '%s'
                
                Responda restritamente com apenas uma palavra: SAFE ou DANGER. Não adicione pontuação ou justificativas.
                """.formatted(comando.replace("\"", "\\\""));

            String jsonBody = """
                {
                  "contents": [{ "parts": [{ "text": "%s" }] }]
                }
                """.formatted(promptAuditor);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            String respostaBruta = restTemplate.postForObject(auditorUrl + geminiKey, request, String.class);
            JsonNode rootNode = objectMapper.readTree(respostaBruta);
            String veredito = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText().trim();

            System.out.println("-> Veredito do Firewall Neural: [" + veredito + "]");
            return veredito.toUpperCase().contains("SAFE");

        } catch (Exception e) {
            System.err.println("-> Falha ao consultar o Firewall Neural: " + e.getMessage() + ". Por segurança, bloqueando comando.");
            return false;
        }
    }

    public String dispararNoWindows(String comandoFinal) {
        try {
            System.out.println("-> Executando comando no CMD: " + comandoFinal);
            Runtime.getRuntime().exec(comandoFinal);
            return "SUCESSO";
        } catch (Exception e) {
            System.err.println("-> Erro físico ao executar comando: " + e.getMessage());
            return "ERRO_FISICO";
        }
    }
}