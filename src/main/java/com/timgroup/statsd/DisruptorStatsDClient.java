package com.timgroup.statsd;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client implementation that queues up metric messages in a LMAX disruptor.
 */
public class DisruptorStatsDClient extends StringMessageStatsDClient {

    private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    private static final int PACKET_SIZE_BYTES = 1400;
    private static final StatsDClientErrorHandler NO_OP_HANDLER = e -> {};
    private static final EventFactory<DisruptorEvent> FACTORY = DisruptorEvent::new;
    private static final EventTranslatorOneArg<DisruptorEvent, String> TRANSLATOR =
            (event, sequence, msg) -> event.setValue(msg);

    private final String prefix;
    private final DatagramChannel clientChannel;
    private final AtomicReference<InetSocketAddress> address;
    private final StatsDClientErrorHandler errorHandler;
    private final String constantTagsRendered;

    private final ThreadFactory threadFactory = r -> {
        Thread thread = new Thread(r);
        thread.setName("statsd-disruptor-" + thread.getName());
        thread.setDaemon(true);
        return thread;
    };

    private final Disruptor<DisruptorEvent> disruptor = new Disruptor<>(FACTORY, 16384, threadFactory);

    public DisruptorStatsDClient(String prefix, String hostname, int port, String[] constantTags,
                                 StatsDClientErrorHandler errorHandler, final DisruptorEventHandler handler)
            throws StatsDClientException {

        if (prefix != null && prefix.length() > 0) {
            this.prefix = String.format("%s.", prefix);
        } else {
            this.prefix = "";
        }

        this.errorHandler = errorHandler;

        if (constantTags != null && constantTags.length > 0) {
            this.constantTagsRendered = tagString(constantTags, null);
        } else {
            this.constantTagsRendered = null;
        }

        try {
            this.clientChannel = DatagramChannel.open();
            this.address = scheduledResolveAddress(hostname, port);
        } catch (Exception e) {
            throw new StatsDClientException("Failed to start StatsD client", e);
        }

        disruptor.setDefaultExceptionHandler(new DisruptorExceptionHandler(this.errorHandler));

        disruptor.handleEventsWith(new DisruptorEventHandler[] { (handler != null) ? handler : new Handler() });

        disruptor.start();
    }

    public DisruptorStatsDClient(String prefix, String hostname, int port) throws StatsDClientException {
        this(prefix, hostname, port, null, NO_OP_HANDLER);
    }
    public DisruptorStatsDClient(String prefix, String hostname, int port, String[] constantTags)
            throws StatsDClientException {

        this(prefix, hostname, port, constantTags, NO_OP_HANDLER);
    }
    public DisruptorStatsDClient(String prefix, String hostname, int port, String[] constantTags,
                                 StatsDClientErrorHandler errorHandler) throws StatsDClientException {

        this(prefix, hostname, port, constantTags, errorHandler, null);
    }

    /**
     * Returns a {@link AtomicReference} containing a {@link InetSocketAddress} object that will be updated
     * periodically by a task that resolves the given hostname/port. The task is scheduled to run every minute.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    private AtomicReference<InetSocketAddress> scheduledResolveAddress(String hostname, int port)
            throws UnknownHostException {

        // Perform an initial resolution and schedule the task to run after an initial delay
        AtomicReference<InetSocketAddress> ref = new AtomicReference<>(resolveAddress(hostname, port));

        Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setName("statsd-dns-resolver");
            thread.setDaemon(true);
            return thread;
        }).scheduleAtFixedRate(() -> {
            try {
                ref.lazySet(resolveAddress(hostname, port));
            } catch (UnknownHostException ignored) {
                // subsequent failures to resolve the hostname should just leave the existing address
            }
        }, 1, 1, TimeUnit.MINUTES);

        return ref;
    }

    @Override
    public void close() {
        try {
            disruptor.shutdown(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            errorHandler.handle(e);
        } finally {
            if (clientChannel != null) {
                try {
                    clientChannel.close();
                } catch (IOException e) {
                    errorHandler.handle(e);
                }
            }
        }
    }

    @Override
    public String getConstantTagsRendered() {
        return constantTagsRendered;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    protected void send(String message) {
        if (!disruptor.getRingBuffer().tryPublishEvent(TRANSLATOR, message)) {
            errorHandler.handle(InsufficientCapacityException.INSTANCE);
        }
    }

    public static class DisruptorEvent {
        private String value;

        void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "Event: " + value;
        }
    }

    /**
     * Avoids "unchecked generic array creation for varargs parameter" errors when using the disruptor library
     */
    interface DisruptorEventHandler extends EventHandler<DisruptorEvent> {}

    protected class Handler implements DisruptorEventHandler {
        private final ByteBuffer sendBuffer = ByteBuffer.allocate(PACKET_SIZE_BYTES);

        @Override
        public void onEvent(DisruptorEvent event, long sequence, boolean batchEnd) throws Exception {
            String message = event.value;
            byte[] data = message.getBytes(MESSAGE_CHARSET);
            if (sendBuffer.remaining() < (data.length + 1)) {
                flush();
            }
            if (sendBuffer.position() > 0) {
                sendBuffer.put((byte) '\n');
            }
            sendBuffer.put(
                    data.length > sendBuffer.remaining() ? Arrays.copyOfRange(data, 0, sendBuffer.remaining()) : data);

            if (batchEnd || 0 == sendBuffer.remaining()) {
                flush();
            }
        }

        private void flush() throws Exception {
            InetSocketAddress inetSocketAddress = address.get();

            int sizeOfBuffer = sendBuffer.position();
            sendBuffer.flip();
            int sentBytes = clientChannel.send(sendBuffer, inetSocketAddress);
            sendBuffer.clear();

            if (sizeOfBuffer != sentBytes) {
                errorHandler.handle(new IOException(
                        String.format(
                                "Could not send entirely stat %s to host %s:%d. Only sent %d bytes out of %d bytes",
                                sendBuffer.toString(),
                                inetSocketAddress.getHostName(),
                                inetSocketAddress.getPort(),
                                sentBytes,
                                sizeOfBuffer)));
            }
        }
    }

    protected static class DisruptorExceptionHandler implements ExceptionHandler<Object> {
        private final FatalExceptionHandler throwableHandler = new FatalExceptionHandler();
        private final StatsDClientErrorHandler exceptionHandler;

        DisruptorExceptionHandler(StatsDClientErrorHandler handler) {
            this.exceptionHandler = handler;
        }

        @Override
        public void handleEventException(Throwable ex, long sequence, Object event) {
            if (ex instanceof Exception) {
                exceptionHandler.handle((Exception) ex);
            } else {
                throwableHandler.handleEventException(ex, sequence, event);
            }
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            if (ex instanceof Exception) {
                exceptionHandler.handle((Exception) ex);
            } else {
                throwableHandler.handleOnStartException(ex);
            }
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            if (ex instanceof Exception) {
                exceptionHandler.handle((Exception) ex);
            } else {
                throwableHandler.handleOnShutdownException(ex);
            }
        }
    }

    private static InetSocketAddress resolveAddress(String hostname, int port) throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(hostname), port);
    }
}
