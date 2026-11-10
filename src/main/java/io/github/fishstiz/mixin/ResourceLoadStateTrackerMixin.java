package io.github.fishstiz.mixin;

import net.minecraft.client.ResourceLoadStateTracker;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ResourceLoadStateTracker.class)
public class ResourceLoadStateTrackerMixin {
    @Unique
    private static final Logger LOG = LoggerFactory.getLogger("loadingoverlayduration");

    @Shadow
    private int reloadCount;

    @Shadow
    private ResourceLoadStateTracker.@Nullable ReloadState reloadState;

    @Unique
    private ResourceLoadStateTracker.@Nullable ReloadState reloadKey;

    @Unique
    private long reloadStart;

    @Inject(method = "startReload", at = @At("TAIL"))
    private void onStartReload(ResourceLoadStateTracker.ReloadReason reloadReason, List<PackResources> packs, CallbackInfo ci) {
        LOG.info("[loadingoverlayduration] Reload starting...");
        this.reloadKey = this.reloadState;
        this.reloadStart = System.nanoTime();
    }

    @Inject(method = "finishReload", at = @At("TAIL"))
    private void onFinishReload(CallbackInfo ci) {
        if (reloadKey != null && reloadKey == this.reloadState && reloadKey.finished) {
            long durationMs = (System.nanoTime() - this.reloadStart) / 1_000_000;
            LOG.info("[loadingoverlayduration] Reload finished in {} ms", durationMs);
        }
    }
}
