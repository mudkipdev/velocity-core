package dev.emortal.velocity.adapter.server;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ServerProvider {

    @Nullable RegisteredServer getServer(@NotNull String name);

    @NotNull RegisteredServer createServer(@NotNull String name, @NotNull String address, int port);

    void unregisterServer(@NotNull ServerInfo info);
}
