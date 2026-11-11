package io.github.fishstiz.lodur;

import io.github.fishstiz.lodur.utils.CSVBuilder;
import it.unimi.dsi.fastutil.longs.LongImmutableList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CompletableFuture;

public final class ReloadTestStatistics {
    private final LongList reloadDurations;
    private final LongSummaryStatistics stats;
    private final double median;
    private final double stdDev;
    private final long runDuration;

    private ReloadTestStatistics(
            LongList reloadDurations,
            LongSummaryStatistics stats,
            double median,
            double stdDev,
            long runDuration
    ) {
        this.reloadDurations = reloadDurations;
        this.stats = stats;
        this.median = median;
        this.stdDev = stdDev;
        this.runDuration = runDuration;
    }

    public static ReloadTestStatistics create(long runDuration, LongList reloadDurations) {
        LongSummaryStatistics stats = reloadDurations.longStream().summaryStatistics();
        long[] millis = reloadDurations.toLongArray();
        return new ReloadTestStatistics(
                new LongImmutableList(reloadDurations),
                stats,
                getMedian(millis),
                getStdDev(millis, stats.getAverage()),
                runDuration
        );
    }

    private static double getMedian(long[] millis) {
        if (millis.length % 2 == 1) {
            return millis[millis.length / 2];
        } else {
            return (millis[millis.length / 2 - 1] + millis[millis.length / 2]) / 2.0;
        }
    }

    private static double getStdDev(long[] millis, double mean) {
        double varianceSum = 0;
        for (long m : millis) {
            varianceSum += Math.pow(m - mean, 2);
        }
        return (millis.length > 1) ? Math.sqrt(varianceSum / (millis.length - 1)) : 0.0;
    }

    public long count() {
        return stats.getCount();
    }

    public double mean() {
        return stats.getAverage();
    }

    public long max() {
        return stats.getMax();
    }

    public long min() {
        return stats.getMin();
    }

    public long sum() {
        return stats.getSum();
    }

    public double median() {
        return median;
    }

    public double stdDev() {
        return stdDev;
    }

    public long runDuration() {
        return runDuration;
    }

    public LongList reloadDurations() {
        return reloadDurations;
    }

    public String summary() {
        return "%d reload/s in %d ms (%d ms of reloading)".formatted(count(), runDuration(), sum());
    }

    public String extendedSummary() {
        return "mean: %.2f ms, median: %.2f ms, min: %d ms, max: %d ms, std dev: %.2f ms".formatted(
                mean(), median(), min(), max(), stdDev()
        );
    }

    public CompletableFuture<URI> buildReport() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createReportURI();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, Util.backgroundExecutor());
    }

    private URI createReportURI() throws IOException {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("lodur").resolve("reports");
        Files.createDirectories(dir);
        Path path = dir.resolve("report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS")) + ".csv");
        Files.writeString(path, createReportCSV());
        return path.toUri();
    }

    private String createReportCSV() {
        CSVBuilder builder = new CSVBuilder();

        builder.values("Execution (ms)", runDuration());
        builder.newLine();
        builder.newLine();
        builder.values("Reload Count", count());
        builder.newLine();
        builder.values("Total (ms)", sum());
        builder.newLine();
        builder.values("Mean (ms)", mean());
        builder.newLine();
        builder.values("Median (ms)", median());
        builder.newLine();
        builder.values("Min (ms)", min());
        builder.newLine();
        builder.values("Max (ms)", max());
        builder.newLine();
        builder.values("Std Dev (ms)", stdDev());
        builder.newLine();
        builder.newLine();
        builder.values("Reload", "Duration (ms)");

        LongList reloads = reloadDurations();
        for (int i = 0; i < reloads.size(); i++) {
            builder.newLine();
            builder.values(i + 1, reloads.getLong(i));
        }

        builder.newLine();
        builder.values("Total (ms)", sum());

        return builder.build();
    }
}
