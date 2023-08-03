package dev.emortal.velocity.relationships.commands.friend;

import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.command.CommandExecutor;
import dev.emortal.api.service.relationship.AddFriendResult;
import dev.emortal.api.service.relationship.RelationshipService;
import dev.emortal.api.utils.resolvers.PlayerResolver;
import dev.emortal.velocity.lang.TempLang;
import dev.emortal.velocity.relationships.FriendCache;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

public final class FriendAddSub implements CommandExecutor<CommandSource> {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendAddSub.class);

    public static final String FRIEND_ADDED_MESSAGE = "<light_purple>You are now friends with <color:#c98fff><username></color>";
    private static final String ALREADY_FRIENDS_MESSAGE = "<light_purple>You are already friends with <color:#c98fff><username></color>";
    private static final String SENT_REQUEST_MESSAGE = "<light_purple>Sent a friend request to <color:#c98fff><username></color>";
    private static final String PRIVACY_BLOCKED_MESSAGE = "<color:#c98fff><username>'s</color> <light_purple>privacy settings don't allow you you add them as a friend.";
    private static final String ALREADY_REQUESTED_MESSAGE = "<light_purple>You have already sent a friend request to <color:#c98fff><username></color>";
    private static final String YOU_BLOCKED_MESSAGE = "<red>You cannot add <color:#c98fff><username></color> as a friend as you have blocked them!</red>";

    private final RelationshipService relationshipService;
    private final FriendCache friendCache;

    public FriendAddSub(@NotNull RelationshipService relationshipService, @NotNull FriendCache friendCache) {
        this.relationshipService = relationshipService;
        this.friendCache = friendCache;
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSource> context) {
        Player player = (Player) context.getSource();
        String targetUsername = context.getArgument("username", String.class);

        if (player.getUsername().equalsIgnoreCase(targetUsername)) {
            player.sendMessage(Component.text("You can't add yourself as a friend.", NamedTextColor.RED));
            return;
        }

        PlayerResolver.CachedMcPlayer target;
        try {
            target = PlayerResolver.getPlayerData(targetUsername);
        } catch (StatusException exception) {
            LOGGER.error("Failed to retrieve player UUID", exception);
            player.sendMessage(Component.text("An unknown error occurred", NamedTextColor.RED));
            return;
        }

        if (target == null) {
            TempLang.PLAYER_NOT_FOUND.send(player, Placeholder.unparsed("search_username", targetUsername));
            return;
        }

        String correctedUsername = target.username();
        UUID targetId = target.uuid();

        AddFriendResult result;
        try {
            result = this.relationshipService.addFriend(player.getUniqueId(), player.getUsername(), targetId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to send friend request", exception);
            player.sendMessage(Component.text("Failed to send friend request to " + correctedUsername));
            return;
        }

        var usernamePlaceholder = Placeholder.component("username", Component.text(correctedUsername));
        var message = switch (result) {
            case AddFriendResult.RequestSent() -> MINI_MESSAGE.deserialize(SENT_REQUEST_MESSAGE, usernamePlaceholder);
            case AddFriendResult.FriendAdded(Instant friendsSince) -> {
                this.friendCache.add(player.getUniqueId(), new FriendCache.CachedFriend(targetId, friendsSince));
                yield MINI_MESSAGE.deserialize(FRIEND_ADDED_MESSAGE, usernamePlaceholder);
            }
            case AddFriendResult.Error error -> switch (error) {
                case ALREADY_FRIENDS -> MINI_MESSAGE.deserialize(ALREADY_FRIENDS_MESSAGE, usernamePlaceholder);
                case PRIVACY_BLOCKED -> MINI_MESSAGE.deserialize(PRIVACY_BLOCKED_MESSAGE, usernamePlaceholder);
                case ALREADY_REQUESTED -> MINI_MESSAGE.deserialize(ALREADY_REQUESTED_MESSAGE, usernamePlaceholder);
                case YOU_BLOCKED -> MINI_MESSAGE.deserialize(YOU_BLOCKED_MESSAGE, usernamePlaceholder);
            };
        };
        player.sendMessage(message);
    }
}
