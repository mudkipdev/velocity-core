package dev.emortal.velocity.relationships.commands.friend;

import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.command.CommandExecutor;
import dev.emortal.api.grpc.relationship.RelationshipProto;
import dev.emortal.api.service.relationship.RelationshipService;
import dev.emortal.velocity.lang.ChatMessages;
import dev.emortal.velocity.player.resolver.CachedMcPlayer;
import dev.emortal.velocity.player.resolver.PlayerResolver;
import dev.emortal.velocity.relationships.FriendCache;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class FriendRemoveSub implements CommandExecutor<CommandSource> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendRemoveSub.class);

    private final McPlayerService mcPlayerService;
    private final RelationshipService relationshipService;
    private final FriendCache friendCache;
    private final PlayerResolver playerResolver;

    public FriendRemoveSub(@NotNull RelationshipService relationshipService, @NotNull FriendCache friendCache,
                           @NotNull PlayerResolver playerResolver) {
        this.relationshipService = relationshipService;
        this.friendCache = friendCache;
        this.playerResolver = playerResolver;
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSource> context) {
        Player player = (Player) context.getSource();
        String targetUsername = context.getArgument("username", String.class);

        CachedMcPlayer target;
        try {
            target = this.playerResolver.getPlayer(targetUsername);
        } catch (StatusException exception) {
            LOGGER.error("Failed to get player data for '{}'", targetUsername, exception);
            ChatMessages.GENERIC_ERROR.send(player);
            return;
        }

        if (target == null) {
            ChatMessages.PLAYER_NOT_FOUND.send(player);
            return;
        }

        UUID targetId = target.uuid();
        String correctedUsername = target.username(); // this will have correct capitalisation

        RelationshipProto.RemoveFriendResponse.RemoveFriendResult result;
        try {
            result = this.relationshipService.removeFriend(player.getUniqueId(), player.getUsername(), targetId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to remove friend '{}' from '{}'", correctedUsername, player.getUsername(), exception);
            ChatMessages.GENERIC_ERROR.send(player);
            return;
        }

        switch (result) {
            case REMOVED -> {
                this.friendCache.remove(player.getUniqueId(), targetId);
                ChatMessages.FRIEND_REMOVED.send(player, Component.text(correctedUsername));
            }
            case NOT_FRIENDS -> ChatMessages.ERROR_NOT_FRIENDS.send(player, Component.text(correctedUsername));
        }
    }
}
