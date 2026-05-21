package com.earth2me.essentials;

import com.earth2me.essentials.commands.IEssentialsCommand;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandLoadingTest {
    private static final String COMMAND_CLASS_PREFIX = "com.earth2me.essentials.commands.Command";

    @Test
    public void testPluginCommandsHaveLoadableCommandClasses() throws Exception {
        final CommandMetadata commandMetadata = getPluginCommandMetadata();
        final List<String> commandNames = new ArrayList<>(commandMetadata.commands.keySet());
        final List<String> unloadableCommands = new ArrayList<>();

        for (final String commandName : commandNames) {
            final String className = COMMAND_CLASS_PREFIX + commandName;
            try {
                final Class<?> commandClass = Class.forName(className, false, CommandLoadingTest.class.getClassLoader());
                if (!IEssentialsCommand.class.isAssignableFrom(commandClass)) {
                    unloadableCommands.add(commandName + " (" + className + " does not implement IEssentialsCommand)");
                    continue;
                }
                commandClass.getDeclaredConstructor();
            } catch (final ClassNotFoundException ex) {
                unloadableCommands.add(commandName + " (" + className + ")");
            } catch (final NoSuchMethodException ex) {
                unloadableCommands.add(commandName + " (" + className + " has no no-arg constructor)");
            }
        }

        assertTrue(commandNames.containsAll(Arrays.asList("pay", "paytoggle", "payconfirmtoggle")), "Payment commands must be declared in plugin.yml");
        assertEquals("pay", commandMetadata.aliases.get("epay"), "/epay must resolve to /pay");
        assertEquals("afk", commandMetadata.aliases.get("away"), "/away must resolve to /afk");
        assertEquals("afk", commandMetadata.aliases.get("eafk"), "/eafk must resolve to /afk");
        assertTrue(unloadableCommands.isEmpty(), "plugin.yml commands without loadable command classes: " + unloadableCommands);
    }

    private CommandMetadata getPluginCommandMetadata() throws Exception {
        final InputStream inputStream = CommandLoadingTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        assertFalse(inputStream == null, "plugin.yml must be available on the test classpath");

        final CommandMetadata commandMetadata = new CommandMetadata();
        boolean inCommands = false;
        String currentCommand = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if ("commands:".equals(line.trim())) {
                    inCommands = true;
                    continue;
                }

                if (!inCommands) {
                    continue;
                }

                if (!line.startsWith(" ")) {
                    break;
                }

                if (line.startsWith("  ") && !line.startsWith("    ") && line.trim().endsWith(":")) {
                    currentCommand = line.trim().substring(0, line.trim().length() - 1);
                    commandMetadata.commands.put(currentCommand, new ArrayList<>());
                    continue;
                }

                if (currentCommand != null && line.trim().startsWith("aliases:")) {
                    final List<String> aliases = parseInlineAliases(line.trim());
                    commandMetadata.commands.get(currentCommand).addAll(aliases);
                    for (final String alias : aliases) {
                        commandMetadata.aliases.put(alias, currentCommand);
                    }
                }
            }
        }
        return commandMetadata;
    }

    private List<String> parseInlineAliases(final String line) {
        final List<String> aliases = new ArrayList<>();
        final int start = line.indexOf('[');
        final int end = line.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return aliases;
        }

        for (final String alias : line.substring(start + 1, end).split(",")) {
            final String trimmed = alias.trim();
            if (!trimmed.isEmpty()) {
                aliases.add(trimmed);
            }
        }
        return aliases;
    }

    private static class CommandMetadata {
        private final Map<String, List<String>> commands = new LinkedHashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();
    }
}
