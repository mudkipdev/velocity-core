package cc.towerdefence.velocity.friends.commands;

import cc.towerdefence.api.model.common.PlayerProto;
import cc.towerdefence.api.service.McPlayerGrpc;
import cc.towerdefence.api.service.McPlayerProto;
import cc.towerdefence.api.service.PlayerTrackerGrpc;
import cc.towerdefence.api.service.PlayerTrackerProto;
import cc.towerdefence.api.utils.GrpcTimestampConverter;
import cc.towerdefence.api.utils.utils.FunctionalFutureCallback;
import cc.towerdefence.velocity.friends.FriendCache;
import cc.towerdefence.velocity.utils.DurationFormatter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class FriendListSub {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendListSub.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Component NO_FRIENDS_MESSAGE = Component.text("You have no fiends. Use /friend add <name> to add someone");
    private static final String MESSAGE_TITLE = "<light_purple>----- Friends (Page <page>/<max_page>) -----</light_purple>";
    private static final String ONLINE_LINE = "<click:suggest_command:'/message <username> '><green><username> - <server></green></click>";
    private static final String OFFLINE_LINE = "<red><username> - Seen <last_seen></red>";
    private static final Component MESSAGE_FOOTER = Component.text("----------------------------", NamedTextColor.LIGHT_PURPLE);

    private final McPlayerGrpc.McPlayerFutureStub mcPlayerService;
    private final PlayerTrackerGrpc.PlayerTrackerFutureStub playerTrackerService;
    private final FriendCache friendCache;

    public FriendListSub(McPlayerGrpc.McPlayerFutureStub mcPlayerService,
                         PlayerTrackerGrpc.PlayerTrackerFutureStub playerTrackerService, FriendCache friendCache) {
        this.mcPlayerService = mcPlayerService;
        this.playerTrackerService = playerTrackerService;
        this.friendCache = friendCache;
    }

    public int execute(CommandContext<CommandSource> context) {
        Player player = (Player) context.getSource();

        List<FriendCache.CachedFriend> friends = this.friendCache.get(player.getUniqueId());
        int maxPage = (int) Math.ceil(friends.size() / 10.0);

        if (maxPage == 0) {
            player.sendMessage(NO_FRIENDS_MESSAGE);
            return 1;
        }

        int page = context.getArguments().containsKey("page") ? Math.min(context.getArgument("page", Integer.class), maxPage) : 1;

        this.retrieveStatuses(friends, friendStatuses -> {
            Collections.sort(friendStatuses);
            List<FriendStatus> pageFriends = friendStatuses.stream()
                    .skip((page - 1) * 10L)
                    .limit(10).toList();

            player.sendMessage(this.createMessage(pageFriends, page, maxPage));
        });

        return 1;
    }

    private Component createMessage(List<FriendStatus> statuses, int page, int maxPage) {
        TextComponent.Builder message = Component.text()
                .append(
                        MINI_MESSAGE.deserialize(MESSAGE_TITLE,
                                Placeholder.component("page", Component.text(page)),
                                Placeholder.component("max_page", Component.text(maxPage)))
                ).append(Component.newline());

        for (FriendStatus status : statuses) {
            if (status.isOnline()) {
                message.append(
                        MINI_MESSAGE.deserialize(ONLINE_LINE,
                                Placeholder.parsed("username", status.getUsername()),
                                Placeholder.parsed("server", status.getServerId()))
                );
            } else {
                message.append(
                        MINI_MESSAGE.deserialize(OFFLINE_LINE,
                                Placeholder.parsed("username", status.getUsername()),
                                Placeholder.parsed("last_seen", DurationFormatter.formatShortFromInstant(status.getLastSeen())))
                );
            }
            message.append(Component.newline());
        }
        message.append(MESSAGE_FOOTER);
        return message.build();
    }

    private void retrieveStatuses(List<FriendCache.CachedFriend> friends, Consumer<List<FriendStatus>> callback) {
        Map<UUID, FriendStatus> statuses = new ConcurrentHashMap<>();
        for (FriendCache.CachedFriend friend : friends) statuses.put(friend.playerId(), new FriendStatus(friend.playerId(), friend.friendsSince()));
        ListenableFuture<McPlayerProto.PlayersResponse> playersResponseFuture = this.mcPlayerService.getPlayers(PlayerProto.PlayersRequest.newBuilder()
                .addAllPlayerIds(friends.stream().map(FriendCache.CachedFriend::playerId).map(UUID::toString).toList())
                .build());

        Futures.addCallback(playersResponseFuture, FunctionalFutureCallback.create(
                response -> {
                    for (McPlayerProto.PlayerResponse player : response.getPlayersList()) {
                        FriendStatus status = statuses.get(UUID.fromString(player.getId()));
                        status.setUsername(player.getCurrentUsername());
                        status.setLastSeen(GrpcTimestampConverter.reverse(player.getLastOnline()));
                        status.setOnline(player.getCurrentlyOnline());
                    }

                    ListenableFuture<PlayerTrackerProto.GetPlayerServersResponse> playerServersResponseFuture =
                            this.playerTrackerService.getPlayerServers(PlayerProto.PlayersRequest.newBuilder()
                                    .addAllPlayerIds(statuses.values().stream().filter(FriendStatus::isOnline).map(FriendStatus::getUuid).map(UUID::toString).toList())
                                    .build());

                    Futures.addCallback(playerServersResponseFuture,
                            FunctionalFutureCallback.create(
                                    playerServersResponse -> {
                                        for (Map.Entry<String, PlayerTrackerProto.OnlineServer> onlineServerEntry : playerServersResponse.getPlayerServersMap().entrySet()) {
                                            UUID playerId = UUID.fromString(onlineServerEntry.getKey());
                                            FriendStatus status = statuses.get(playerId);

                                            status.setServerId(onlineServerEntry.getValue().getServerId());
                                        }

                                        callback.accept(new ArrayList<>(statuses.values()));
                                    },
                                    error -> {
                                        LOGGER.error("Failed to retrieve player servers: ", error);
                                        callback.accept(new ArrayList<>(statuses.values()));
                                    }
                            ), ForkJoinPool.commonPool());

                },
                error -> {
                    LOGGER.error("Failed to retrieve player statuses: ", error);
                    callback.accept(new ArrayList<>(statuses.values()));
                }
        ), ForkJoinPool.commonPool());
    }

    private static class FriendStatus implements Comparable<FriendStatus> {
        private final @NotNull UUID uuid;
        private final @NotNull Instant friendsSince;
        private String username;
        boolean online;
        private String serverId;
        private Instant lastSeen;

        private FriendStatus(@NotNull UUID uuid, @NotNull Instant friendsSince) {
            this.uuid = uuid;
            this.friendsSince = friendsSince;
        }

        public int compareTo(@NotNull FriendListSub.FriendStatus o) {
            if (this.online && !o.online) return -1;
            if (!this.online && o.online) return 1;
            if (!this.online) return o.lastSeen.compareTo(this.lastSeen); // both offline
            return this.username.compareTo(o.username);
        }

        public @NotNull UUID getUuid() {
            return uuid;
        }

        public @NotNull Instant getFriendsSince() {
            return friendsSince;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public boolean isOnline() {
            return online;
        }

        public void setOnline(boolean online) {
            this.online = online;
        }

        public String getServerId() {
            return serverId;
        }

        public void setServerId(String serverId) {
            this.serverId = serverId;
        }

        public Instant getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(Instant lastSeen) {
            this.lastSeen = lastSeen;
        }

        @Override
        public String toString() {
            return "FriendStatus{" +
                    "uuid=" + uuid +
                    ", username='" + username + '\'' +
                    ", online=" + online +
                    ", serverId='" + serverId + '\'' +
                    ", lastSeen=" + lastSeen +
                    '}';
        }
    }
}
