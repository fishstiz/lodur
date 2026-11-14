package io.github.fishstiz.lodur;

import java.util.concurrent.CompletableFuture;

public interface ResourceReloader {
    default CompletableFuture<Void> lodur$reload(boolean discovery) {
        throw new AssertionError();
    }
}
