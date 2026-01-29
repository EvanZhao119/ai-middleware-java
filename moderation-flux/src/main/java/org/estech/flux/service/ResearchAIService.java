package org.estech.flux.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.estech.flux.model.ResearchEvidence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ResearchAIService {

    @Value("${ai.api.key}")
    private String apiKey;
    @Value("${ai.templates.autonomous-research-miner}")
    private String promptTemplate;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResearchAIService() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .build();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 1. Ingestion: Get URL (Oracle Cloud) to analyze PDF
    public Mono<String> fetchAndParsePdf(String fileUrl) {
        //暂时不调用chatgpt
        return Mono.just("");
//        return webClient.get().uri(fileUrl)
//                .retrieve()
//                .bodyToMono(byte[].class)
//                .map(bytes -> {
//                    try (PDDocument doc = PDDocument.load(bytes)) {
//                        PDFTextStripper stripper = new PDFTextStripper();
//                        stripper.setEndPage(5);
//                        return stripper.getText(doc);
//                    } catch (IOException e) {
//                        log.error("PDF Parsing error", e);
//                        throw new RuntimeException("Failed to extract text from PDF");
//                    }
//                });
    }

    // 2. Inference: invoke LLM
    public Mono<ResearchEvidence> extractIntelligence(String rawText) {
        //暂时不调用chatgpt，返回默认值
        return Mono.just(getFallbackData());
//        String finalPrompt = promptTemplate.replace("{{CONTENT}}", rawText);
//
//        Map<String, Object> body = Map.of(
//                "model", "gpt-4o",
//                "messages", List.of(Map.of("role", "user", "content", finalPrompt)),
//                "response_format", Map.of("type", "json_object")
//        );
//
//        return webClient.post()
//                .uri("https://api.openai.com/v1/chat/completions")
//                .header("Authorization", "Bearer " + apiKey)
//                .bodyValue(body)
//                .retrieve()
//                .bodyToMono(JsonNode.class)
//                .map(node -> {
//                    String jsonStr = node.path("choices").get(0).path("message").path("content").asText();
//                    try {
//                        return mapper.readValue(jsonStr, ResearchEvidence.class);
//                    } catch (Exception e) {
//                        log.error("JSON mapping error", e);
//                        return getFallbackData();
//                    }
//                }).onErrorResume(e -> {
//                    log.warn("AI API request failed (Error: {}). Activating fallback mode with high-fidelity local research data for demonstration.", e.getMessage());
//                    return Mono.just(getFallbackData());
//                });
    }

    private ResearchEvidence getFallbackData() {
        ResearchEvidence mock = new ResearchEvidence();
        mock.setPaperTitle("A Review of Sensor Technologies for Perception in Automated Driving");
        mock.setSensorModality("Exteroceptive sensors including Cameras (Mono, Stereo, NIR, FIR, ToF), FMCW Radar, and LiDAR (Mechanical and Solid State)");
        mock.setEnvironmentContext("Automated driving in complex, dynamic environments under varying lighting and weather conditions (day, night, rain, fog, snow, dust).");
        mock.setKeyMetrics("LiDAR accuracy averages a few millimeters error; Radar horizontal angular resolution of 2 to 5 degrees; Camera dynamic range up to 120-170 dB.");
        mock.setEvidenceSummary("The review demonstrates that while individual sensors have specific failure modes—such as cameras struggling with high dynamic range scenes and LiDAR being affected by atmospheric scattering in fog—sensor fusion (e.g., Radar/LiDAR or Radar/Vision) effectively mitigates these gaps by combining long-range robustness with high-resolution spatial mapping.");
        mock.setSourceQuote("Selection and arrangement of sensors represent a key factor in the design of the system.");
        return mock;
    }

    // 3. Persistence
    public void saveToHistory(String url, ResearchEvidence evidence) {
        String sql = "INSERT INTO FLUX_RESEARCH_HISTORY (PAPER_URL, PAPER_TITLE, SENSOR_MODALITY, ENVIRONMENT_CONTEXT, KEY_METRICS, EVIDENCE_SUMMARY, SOURCE_QUOTE) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                url,
                evidence.getPaperTitle(),
                evidence.getSensorModality(),
                evidence.getEnvironmentContext(),
                evidence.getKeyMetrics(),
                evidence.getEvidenceSummary(),
                evidence.getSourceQuote()
        );
    }

    public List<Map<String, Object>> getHistory() {
        return jdbcTemplate.queryForList("SELECT * FROM FLUX_RESEARCH_HISTORY ORDER BY created_at DESC");
    }
}
