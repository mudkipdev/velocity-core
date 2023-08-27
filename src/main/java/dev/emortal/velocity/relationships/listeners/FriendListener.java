package dev.emortal.velocity.relationships.listeners;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.emortal.api.message.relationship.FriendAddedMessage;
import dev.emortal.api.message.relationship.FriendRemovedMessage;
import dev.emortal.api.message.relationship.FriendRequestReceivedMessage;
import dev.emortal.api.model.relationship.FriendRequest;
import dev.emortal.velocity.lang.ChatMessages;
import dev.emortal.velocity.messaging.MessagingModule;
import dev.emortal.velocity.relationships.FriendCache;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class FriendListener {

    private final ProxyServer proxy;
    private final FriendCache friendCache;

    public FriendListener(@NotNull ProxyServer proxy, @NotNull MessagingModule messaging, @NotNull FriendCache friendCache) {
        this.proxy = proxy;
        this.friendCache = friendCache;

        messaging.addListener(FriendRequestReceivedMessage.class, message -> this.onFriendRequestReceived(message.getRequest()));
        messaging.addListener(FriendAddedMessage.class, this::onFriendAdded);
        messaging.addListener(FriendRemovedMessage.class, this::onFriendRemoved);
    }

    private void onFriendRequestReceived(@NotNull FriendRequest request) {
        Player player = this.proxy.getPlayer(UUID.fromString(request.getTargetId())).orElse(null);
        if (player == null) return;

        ChatMessages.RECEIVED_FRIEND_REQUEST.send(player, Component.text(request.getSenderUsername()));
    }

    private void onFriendAdded(@NotNull FriendAddedMessage message) {
        UUID recipientId = UUID.fromString(message.getRecipientId());

        Player player = this.proxy.getPlayer(recipientId).orElse(null);
        if (player == null) return;

        ChatMessages.FRIEND_ADDED.send(player, Component.text(message.getSenderUsername()));

        UUID senderId = UUID.fromString(message.getSenderId());
        this.friendCache.add(recipientId, new FriendCache.CachedFriend(senderId, Instant.now()));
    }

    private void onFriendRemoved(@NotNull FriendRemovedMessage message) {
        UUID senderId = UUID.fromString(message.getSenderId());
        UUID recipientId = UUID.fromString(message.getRecipientId());

        this.friendCache.remove(recipientId, senderId);
        this.friendCache.remove(senderId, recipientId);
    }
}
