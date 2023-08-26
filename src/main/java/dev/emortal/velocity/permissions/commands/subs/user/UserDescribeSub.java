package dev.emortal.velocity.permissions.commands.subs.user;

import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import dev.emortal.api.command.CommandExecutor;
import dev.emortal.api.grpc.permission.PermissionProto;
import dev.emortal.api.service.permission.PermissionService;
import dev.emortal.velocity.lang.ChatMessages;
import dev.emortal.velocity.permissions.PermissionCache;
import dev.emortal.velocity.player.resolver.CachedMcPlayer;
import dev.emortal.velocity.player.resolver.PlayerResolver;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class UserDescribeSub implements CommandExecutor<CommandSource> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserDescribeSub.class);

    private final PermissionService permissionService;
    private final PermissionCache permissionCache;
    private final PlayerResolver playerResolver;

    public UserDescribeSub(@NotNull PermissionService permissionService, @NotNull PermissionCache permissionCache,
                           @NotNull PlayerResolver playerResolver) {
        this.permissionService = permissionService;
        this.permissionCache = permissionCache;
        this.playerResolver = playerResolver;
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSource> context) {
        CommandSource source = context.getSource();
        String targetUsername = context.getArgument("username", String.class);

        CachedMcPlayer target;
        try {
            target = this.playerResolver.getPlayer(targetUsername);
        } catch (StatusException exception) {
            LOGGER.error("Failed to get player data for '{}'", targetUsername, exception);
            ChatMessages.GENERIC_ERROR.send(source);
        }

        if (target == null) {
            ChatMessages.ERROR_USER_NOT_FOUND.send(source, Component.text(targetUsername));
            return;
        }

        UUID targetId = target.uuid();
        String correctedUsername = target.username();

        PermissionProto.PlayerRolesResponse response;
        try {
            response = this.permissionService.getPlayerRoles(targetId);
        } catch (StatusRuntimeException exception) {
            LOGGER.error("Failed to retrieve roles for '{}'", targetId, exception);
            ChatMessages.GENERIC_ERROR.send(source);
            return;
        }

        List<String> roleIds = response.getRoleIdsList();
        List<PermissionCache.CachedRole> sortedRoles = this.sortRolesByWeight(roleIds);

        List<Component> roleComponents = new ArrayList<>();
        for (int i = 0; i < sortedRoles.size(); i++) {
            PermissionCache.CachedRole role = sortedRoles.get(i);
            if (i == 0) {
                roleComponents.add(Component.text(role.id(), Style.style(TextDecoration.BOLD)));
            } else {
                roleComponents.add(Component.text(role.id()));
            }
        }
        Component groupsValue = Component.join(JoinConfiguration.commas(true), roleComponents);
        String activeDisplayName = this.permissionCache.determineActiveName(roleIds);

        TextComponent.Builder exampleChatBuilder = Component.text();

        if (activeDisplayName != null) {
            exampleChatBuilder.append(MiniMessage.miniMessage().deserialize(activeDisplayName, Placeholder.unparsed("username", correctedUsername)));
        } else {
            exampleChatBuilder.append(Component.text(correctedUsername));
        }
        exampleChatBuilder.append(Component.text(": Test Chat", NamedTextColor.WHITE));

        int permissionCount = 0;
        for (PermissionCache.CachedRole role : sortedRoles) {
            permissionCount += role.permissions().size();
        }

        Component displayName = Component.text(activeDisplayName == null ? "null" : activeDisplayName);
        ChatMessages.USER_DESCRIPTION.send(source,
                Component.text(correctedUsername),
                groupsValue,
                Component.text(permissionCount),
                displayName,
                exampleChatBuilder.build());
    }

    private @NotNull List<PermissionCache.CachedRole> sortRolesByWeight(@NotNull List<String> roleIds) {
        return roleIds.stream()
                .map(this.permissionCache::getRole)
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.priority(), a.priority()))
                .toList();
    }
}
