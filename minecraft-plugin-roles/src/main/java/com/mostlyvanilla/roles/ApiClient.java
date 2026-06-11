package com.mostlyvanilla.roles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

public class ApiClient {

    private final String baseUrl;
    private final String secret;
    private final HttpClient http;
    private Logger logger;

    public ApiClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.secret  = secret;
        this.http    = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public void setLogger(Logger logger) { this.logger = logger; }

    private void warn(String msg) {
        if (logger != null) logger.warning("[DiscordAPI] " + msg);
        else System.err.println("[DiscordAPI] " + msg);
    }

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

    /** Tell the bot an in-game role was assigned or removed so it can sync the Discord role. */
    public void notifyRoleChange(String mcUuid, String gameRole, boolean assign) {
        String body = "{\"mc_uuid\":\"" + mcUuid + "\",\"game_role\":\"" + escape(gameRole) + "\",\"assign\":" + assign + "}";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/mc-role-change"))
                .header("Content-Type", "application/json")
                .header("X-Api-Secret", secret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }

    /** Poll for Discord→MC role changes queued by the bot. Returns raw JSON or null on error. */
    public String pollPendingRoles() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/pending-game-roles"))
                .header("X-Api-Secret", secret)
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) return res.body();
        } catch (Exception ignored) {}
        return null;
    }

    public boolean setRoleLink(String gameRole, String discordRoleId) {
        String body = "{\"game_role\":\"" + escape(gameRole) + "\",\"discord_role_id\":\"" + escape(discordRoleId) + "\"}";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/role-links"))
                .header("Content-Type", "application/json")
                .header("X-Api-Secret", secret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                warn("setRoleLink HTTP " + res.statusCode() + ": " + res.body());
            }
            return res.statusCode() == 200;
        } catch (Exception e) {
            warn("setRoleLink exception: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteRoleLink(String gameRole) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/role-links/" + escape(gameRole)))
                .header("X-Api-Secret", secret)
                .DELETE()
                .timeout(Duration.ofSeconds(10))
                .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                warn("deleteRoleLink HTTP " + res.statusCode() + ": " + res.body());
            }
            return res.statusCode() == 200;
        } catch (Exception e) {
            warn("deleteRoleLink exception: " + e.getMessage());
            return false;
        }
    }

    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record CodeResult(boolean success, String code, String inviteUrl, String error) {}
}
