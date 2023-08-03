package dev.emortal.velocity.privatemessages.commands;

import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.model.messagehandler.PrivateMessage;
import dev.emortal.api.service.messagehandler.MessageService;
import dev.emortal.api.service.messagehandler.SendPrivateMessageResult;
import dev.emortal.api.utils.resolvers.PlayerResolver;
import dev.emortal.velocity.lang.TempLang;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class MessageSender {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageSender.class);

    private static final String MESSAGE_FORMAT = "<dark_purple>(<light_purple>You -> <username><dark_purple>) <light_purple><message>";

    private static final String YOU_BLOCKED_MESSAGE = "<red>You have blocked <username> so you cannot message them.";
    private static final String THEY_BLOCKED_MESSAGE = "<red><username> has blocked you so you cannot message them.";

    private final MessageService messageService;

    public MessageSender(@NotNull MessageService messageService) {
        this.messageService = messageService;
    }

    public void sendMessage(@NotNull Player player, @NotNull String targetUsername, @NotNull String message) {
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

        if (!target.online()) {
            TempLang.PLAYER_NOT_ONLINE.send(player, Placeholder.unparsed("username", correctedUsername));
            return;
        }

        var privateMessage = PrivateMessage.newBuilder()
                .setSenderId(player.getUniqueId().toString())
                .setSenderUsername(player.getUsername())
                .setRecipientId(targetId.toString())
                .setMessage(message)
                .build();

        SendPrivateMessageResult result;
        try {
            result = this.messageService.sendPrivateMessage(privateMessage);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("An error occurred while sending a private message: ", exception);
            player.sendMessage(Component.text("An error occurred while sending your message.", NamedTextColor.RED)); // todo
            return;
        }

        var usernamePlaceholder = Placeholder.parsed("username", correctedUsername);
        var responseMessage = switch (result) {
            case SendPrivateMessageResult.Success(PrivateMessage ignored) ->
                    MINI_MESSAGE.deserialize(MESSAGE_FORMAT, usernamePlaceholder, Placeholder.unparsed("message", message));
            case SendPrivateMessageResult.Error error -> switch (error) {
                case YOU_BLOCKED -> MINI_MESSAGE.deserialize(YOU_BLOCKED_MESSAGE, usernamePlaceholder);
                case PRIVACY_BLOCKED -> MINI_MESSAGE.deserialize(THEY_BLOCKED_MESSAGE, usernamePlaceholder);
                case PLAYER_NOT_ONLINE -> TempLang.PLAYER_NOT_ONLINE.deserialize(usernamePlaceholder);
            };
        };
        player.sendMessage(responseMessage);
    }
}
