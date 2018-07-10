package com.timgroup.statsd;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Abstract implementation of {@link StatsDClient} that requires a minimal set of
 * methods to be implemented in order to support all operations:
 *
 * <ul>
 *     <li>{@link #send(String)}</li>
 *     <li>{@link #getPrefix()}</li>
 *     <li>{@link #getConstantTagsRendered()} (optional)</li>
 * </ul>
 *
 * Implementation classes should typically insert the message into a queue and have a
 * separate thread processing off the queue. However, other implementation can handle it
 * differently.
 */
public abstract class StringMessageStatsDClient implements StatsDClient {
    /**
     * Because NumberFormat is not thread-safe we cannot share instances across threads. Use a ThreadLocal to
     * create one pre thread as this seems to offer a significant performance improvement over creating one per-thread:
     * http://stackoverflow.com/a/1285297/2648
     * https://github.com/indeedeng/java-dogstatsd-client/issues/4
     */
    private static final ThreadLocal<NumberFormat> NUMBER_FORMATTERS = ThreadLocal.withInitial(() -> {
        // Always create the formatter for the US locale in order to avoid this bug:
        // https://github.com/indeedeng/java-dogstatsd-client/issues/3
        NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);
        numberFormatter.setGroupingUsed(false);
        numberFormatter.setMaximumFractionDigits(6);
        configureFormatter(numberFormatter);
        return numberFormatter;
    });

    private static final ThreadLocal<NumberFormat> SAMPLE_RATE_FORMATTERS = ThreadLocal.withInitial(() -> {
        final NumberFormat numberFormatter = NumberFormat.getInstance(Locale.US);
        numberFormatter.setGroupingUsed(false);
        numberFormatter.setMinimumFractionDigits(6);
        configureFormatter(numberFormatter);
        return numberFormatter;
    });

    private static void configureFormatter(NumberFormat numberFormatter) {
        // we need to specify a value for Double.NaN that is recognized by dogStatsD
        if (numberFormatter instanceof DecimalFormat) {
            final DecimalFormat decimalFormat = (DecimalFormat) numberFormatter;
            final DecimalFormatSymbols symbols = decimalFormat.getDecimalFormatSymbols();
            symbols.setNaN("NaN");
            decimalFormat.setDecimalFormatSymbols(symbols);
        }
    }

    protected abstract void send(final String message);

    /**
     * Defines tags that should always be included with the metrics sent. Defaults to null for none.
     */
    public String getConstantTagsRendered() {
        return null;
    }

    /**
     * Defines a string prefix that should always be prepended to the metric names being emitted. Typically
     * you'll want this string to end with a ".".
     */
    public abstract String getPrefix();

    /**
     * Generate a suffix conveying the given tag list to the client
     */
    private String tagString(final String[] tags) {
        return tagString(tags, getConstantTagsRendered());
    }

    @Override
    public void count(String aspect, long delta, String... tags) {
        send(String.format("%s%s:%d|c%s", getPrefix(), aspect, delta, tagString(tags)));
    }

    @Override
    public void count(String aspect, long delta, double sampleRate, String... tags) {
        if (isUnsampledEvent(sampleRate)) {
            return;
        }
        send(String.format("%s%s:%d|c|@%s%s", getPrefix(), aspect, delta,
                SAMPLE_RATE_FORMATTERS.get().format(sampleRate), tagString(tags)));
    }

    @Override
    public void recordGaugeValue(String aspect, double value, String... tags) {
        // Intentionally using %s rather than %f here to avoid padding with extra 0s to represent precision
        send(String.format("%s%s:%s|g%s", getPrefix(), aspect, NUMBER_FORMATTERS.get().format(value), tagString(tags)));
    }

    @Override
    public void recordGaugeValue(String aspect, double value, double sampleRate, String... tags) {
        if (isUnsampledEvent(sampleRate)) {
            return;
        }
        send(String.format("%s%s:%s|g|@%s%s", getPrefix(), aspect, NUMBER_FORMATTERS.get().format(value),
                SAMPLE_RATE_FORMATTERS.get().format(sampleRate), tagString(tags)));
    }

    @Override
    public void recordGaugeValue(String aspect, long value, String... tags) {
        send(String.format("%s%s:%d|g%s", getPrefix(), aspect, value, tagString(tags)));
    }

    @Override
    public void recordGaugeValue(String aspect, long value, double sampleRate, String... tags) {
        if (isUnsampledEvent(sampleRate)) {
            return;
        }
        send(String.format("%s%s:%d|g|@%s%s", getPrefix(), aspect, value,
                SAMPLE_RATE_FORMATTERS.get().format(sampleRate), tagString(tags)));
    }

    @Override
    public void recordExecutionTime(String aspect, long timeInMs, String... tags) {
        send(String.format("%s%s:%d|ms%s", getPrefix(), aspect, timeInMs, tagString(tags)));
    }

    @Override
    public void recordExecutionTime(String aspect, long timeInMs, double sampleRate, String... tags) {
        if (isUnsampledEvent(sampleRate)) {
            return;
        }
        send(String.format("%s%s:%d|ms|@%s%s", getPrefix(), aspect, timeInMs,
                SAMPLE_RATE_FORMATTERS.get().format(sampleRate), tagString(tags)));
    }

    @Override
    public void recordHistogramValue(String aspect, double value, String... tags) {
        // Intentionally using %s rather than %f here to avoid padding with extra 0s to represent precision
        send(String.format("%s%s:%s|h%s", getPrefix(), aspect, NUMBER_FORMATTERS.get().format(value), tagString(tags)));
    }

    @Override
    public void recordHistogramValue(String aspect, double value, double sampleRate, String... tags) {
        if (isUnsampledEvent(sampleRate)) {
            return;
        }

        // Intentionally using %s rather than %f here to avoid padding with extra 0s to represent precision
        send(String.format("%s%s:%s|h|@%s%s", getPrefix(), aspect, NUMBER_FORMATTERS.get().format(value),
                SAMPLE_RATE_FORMATTERS.get().format(sampleRate), tagString(tags)));
    }

    @Override
    public void recordHistogramValue(String aspect, long value, String... tags) {
        send(String.format("%s%s:%d|h%s", getPrefix(), aspect, value, tagString(tags)));
    }

    @Override
    public void recordHistogramValue(String aspect, long value, double sampleRate, String... tags) {
        if (isUnsampledEvent(sampleRate)) {
            return;
        }
        send(String.format("%s%s:%d|h|@%s%s", getPrefix(), aspect, value,
                SAMPLE_RATE_FORMATTERS.get().format(sampleRate), tagString(tags)));
    }

    @Override
    public void recordEvent(Event event, String... tags) {
        final String title = (getPrefix() + event.getTitle()).replace("\n", "\\n");
        final String text = event.getText().replace("\n", "\\n");
        send(String.format("_e{%d,%d}:%s|%s%s%s",
                title.length(), text.length(), title, text, eventMap(event), tagString(tags)));
    }

    @Override
    public void recordServiceCheckRun(ServiceCheck sc) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("_sc|%s|%d", sc.getName(), sc.getStatus()));
        if (sc.getTimestamp() > 0) {
            sb.append(String.format("|d:%d", sc.getTimestamp()));
        }
        if (sc.getHostname() != null) {
            sb.append(String.format("|h:%s", sc.getHostname()));
        }
        sb.append(tagString(sc.getTags()));
        if (sc.getMessage() != null) {
            sb.append(String.format("|m:%s", sc.getEscapedMessage()));
        }

        send(sb.toString());
    }

    @Override
    public void recordSetValue(String aspect, String value, String... tags) {
        send(String.format("%s%s:%s|s%s", getPrefix(), aspect, value, tagString(tags)));
    }

    private boolean isUnsampledEvent(double sampleRate) {
        return sampleRate != 1.0 && ThreadLocalRandom.current().nextDouble() > sampleRate;
    }

    private String eventMap(final Event event) {
        final StringBuilder res = new StringBuilder();

        final long millisSinceEpoch = event.getMillisSinceEpoch();
        if (millisSinceEpoch != -1) {
            res.append("|d:").append(millisSinceEpoch / 1000);
        }

        final String hostname = event.getHostname();
        if (hostname != null) {
            res.append("|h:").append(hostname);
        }

        final String aggregationKey = event.getAggregationKey();
        if (aggregationKey != null) {
            res.append("|k:").append(aggregationKey);
        }

        final String priority = event.getPriority();
        if (priority != null) {
            res.append("|p:").append(priority);
        }

        final String alertType = event.getAlertType();
        if (alertType != null) {
            res.append("|t:").append(alertType);
        }

        return res.toString();
    }

    /**
     * Generate a suffix conveying the given tag list to the client
     */
    static String tagString(String[] tags, String tagPrefix) {
        List<String> components = new ArrayList<>();
        components.add(tagPrefix);

        if (tags != null) {
            components.addAll(Arrays.asList(tags));
        }

        String joined = components.stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(","));

        return ((tagPrefix == null && !joined.isEmpty()) ? "|#" : "") + joined;
    }
}
