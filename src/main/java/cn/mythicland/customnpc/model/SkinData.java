package cn.mythicland.customnpc.model;

import java.util.Objects;

/**
 * Cached signed Mojang texture data.
 *
 * @param playerName original requested player name
 * @param value      base64 texture value
 * @param signature  Mojang texture signature
 */
public record SkinData(String playerName, String value, String signature) {

    public SkinData {
        playerName = requireText(playerName, "playerName");
        value = requireText(value, "value");
        signature = requireText(signature, "signature");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " cannot be blank");
        return normalized;
    }
}
