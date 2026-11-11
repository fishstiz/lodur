package io.github.fishstiz.lodur.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.github.fishstiz.lodur.Lodur;
import io.github.fishstiz.lodur.ResourceReloader;
import net.minecraft.client.GameLoadCookie;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Mixin(Minecraft.class)
abstract class MinecraftMixin implements ResourceReloader {
    @Unique
    private static final ScopedValue<Boolean> CANCEL_RELOAD = ScopedValue.newInstance();

    @Shadow
    public abstract CompletableFuture<Void> reloadResourcePacks();

    @ModifyArg(
            method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/resources/ReloadInstance;Ljava/util/function/Consumer;Z)V"
            )
    )
    private Consumer<Optional<Throwable>> wrapExceptionally(
            Consumer<Optional<Throwable>> onFinish,
            @Local(name = "result") CompletableFuture<Void> result
    ) {
        return onFinish.andThen(maybeT -> maybeT.ifPresent(result::completeExceptionally));
    }

    @Inject(
            method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V")
    )
    private void captureStartOnReload(
            boolean isRecovery,
            @Nullable GameLoadCookie loadCookie,
            CallbackInfoReturnable<CompletableFuture<Void>> cir,
            @Share("startTime") LocalRef<Instant> startTimeRef
    ) {
        Lodur.LOG.info("[lodur] Reload starting...");
        startTimeRef.set(Instant.now());
    }

    @ModifyReturnValue(
            method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN")
    )
    private CompletableFuture<Void> logDurationOnReload(
            CompletableFuture<Void> original,
            @Share("startTime") LocalRef<Instant> startTimeRef
    ) {
        Instant startTime = startTimeRef.get();
        if (startTime != null) {
            original.thenRun(() -> {
                long duration = Duration.between(startTime, Instant.now()).toMillis();
                Lodur.LOG.info("[lodur] Reload finished in {} ms", duration);
            });
        }

        return original;
    }

    @WrapOperation(
            method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V")
    )
    private void cancelDiscovery(PackRepository instance, Operation<Void> original) {
        if (!CANCEL_RELOAD.isBound() || !CANCEL_RELOAD.get()) {
            original.call(instance);
        } else {
            Lodur.LOG.warn("[lodur] Reloading without discovery may cause issues when reading stale resources!");
        }
    }

    @Override
    public void lodur$reload(boolean discovery) {
        ScopedValue.where(CANCEL_RELOAD, !discovery).run(this::reloadResourcePacks);
    }
}
