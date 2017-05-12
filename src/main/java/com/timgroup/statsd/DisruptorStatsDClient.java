package com.timgroup.statsd;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Client implementation that queues up metric messages in a LMAX disruptor.
 */
public class DisruptorStatsDClient extends StringMessageStatsDClient {

    public static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    private static final int PACKET_SIZE_BYTES = 1400;
    private static final StatsDClientErrorHandler NO_OP_HANDLER = e -> {};
    private static final EventFactory<DisruptorEvent> FACTORY = DisruptorEvent::new;
    private static final EventTranslatorOneArg<DisruptorEvent, String> TRANSLATOR =
            (event, sequence, msg) -> event.setValue(msg);

    private final String prefix;
    private final DatagramChannel clientChannel;
    private final InetSocketAddress address;
    private final StatsDClientErrorHandler errorHandler;
    private final String constantTagsRendered;

    private final ThreadFactory threadFactory = r -> {
        Thread thread = new Thread(r);
        thread.setName("StatsD-disruptor-" + thread.getName());
        thread.setDaemon(true);
        return thread;
    };

    private final Disruptor<DisruptorEvent> disruptor = new Disruptor<>(FACTORY, 16384, threadFactory);

    public DisruptorStatsDClient(String prefix, String hostname, int port, String[] constantTags,
                                 StatsDClientErrorHandler errorHandler, DisruptorEventHandler handler)
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
            this.address = new InetSocketAddress(hostname, port);
        } catch (Exception e) {
            throw new StatsDClientException("Failed to start StatsD client", e);
        }

        disruptor.setDefaultExceptionHandler(new DisruptorExceptionHandler(this.errorHandler));

        if (handler != null) {
            disruptor.handleEventsWith(handler);
        } else {
            disruptor.handleEventsWith(new Handler());
        }

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

        public void setValue(String value) {
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
    public interface DisruptorEventHandler extends EventHandler<DisruptorEvent> {}

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

        private void flush() throws IOException {
            int sizeOfBuffer = sendBuffer.position();
            sendBuffer.flip();
            int sentBytes = clientChannel.send(sendBuffer, address);
            sendBuffer.clear();

            if (sizeOfBuffer != sentBytes) {
                errorHandler.handle(new IOException(
                        String.format(
                                "Could not send entirely stat %s to host %s:%d. Only sent %d bytes out of %d bytes",
                                sendBuffer.toString(),
                                address.getHostName(),
                                address.getPort(),
                                sentBytes,
                                sizeOfBuffer)));
            }
        }
    }

    protected static class DisruptorExceptionHandler implements ExceptionHandler<Object> {
        private final FatalExceptionHandler throwableHandler = new FatalExceptionHandler();
        private final StatsDClientErrorHandler exceptionHandler;

        public DisruptorExceptionHandler(StatsDClientErrorHandler handler) {
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
}
