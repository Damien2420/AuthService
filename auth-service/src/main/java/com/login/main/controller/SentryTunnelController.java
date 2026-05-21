package com.login.main.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.login.main.config.AppProperties;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentry Tunnel 代理控制器
 *
 * 接受前端 @sentry/react SDK 傳送的 Envelope 請求，
 * 驗證 Project ID 白名單後將原始 body 轉發至 Sentry ingest API。
 * 用途：繞過廣告封鎖器對 *.ingest.sentry.io 的封鎖。
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Tag(name = "監控", description = "Sentry Tunnel 代理轉發")
@Slf4j
public class SentryTunnelController {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    /**
     * Sentry Envelope 轉發端點
     *
     * 讀取 Sentry SDK 傳入的原始 Envelope body，解析第一行 JSON header 取得 DSN，
     * 驗證 projectId 後將完整 body 轉發至 Sentry。
     *
     * @param request 原生 HTTP 請求，直接讀取 InputStream 以保留原始 bytes
     * @return 200 轉發成功；400 格式錯誤或 projectId 不合法；502 Sentry 上游失敗
     */
    @Operation(summary = "Sentry Tunnel", description = "代理轉發 Sentry Envelope")
    @PostMapping("/tunnel")
    public ResponseEntity<Void> tunnel(HttpServletRequest request) {

        // 讀取原始 body bytes（不使用 @RequestBody，避免破壞 Envelope 格式）
        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("Sentry tunnel: 無法讀取請求 body - {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        if (body.length == 0) {
            log.warn("Sentry tunnel: 空請求 body");
            return ResponseEntity.badRequest().build();
        }

        // 提取 Envelope 第一行
        String envelopeHeader = extractFirstLine(body);
        if (envelopeHeader == null || envelopeHeader.isBlank()) {
            log.warn("Sentry tunnel: 無法提取 Envelope header");
            return ResponseEntity.badRequest().build();
        }

        // 解析 DSN
        String dsn;
        try {
            JsonNode headerNode = objectMapper.readTree(envelopeHeader);
            JsonNode dsnNode = headerNode.get("dsn");
            if (dsnNode == null || dsnNode.isNull()) {
                log.warn("Sentry tunnel: Envelope header 缺少 dsn 欄位");
                return ResponseEntity.badRequest().build();
            }
            dsn = dsnNode.asText();
        } catch (Exception e) {
            log.warn("Sentry tunnel: 解析 Envelope header 失敗 - {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // 從 DSN 取得 host 與 projectId
        // DSN 格式：https://<publicKey>@<host>/<projectId>
        String host;
        String projectId;
        try {
            URI dsnUri = URI.create(dsn);
            host = dsnUri.getHost();
            String path = dsnUri.getPath();
            if (path == null || path.length() < 2) {
                throw new IllegalArgumentException("DSN path 格式不合法: " + path);
            }
            projectId = path.substring(1); // 去掉開頭的 "/"
        } catch (Exception e) {
            log.warn("Sentry tunnel: 解析 DSN URI 失敗 - error={}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // 驗證 projectId 格式（Sentry projectId 必須為純數字）
        if (projectId.isEmpty() || !projectId.matches("\\d+")) {
            log.warn("Sentry tunnel: projectId 格式不合法，拒絕轉發 - projectId={}", projectId);
            return ResponseEntity.badRequest().build();
        }

        // 驗證 host 必須為合法的 Sentry ingest domain（防止 SSRF）
        if (host == null || !host.endsWith(".ingest.sentry.io") && !host.endsWith(".ingest.us.sentry.io")) {
            log.warn("Sentry tunnel: host 不是合法的 Sentry domain，拒絕轉發 - host={}", host);
            return ResponseEntity.badRequest().build();
        }

        // 驗證 projectId 白名單（防止被當作免費 proxy）
        if (!appProperties.getSentry().isProjectIdAllowed(projectId)) {
            log.warn("Sentry tunnel: projectId 不在白名單中，拒絕轉發 - projectId={}", projectId);
            return ResponseEntity.badRequest().build();
        }

        // 轉發至 Sentry
        String sentryUrl = "https://" + host + "/api/" + projectId + "/envelope/";
        log.debug("Sentry tunnel: 轉發至 {} - {} bytes", sentryUrl, body.length);

        // 透傳前端帶來的 X-Request-ID，讓 Sentry 端可追蹤同一請求鏈
        String requestId = request.getHeader("X-Request-ID");

        try {
            var requestSpec = restClient.post()
                    .uri(sentryUrl)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body);

            if (requestId != null) {
                requestSpec = requestSpec.header("X-Request-ID", requestId);
            }

            requestSpec.retrieve().toBodilessEntity();

            return ResponseEntity.ok().build();

        } catch (RestClientException e) {
            log.error("Sentry tunnel: 轉發失敗 - url={}, error={}", sentryUrl, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * 從原始 body bytes 提取第一行
     *
     * Sentry Envelope 規範使用 LF（\n）作為行分隔符。
     *
     * @param body Envelope 原始 bytes
     * @return 第一行的 UTF-8 字串；解碼失敗回傳 null
     */
    private String extractFirstLine(byte[] body) {
        int lineEnd = body.length;
        for (int i = 0; i < body.length; i++) {
            if (body[i] == '\n') {
                lineEnd = i;
                break;
            }
        }
        try {
            return new String(body, 0, lineEnd, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Sentry tunnel: 第一行 UTF-8 解碼失敗");
            return null;
        }
    }
}
