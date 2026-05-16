package com.mostlyvanilla.verify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {

    private final String baseUrl;
    private final String secret;
    private final HttpClient http;

    public ApiClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.secret  = secret;
        this.http    = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    /** Ask the bot to generate a code + single-use Discord invite for this player. */
    public CodeResult requestCode(String uuid, String name) {
        String body = "{\"minecraft_uuid\":\"" + uuid + "\",\"minecraft_name\":\"" + escape(name) + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/register-code"))
                .header("Content-Type", "application/json")
                .header("X-Api-Secret", secret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                String code      = extractString(res.body(), "code");
                String inviteUrl = extractString(res.body(), "invite_url");
                return new CodeResult(true, code, inviteUrl, null);
            }
            return new CodeResult(false, null, null, "HTTP " + res.statusCode());
        } catch (Exception e) {
            return new CodeResult(false, null, null, e.getMessage());
        }
    }

    /** Check whether a Minecraft UUID is already linked. */
    public boolean isVerified(String uuid) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/verified/" + uuid))
                .header("X-Api-Secret", secret)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 && res.body().contains("\"verified\":true");
        } catch (Exception e) {
            return false;
        }
    }

    // Minimal JSON string extraction — avoids pulling in a JSON library
    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record CodeResult(boolean success, String code, String inviteUrl, String error) {}
}
