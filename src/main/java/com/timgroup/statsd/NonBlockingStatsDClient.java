package com.timgroup.statsd;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.util.concurrent.*;



/**
 * A simple StatsD client implementation facilitating metrics recording.
 *
 * <p>Upon instantiation, this client will establish a socket connection to a StatsD instance
 * running on the specified host and port. Metrics are then sent over this connection as they are
 * received by the client.
 * </p>
 *
 * <p>Three key methods are provided for the submission of data-points for the application under
 * scrutiny:
 * <ul>
 *   <li>{@link #incrementCounter} - adds one to the value of the specified named counter</li>
 *   <li>{@link #recordGaugeValue} - records the latest fixed value for the specified named gauge</li>
 *   <li>{@link #recordExecutionTime} - records an execution time in milliseconds for the specified named operation</li>
 *   <li>{@link #recordHistogramValue} - records a value, to be tracked with average, maximum, and percentiles</li>
 *   <li>{@link #recordEvent} - records an event</li>
 *   <li>{@link #recordSetValue} - records a value in a set</li>
 * </ul>
 * From the perspective of the application, these methods are non-blocking, with the resulting
 * IO operations being carried out in a separate thread. Furthermore, these methods are guaranteed
 * not to throw an exception which may disrupt application execution.
 *
 * <p>As part of a clean system shutdown, the {@link #close()} method should be invoked
 * on any StatsD clients.</p>
 *
 * @author Tom Denley
 *
 */
public class NonBlockingStatsDClient extends StringMessageStatsDClient {

    public static final Charset MESSAGE_CHARSET = Charset.forName("UTF-8");
    private static final int PACKET_SIZE_BYTES = 1400;
    private static final StatsDClientErrorHandler NO_OP_HANDLER = e -> { /* No-op */ };

    private final String prefix;
    private final DatagramChannel clientChannel;
    private final StatsDClientErrorHandler handler;
    private final String constantTagsRendered;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        final ThreadFactory delegate = Executors.defaultThreadFactory();
        @Override public Thread newThread(final Runnable r) {
            final Thread result = delegate.newThread(r);
            result.setName("StatsD-" + result.getName());
            result.setDaemon(true);
            return result;
        }
    });

    private final BlockingQueue<String> queue;

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final String hostname, final int port) throws StatsDClientException {
        this(prefix, hostname, port, Integer.MAX_VALUE);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param queueSize
     *     the maximum amount of unprocessed messages in the BlockingQueue.
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final String hostname, final int port, final int queueSize) throws StatsDClientException {
        this(prefix, hostname, port, queueSize, null, null);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final String hostname, final int port, final String... constantTags) throws StatsDClientException {
        this(prefix, hostname, port, Integer.MAX_VALUE, constantTags, null);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @param queueSize
     *     the maximum amount of unprocessed messages in the BlockingQueue.
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final String hostname, final int port, final int queueSize, final String... constantTags) throws StatsDClientException {
        this(prefix, hostname, port, queueSize, constantTags, null);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @param errorHandler
     *     handler to use when an exception occurs during usage, may be null to indicate noop
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final String hostname, final int port,
                                   final String[] constantTags, final StatsDClientErrorHandler errorHandler) throws StatsDClientException {
        this(prefix, Integer.MAX_VALUE, constantTags, errorHandler, staticStatsDAddressResolution(hostname, port));
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @param errorHandler
     *     handler to use when an exception occurs during usage, may be null to indicate noop
     * @param queueSize
     *     the maximum amount of unprocessed messages in the BlockingQueue.
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final String hostname, final int port, final int queueSize,
                                   final String[] constantTags, final StatsDClientErrorHandler errorHandler) throws StatsDClientException {
        this(prefix, queueSize, constantTags, errorHandler, staticStatsDAddressResolution(hostname, port));
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param constantTags
     *     tags to be added to all content sent
     * @param errorHandler
     *     handler to use when an exception occurs during usage, may be null to indicate noop
     * @param addressLookup
     *     yields the IP address and socket of the StatsD server
     * @param queueSize
     *     the maximum amount of unprocessed messages in the BlockingQueue.
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClient(final String prefix, final int queueSize, String[] constantTags, final StatsDClientErrorHandler errorHandler,
                                   final Callable<InetSocketAddress> addressLookup) throws StatsDClientException {
        if((prefix != null) && (!prefix.isEmpty())) {
            this.prefix = String.format("%s.", prefix);
        } else {
            this.prefix = "";
        }
        if(errorHandler == null) {
            handler = NO_OP_HANDLER;
        }
        else {
            handler = errorHandler;
        }

        /* Empty list should be null for faster comparison */
        if((constantTags != null) && (constantTags.length == 0)) {
            constantTags = null;
        }

        if(constantTags != null) {
            constantTagsRendered = tagString(constantTags, null);
        } else {
            constantTagsRendered = null;
        }

        try {
            clientChannel = DatagramChannel.open();
        } catch (final Exception e) {
            throw new StatsDClientException("Failed to start StatsD client", e);
        }
        queue = new LinkedBlockingQueue<>(queueSize);
        executor.submit(new QueueConsumer(addressLookup));
    }

    /**
     * Cleanly shut down this StatsD client. This method may throw an exception if
     * the socket cannot be closed.
     */
    @Override
    public void close() {
        try {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch (final Exception e) {
            handler.handle(e);
        }
        finally {
            if (clientChannel != null) {
                try {
                    clientChannel.close();
                }
                catch (final IOException e) {
                    handler.handle(e);
                }
            }
        }
    }

    protected void send(final String message) {
        queue.offer(message);
    }

    @Override
    public String getConstantTagsRendered() {
        return constantTagsRendered;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    private class QueueConsumer implements Runnable {
        private final ByteBuffer sendBuffer = ByteBuffer.allocate(PACKET_SIZE_BYTES);

        private final Callable<InetSocketAddress> addressLookup;

        QueueConsumer(final Callable<InetSocketAddress> addressLookup) {
            this.addressLookup = addressLookup;
        }

        @Override public void run() {
            while(!executor.isShutdown()) {
                try {
                    final String message = queue.poll(1, TimeUnit.SECONDS);
                    if(null != message) {
                        final InetSocketAddress address = addressLookup.call();
                        final byte[] data = message.getBytes(MESSAGE_CHARSET);
                        if(sendBuffer.remaining() < (data.length + 1)) {
                            blockingSend(address);
                        }
                        if(sendBuffer.position() > 0) {
                            sendBuffer.put( (byte) '\n');
                        }
                        sendBuffer.put(data);
                        if(null == queue.peek()) {
                            blockingSend(address);
                        }
                    }
                } catch (final Exception e) {
                    handler.handle(e);
                }
            }
        }

        private void blockingSend(final InetSocketAddress address) throws IOException {
            final int sizeOfBuffer = sendBuffer.position();
            sendBuffer.flip();

            final int sentBytes = clientChannel.send(sendBuffer, address);
            sendBuffer.limit(sendBuffer.capacity());
            sendBuffer.rewind();

            if (sizeOfBuffer != sentBytes) {
                handler.handle(
                        new IOException(
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

    /**
     * Create dynamic lookup for the given host name and port.
     *
     * @param hostname the host name of the targeted StatsD server
     * @param port     the port of the targeted StatsD server
     * @return a function to perform the lookup
     */
    public static Callable<InetSocketAddress> volatileAddressResolution(final String hostname, final int port) {
        return () -> new InetSocketAddress(InetAddress.getByName(hostname), port);
    }

  /**
   * Lookup the address for the given host name and cache the result.
   *
   * @param hostname the host name of the targeted StatsD server
   * @param port     the port of the targeted StatsD server
   * @return a function that cached the result of the lookup
   * @throws Exception if the lookup fails, i.e. {@link UnknownHostException}
   */
    public static Callable<InetSocketAddress> staticAddressResolution(final String hostname, final int port) throws Exception {
        final InetSocketAddress address = volatileAddressResolution(hostname, port).call();
        return () -> address;
    }

    private static Callable<InetSocketAddress> staticStatsDAddressResolution(final String hostname, final int port) throws StatsDClientException {
        try {
            return staticAddressResolution(hostname, port);
        } catch (final Exception e) {
            throw new StatsDClientException("Failed to lookup StatsD host", e);
        }
    }
}
