package dev.emortal.velocity.privatemessages.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.grpc.mcplayer.McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod;
import dev.emortal.velocity.command.CommandConditions;
import dev.emortal.velocity.command.EmortalCommand;
import dev.emortal.velocity.player.UsernameSuggestions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

public final class MessageCommand extends EmortalCommand {

    private final MessageSender messageSender;

    public MessageCommand(@NotNull MessageSender messageSender, @NotNull UsernameSuggestions usernameSuggestions) {
        super("message", "msg");
        this.messageSender = messageSender;

        super.setCondition(CommandConditions.playerOnly());
        super.setDefaultExecutor(this::sendUsage);

        var receiverArgument = argument("receiver", StringArgumentType.word(), usernameSuggestions.command(FilterMethod.ONLINE));
        super.addSyntax(this::sendUsage, receiverArgument);

        var messageArgument = argument("message", StringArgumentType.greedyString(), null);
        super.addSyntax(this::execute, receiverArgument, messageArgument);
    }

    private void sendUsage(@NotNull CommandContext<CommandSource> context) {
        context.getSource().sendMessage(Component.text("Usage: /msg <player> <message>", NamedTextColor.RED));
    }

    private void execute(@NotNull CommandContext<CommandSource> context) {
        String targetUsername = context.getArgument("receiver", String.class);
        String message = context.getArgument("message", String.class);
        Player player = (Player) context.getSource();

        if (player.getUsername().equalsIgnoreCase(targetUsername)) {
            player.sendMessage(Component.text("You cannot send a message to yourself.", NamedTextColor.RED));
            return;
        }

        this.messageSender.sendMessage(player, targetUsername, message);
    }
}
