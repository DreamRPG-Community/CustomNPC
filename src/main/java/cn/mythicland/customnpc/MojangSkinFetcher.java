package cn.mythicland.customnpc;

import cn.mythicland.customnpc.model.SkinData;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches signed player textures from Mojang's public session APIs.
 */
public final class MojangSkinFetcher {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\""
    );
    private static final Pattern VALUE_PATTERN = Pattern.compile(
            "\"name\"\\s*:\\s*\"textures\".*?\"value\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
    );
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(
            "\"name\"\\s*:\\s*\"textures\".*?\"signature\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
    );

    private static String request(String endpoint) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(endpoint).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent", "DreamRPG-CustomNPC/1.0");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Mojang request failed with HTTP " + status);
            }
            try (InputStream stream = connection.getInputStream()) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Mojang request failed", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String encodePath(String value) {
        return value.replace("%", "%25").replace(" ", "%20");
    }

    /**
     * Fetches one player skin off the server thread.
     *
     * @param playerName player name
     * @return future texture data
     */
    public CompletableFuture<SkinData> fetch(String playerName) {
        String normalized = Objects.requireNonNull(playerName, "playerName").trim();
        return CompletableFuture.supplyAsync(() -> fetchBlocking(normalized));
    }

    private SkinData fetchBlocking(String playerName) {
        String uuidJson = request("https://api.mojang.com/users/profiles/minecraft/" + encodePath(playerName));
        Matcher uuidMatcher = UUID_PATTERN.matcher(uuidJson);
        if (!uuidMatcher.find()) throw new IllegalStateException("Mojang returned no player for " + playerName);
        String uuid = uuidMatcher.group(1);
        String profileJson = request("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
        Matcher valueMatcher = VALUE_PATTERN.matcher(profileJson);
        Matcher signatureMatcher = SIGNATURE_PATTERN.matcher(profileJson);
        if (!valueMatcher.find() || !signatureMatcher.find()) {
            throw new IllegalStateException("Mojang returned no signed textures for " + playerName);
        }
        return new SkinData(playerName, valueMatcher.group(1), signatureMatcher.group(1));
    }
}
