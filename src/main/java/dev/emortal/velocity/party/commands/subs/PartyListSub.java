package dev.emortal.velocity.party.commands.subs;

import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.emortal.velocity.party.PartyCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.util.*;

public class PartyListSub {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String MESSAGE_CONTENT = """
            <color:#2383d1><strikethrough>          </strikethrough> <bold><color:#2ba0ff>ʏᴏᴜʀ ᴘᴀʀᴛʏ </bold>(<party_size>) <strikethrough>          </strikethrough></color>
                        
            <member_content>
                        
            <color:#2383d1><strikethrough>                                          </strikethrough>""";

    private final @NotNull PartyCache partyCache;

    public PartyListSub(@NotNull PartyCache partyCache) {
        this.partyCache = partyCache;
    }

    public int execute(CommandContext<CommandSource> context) {
        Player executor = (Player) context.getSource();

        PartyCache.CachedParty party = this.partyCache.getPlayerParty(executor.getUniqueId());
        if (party == null) {
            executor.sendMessage(MINI_MESSAGE.deserialize("<red>You are not in a party."));
            return 0;
        }

        executor.sendMessage(MINI_MESSAGE.deserialize(MESSAGE_CONTENT,
                Placeholder.unparsed("party_size", String.valueOf(party.getMembers().size())),
                Placeholder.component("member_content", this.createMessageContent(party)))
        );
        return 1;
    }

    private Component createMessageContent(PartyCache.CachedParty party) {
        Map<UUID, PartyCache.CachedPartyMember> members = new HashMap<>(party.getMembers());
        PartyCache.CachedPartyMember leader = members.remove(party.getLeaderId());

        TextComponent.Builder contentBuilder = Component.text();
        contentBuilder.append(Component.text("⭐ ", TextColor.color(255, 225, 115)));
        contentBuilder.append(Component.text(leader.username(), TextColor.color(255, 225, 115)));

        for (PartyCache.CachedPartyMember member : members.values()) {
            contentBuilder.append(Component.text(", ", NamedTextColor.GRAY));
            contentBuilder.append(Component.text(member.username(), NamedTextColor.GRAY));
        }

        return contentBuilder.build();
    }
}
