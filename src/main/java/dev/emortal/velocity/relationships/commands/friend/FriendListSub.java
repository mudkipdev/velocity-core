package dev.emortal.velocity.relationships.commands.friend;

import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.command.CommandExecutor;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeCollection;
import dev.emortal.api.liveconfigparser.configs.gamemode.GameModeConfig;
import dev.emortal.api.model.mcplayer.McPlayer;
import dev.emortal.api.service.mcplayer.McPlayerService;
import dev.emortal.api.utils.ProtoTimestampConverter;
import dev.emortal.velocity.relationships.FriendCache;
import dev.emortal.velocity.utils.DurationFormatter;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FriendListSub implements CommandExecutor<CommandSource> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendListSub.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final Component NO_FRIENDS_MESSAGE = MINI_MESSAGE.deserialize("<light_purple>You have no friends. Use /friend add <name> to add someone.");
    private static final String MESSAGE_TITLE = "<light_purple>----- Friends (Page <page>/<max_page>) -----</light_purple>";
    private static final String ONLINE_LINE = "<click:suggest_command:'/message <username> '><green><username> - <server></green></click>";
    private static final String OFFLINE_LINE = "<red><username> - Seen <last_seen></red>";
    private static final Component MESSAGE_FOOTER = Component.text("----------------------------", NamedTextColor.LIGHT_PURPLE);

    private final @NotNull McPlayerService mcPlayerService;
    private final @NotNull FriendCache friendCache;
    private final @Nullable GameModeCollection gameModeCollection;

    public FriendListSub(@NotNull McPlayerService mcPlayerService, @NotNull FriendCache friendCache, @Nullable GameModeCollection gameModeCollection) {
        this.mcPlayerService = mcPlayerService;
        this.friendCache = friendCache;
        this.gameModeCollection = gameModeCollection;

        if (gameModeCollection == null) LOGGER.warn("GameModeCollection is null. Friend statuses will not be displayed.");
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSource> context) {
        Player player = (Player) context.getSource();

        List<FriendCache.CachedFriend> friends = this.friendCache.get(player.getUniqueId());
        int maxPage = (int) Math.ceil(friends.size() / 8.0);

        if (maxPage == 0) {
            player.sendMessage(NO_FRIENDS_MESSAGE);
            return;
        }

        int page = context.getArguments().containsKey("page") ? Math.min(context.getArgument("page", Integer.class), maxPage) : 1;

        List<FriendStatus> statuses = this.retrieveStatuses(friends);
        Collections.sort(statuses);

        List<FriendStatus> pageFriends = statuses.stream()
                .skip((page - 1) * 8L)
                .limit(8)
                .toList();
        player.sendMessage(this.createMessage(pageFriends, page, maxPage));
    }

    private @NotNull Component createMessage(@NotNull List<FriendStatus> statuses, int page, int maxPage) {
        TextComponent.Builder message = Component.text()
                .append(MINI_MESSAGE.deserialize(MESSAGE_TITLE,
                        Placeholder.parsed("page", String.valueOf(page)),
                        Placeholder.parsed("max_page", String.valueOf(maxPage))))
                .append(Component.newline());

        for (FriendStatus status : statuses) {
            var usernamePlaceholder = Placeholder.parsed("username", status.username());
            String line = status.online() ? ONLINE_LINE : OFFLINE_LINE;

            TagResolver.Single secondPlaceholder;
            if (status.online()) {
                secondPlaceholder = Placeholder.parsed("server", this.createActivityForServer(status.serverId()));
            } else {
                secondPlaceholder = Placeholder.parsed("last_seen", DurationFormatter.formatShortFromInstant(status.lastSeen()));
            }

            message.append(MINI_MESSAGE.deserialize(line, usernamePlaceholder, secondPlaceholder));
            message.append(Component.newline());
        }

        message.append(MESSAGE_FOOTER);
        return message.build();
    }

    private @NotNull List<FriendStatus> retrieveStatuses(@NotNull List<FriendCache.CachedFriend> friends) {
        Map<UUID, FriendStatus> statuses = new ConcurrentHashMap<>();
        for (FriendCache.CachedFriend(UUID playerId, Instant friendsSince) : friends) {
            statuses.put(playerId, new FriendStatus(playerId, friendsSince));
        }

        List<UUID> playerIds = new ArrayList<>();
        for (FriendCache.CachedFriend friend : friends) {
            playerIds.add(friend.playerId());
        }

        List<McPlayer> players;
        try {
            players = this.mcPlayerService.getPlayersById(playerIds);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to retrieve player statuses: ", exception);
            return new ArrayList<>(statuses.values());
        }

        for (McPlayer player : players) {
            UUID uuid = UUID.fromString(player.getId());
            FriendStatus status = statuses.get(uuid);

            String serverId = player.hasCurrentServer() ? player.getCurrentServer().getServerId() : null;
            Instant lastSeen = ProtoTimestampConverter.fromProto(player.getLastOnline());
            FriendStatus newStatus = status.toFull(player.getCurrentUsername(), player.hasCurrentServer(), serverId, lastSeen);

            statuses.put(uuid, newStatus);
        }

        return new ArrayList<>(statuses.values());
    }

    private record FriendStatus(@NotNull UUID uuid, @NotNull Instant friendsSince, @Nullable String username, boolean online,
                                @Nullable String serverId, @Nullable Instant lastSeen) implements Comparable<FriendStatus> {

        FriendStatus(@NotNull UUID uuid, @NotNull Instant friendsSince) {
            this(uuid, friendsSince, null, false, null, null);
        }

        @NotNull FriendStatus toFull(@Nullable String username, boolean online, @Nullable String serverId, @Nullable Instant lastSeen) {
            return new FriendStatus(this.uuid, this.friendsSince, username, online, serverId, lastSeen);
        }

        public int compareTo(@NotNull FriendListSub.FriendStatus o) {
            if (this.online && !o.online) return -1;
            if (!this.online && o.online) return 1;
            if (!this.online) return o.lastSeen.compareTo(this.lastSeen); // both offline
            return this.username.compareTo(o.username);
        }
    }

    private @NotNull String createActivityForServer(@NotNull String serverId) {
        String[] parts = serverId.split("-");
        String[] serverTypeIdParts = Arrays.copyOf(parts, parts.length - 2);
        String fleetId = String.join("-", serverTypeIdParts);

        GameModeConfig gameModeConfig = this.getGameModeConfig(fleetId);
        if (gameModeConfig != null) return gameModeConfig.activityNoun() + " " + gameModeConfig.friendlyName();

        LOGGER.warn("Could not find friendly name for fleet {}", fleetId);
        return fleetId;
    }

    private @Nullable GameModeConfig getGameModeConfig(@NotNull String fleetId) {
        if (this.gameModeCollection == null) return null;

        GameModeConfig gameModeConfig = null;
        for (GameModeConfig config : this.gameModeCollection.getAllConfigs()) {
            if (!config.fleetName().equals(fleetId)) continue;
            gameModeConfig = config;
            break;
        }

        return gameModeConfig;
    }
}
