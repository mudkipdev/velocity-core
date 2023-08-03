package dev.emortal.velocity.relationships.commands.block;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.grpc.mcplayer.McPlayerProto.SearchPlayersByUsernameRequest.FilterMethod;
import dev.emortal.api.grpc.relationship.RelationshipProto;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.api.service.relationship.RelationshipService;
import dev.emortal.velocity.command.CommandConditions;
import dev.emortal.velocity.command.EmortalCommand;
import dev.emortal.velocity.player.UsernameSuggestions;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class BlockCommand extends EmortalCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockCommand.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Component USAGE = MINI_MESSAGE.deserialize("<red>Usage: /block <username>");

    private final RelationshipService relationshipService;
    private final McPlayerService mcPlayerService;

    public BlockCommand(@NotNull McPlayerService mcPlayerService, @NotNull RelationshipService relationshipService,
                        @NotNull UsernameSuggestions usernameSuggestions) {
        super("block");
        this.mcPlayerService = mcPlayerService;
        this.relationshipService = relationshipService;

        super.setCondition(CommandConditions.playerOnly());
        super.setDefaultExecutor(context -> context.getSource().sendMessage(USAGE));

        var usernameArgument = argument("username", StringArgumentType.string(), usernameSuggestions.command(FilterMethod.NONE));
        super.addSyntax(this::execute, usernameArgument);
    }

    private void execute(@NotNull CommandContext<CommandSource> context) {
        Player sender = (Player) context.getSource();
        String targetUsername = StringArgumentType.getString(context, "username");

        McPlayer target;
        try {
            target = this.mcPlayerService.getPlayerByUsername(targetUsername);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("An error occurred while trying to block the player", exception);
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>An error occurred while trying to block the player"));
            return;
        }

        if (target == null) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>Player not found"));
            return;
        }

        UUID targetId = UUID.fromString(target.getId());
        if (targetId.equals(sender.getUniqueId())) {
            sender.sendMessage(MINI_MESSAGE.deserialize("<red>You can't block yourself"));
            return;
        }

        RelationshipProto.CreateBlockResponse.CreateBlockResult result;
        try {
            result = this.relationshipService.block(sender.getUniqueId(), targetId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("An error occurred while trying to block the player", exception);
            sender.sendMessage(Component.text("An error occurred while trying to block " + targetUsername));
            return;
        }

        var message = switch (result) {
            case SUCCESS -> Component.text("You have blocked " + targetUsername, NamedTextColor.GREEN);
            case ALREADY_BLOCKED -> Component.text("You have already blocked " + targetUsername, NamedTextColor.RED);
            case FAILED_FRIENDS -> Component.text("You must unfriend " + targetUsername + " before blocking them", NamedTextColor.RED);
            case UNRECOGNIZED -> Component.text("An error occurred while trying to block " + targetUsername);
        };
        sender.sendMessage(message);
    }
}
