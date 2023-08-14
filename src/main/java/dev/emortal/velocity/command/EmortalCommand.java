package dev.emortal.velocity.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.velocitypowered.api.command.CommandSource;
import dev.emortal.api.command.Command;
import dev.emortal.api.command.element.ArgumentElement;
import dev.emortal.api.command.element.LiteralElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EmortalCommand extends Command<CommandSource> {

    public EmortalCommand(@NotNull String name, @NotNull String... aliases) {
        super(name, aliases);
    }

    public EmortalCommand(@NotNull String name) {
        super(name);
    }

    protected static @NotNull LiteralElement<CommandSource> literal(@NotNull String name) {
        return new LiteralElement<>(name);
    }

    protected static <T> @NotNull ArgumentElement<CommandSource, T> argument(@NotNull String name, @NotNull ArgumentType<T> type,
                                                                             @Nullable SuggestionProvider<CommandSource> suggestionProvider) {
        return new ArgumentElement<>(name, type, suggestionProvider);
    }

    protected static <T> @NotNull ArgumentElement<CommandSource, T> argument(@NotNull String name, @NotNull ArgumentType<T> type) {
        return argument(name, type, null);
    }
}
