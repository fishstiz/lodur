package io.github.fishstiz.lodur;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class Lodur implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("lodur");
    private @Nullable Runnable pendingTest;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
            var lodur = ClientCommands.literal("lodur");

            dispatcher.register(
                    lodur.then(ClientCommands.literal("test")
                            .then(ClientCommands.literal("run")
                                    .then(ClientCommands.argument("count", IntegerArgumentType.integer(1)).executes(testExecutor(false))
                                            .then(ClientCommands.argument("minimize", BoolArgumentType.bool()).executes(testExecutor(true))
                                                    .then(ClientCommands.argument("discovery", BoolArgumentType.bool()).executes(testExecutor(true, true)))))))
            );

            dispatcher.register(
                    lodur.then(ClientCommands.literal("reload").executes(reloadExecutor(false))
                            .then(ClientCommands.argument("discovery", BoolArgumentType.bool()).executes(reloadExecutor(true))))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(_ -> {
            Runnable pendingTest = this.pendingTest;
            if (pendingTest != null) {
                pendingTest.run();
            }
        });
    }

    private Command<FabricClientCommandSource> testExecutor(boolean parseMinimize, boolean parseDiscovery) {
        return context -> {
            int count = IntegerArgumentType.getInteger(context, "count");
            boolean minimize = parseBool(context, "minimize", parseMinimize, false);
            boolean discovery = parseBool(context, "discovery", parseDiscovery, true);
            startTest(context.getSource().getClient(), count, minimize, discovery);
            return 1;
        };
    }

    private Command<FabricClientCommandSource> testExecutor(boolean parseMinimize) {
        return testExecutor(parseMinimize, false);
    }

    private Command<FabricClientCommandSource> reloadExecutor(boolean parseDiscovery) {
        return context -> {
            boolean discovery = parseBool(context, "discovery", parseDiscovery, true);
            Minecraft client = context.getSource().getClient();
            client.execute(() -> {
                sendChat(client, discovery ? "[lodur] Reloading Resources" : "[lodur] Reloading Resources without Discovery");
                ((ResourceReloader) client).lodur$reload(discovery);
            });
            return 1;
        };
    }

    private void startTest(Minecraft client, int count, boolean minimize, boolean discovery) {
        if (this.pendingTest != null) {
            sendChat(client, "[lodur] Test is already running!");
        } else if (count > 0) {
            if (minimize) {
                SDLVideo.SDL_MinimizeWindow(client.getWindow().handle());
            }

            SDLVideo.SDL_SetWindowProgressState(client.getWindow().handle(), SDLVideo.SDL_PROGRESS_STATE_NORMAL);
            SDLVideo.SDL_SetWindowProgressValue(client.getWindow().handle(), 0f);

            sendChat(client, "[lodur] Starting test with " + count + " reload/s...");
            queueTest(client, discovery, count, count, Instant.now(), new LongArrayList(count));
        }
    }

    private void queueTest(
            Minecraft client,
            boolean discovery,
            int remaining,
            int count,
            Instant start,
            LongList reloadDurations
    ) {
        if (remaining <= 0) {
            endTest(client, start, reloadDurations);
            return;
        }

        this.pendingTest = () -> {
            if (client.gui.overlay() instanceof LoadingOverlay) {
                return;
            }

            this.pendingTest = null;

            Instant reloadStart = Instant.now();
            ((ResourceReloader) client).lodur$reload(discovery).whenCompleteAsync((_, throwable) -> {
                if (throwable == null) {
                    long duration = Duration.between(reloadStart, Instant.now()).toMillis();
                    int progress = count - remaining + 1;
                    LOG.info("[lodur] Test reload {}/{} took {} ms", progress, count, duration);
                    reloadDurations.add(duration);
                    SDLVideo.SDL_SetWindowProgressValue(client.getWindow().handle(), (float) progress / count);
                    queueTest(client, discovery, remaining - 1, count, start, reloadDurations);
                } else {
                    sendChat(client, "[lodur] An error occurred while running test. Aborting...");
                    SDLVideo.SDL_SetWindowProgressState(client.getWindow().handle(), SDLVideo.SDL_PROGRESS_STATE_ERROR);
                }
            }, client);
        };
    }

    private void endTest(Minecraft client, Instant start, LongList reloadDurations) {
        this.pendingTest = null;

        long testDuration = Duration.between(start, Instant.now()).toMillis();
        ReloadTestStatistics stats = ReloadTestStatistics.create(testDuration, reloadDurations);

        sendChat(client, "[lodur] Test finished: " + stats.summary() + " | " + stats.extendedSummary());

        SDLVideo.SDL_SetWindowProgressState(client.getWindow().handle(), SDLVideo.SDL_PROGRESS_STATE_NONE);
        SDLVideo.SDL_FlashWindow(client.getWindow().handle(), SDLVideo.SDL_FLASH_UNTIL_FOCUSED);

        stats.buildReport().whenCompleteAsync((uri, throwable) -> {
            if (throwable != null) {
                LOG.error("[lodur] Failed to generate report.", throwable);
                sendChat(client, "[lodur] Failed to generate report. ");
            } else if (uri != null) {
                LOG.info("[lodur] Report generated at uri: {}", uri);

                Path path = Path.of(uri);
                String name = path.getName(path.getNameCount() - 1).toString();
                sendChat(client, Component.literal("[lodur] Report generated: ").append(
                        Component.literal(name).withStyle(s -> s
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent.OpenUrl(uri))
                        )
                ));
            }
        }, client);
    }

    private static void sendChat(Minecraft client, Component message) {
        client.gui.hud.getChat().addClientSystemMessage(message);
    }

    private static void sendChat(Minecraft client, String message) {
        sendChat(client, Component.literal(message));
    }

    private static boolean parseBool(CommandContext<?> context, String arg, boolean parse, boolean defaultValue) {
        return parse ? BoolArgumentType.getBool(context, arg) : defaultValue;
    }
}
