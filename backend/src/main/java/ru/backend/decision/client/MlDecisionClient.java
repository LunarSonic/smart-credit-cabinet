package ru.backend.decision.client;

import ru.backend.decision.config.MlProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Клиент Python-сервиса скоринга и решений. Повторы идут с тем же request_id — дубля решения не будет */

@Component
public class MlDecisionClient {
    private static final Logger log = LoggerFactory.getLogger(MlDecisionClient.class);

    private final RestClient rest;
    private final int retries;

    public MlDecisionClient(MlProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.connectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));
        this.rest = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(f).build();
        this.retries = props.retries();
    }

    public MlDecideResponse decide(MlDecideRequest request) {
        Exception last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                MlDecideResponse r = rest.post().uri("/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(MlDecideResponse.class);
                if (r == null || r.decision() == null || r.decisionId() == null) {
                    throw new IllegalStateException("Пустой или неполный ответ сервиса решений");
                }
                return r;
            } catch (Exception e) {
                last = e;
                log.warn("Сервис решений: попытка {}/{} не удалась (request_id={}): {}",
                        attempt + 1, retries + 1, request.requestId(), e.getMessage());
            }
        }
        throw new MlUnavailableException("Сервис решений недоступен: " + (last == null ? "" : last.getMessage()), last);
    }
}
