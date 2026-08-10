package cn.mythicland.customnpc.model;

import java.util.Objects;

/**
 * Immutable command binding stored with one NPC.
 *
 * @param mode    execution sender mode
 * @param command command text without a leading slash
 */
public record BoundCommand(CommandExecutionMode mode, String command) {

    public BoundCommand {
        Objects.requireNonNull(mode, "mode");
        command = Objects.requireNonNull(command, "command").trim();
        if (command.isBlank()) throw new IllegalArgumentException("command cannot be blank");
        if (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isBlank()) throw new IllegalArgumentException("command cannot be blank");
    }
}
