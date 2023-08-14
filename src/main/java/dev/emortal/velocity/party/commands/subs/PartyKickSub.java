package dev.emortal.velocity.party.commands.subs;

import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.api.command.CommandExecutor;
import dev.emortal.api.service.party.KickPlayerFromPartyResult;
import dev.emortal.api.service.party.PartyService;
import dev.emortal.api.utils.resolvers.PlayerResolver;
import dev.emortal.velocity.lang.TempLang;
import dev.emortal.velocity.party.commands.PartyCommand;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartyKickSub implements CommandExecutor<CommandSource> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PartyKickSub.class);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();


    private static final String KICKED_MESSAGE = "<green>Kicked <username> from your party";
    private static final String NOT_LEADER_MESSAGE = "<red>You must be the party leader to kick players";
    private static final String TARGET_IS_LEADER_MESSAGE = "<red>You cannot kick the party leader";
    private static final String NOT_IN_PARTY_MESSAGE = "<red><username> is not in your party";


    private final @NotNull PartyService partyService;

    public PartyKickSub(@NotNull PartyService partyService) {
        this.partyService = partyService;
    }

    @Override
    public void execute(@NotNull CommandContext<CommandSource> context) {
        Player executor = (Player) context.getSource();
        String targetUsername = context.getArgument("player", String.class);

        PlayerResolver.CachedMcPlayer target;
        try {
            target = PlayerResolver.getPlayerData(targetUsername);
        } catch (StatusException exception) {
            LOGGER.error("An error occurred PartyKickSub getPlayerByUsername: ", exception);
            executor.sendMessage(PartyCommand.ERROR_MESSAGE);
            return;
        }

        if (target == null) {
            TempLang.PLAYER_NOT_FOUND.send(executor, Placeholder.unparsed("search_username", targetUsername));
            return;
        }

        KickPlayerFromPartyResult result;
        try {
            result = this.partyService.kickPlayer(executor.getUniqueId(), executor.getUsername(), target.uuid());
        } catch (StatusRuntimeException exception) {
            LOGGER.error("An error occurred PartyKickSub kickPlayerFromParty: ", exception);
            executor.sendMessage(PartyCommand.ERROR_MESSAGE);
            return;
        }

        var message = switch (result) {
            case SUCCESS -> MINI_MESSAGE.deserialize(KICKED_MESSAGE, Placeholder.unparsed("username", target.username()));
            case SELF_NOT_LEADER -> MINI_MESSAGE.deserialize(NOT_LEADER_MESSAGE);
            case TARGET_IS_LEADER -> MINI_MESSAGE.deserialize(TARGET_IS_LEADER_MESSAGE);
            case TARGET_NOT_IN_PARTY -> MINI_MESSAGE.deserialize(NOT_IN_PARTY_MESSAGE, Placeholder.unparsed("username", target.username()));
        };
        executor.sendMessage(message);
    }
}
