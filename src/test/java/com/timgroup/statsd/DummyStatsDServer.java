
package com.timgroup.statsd;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


class DummyStatsDServer {
    private final BlockingQueue<String> messagesReceived = new LinkedBlockingQueue<>();
    private final DatagramSocket server;

    public DummyStatsDServer(int port) throws SocketException {
        server = new DatagramSocket(port);
        Thread thread = new Thread(() -> {
            while(!server.isClosed()) {
                try {
                    final DatagramPacket packet = new DatagramPacket(new byte[1500], 1500);
                    server.receive(packet);
                    for(String msg : new String(packet.getData(), NonBlockingStatsDClient.MESSAGE_CHARSET).split("\n")) {
                        messagesReceived.add(msg.trim());
                    }
                } catch (IOException ignored) {}
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public String nextMessage() throws InterruptedException{
        return messagesReceived.take();
    }

    public List<String> messagesReceived() {
        return new ArrayList<>(messagesReceived);
    }

    public void close() {
        server.close();
    }

    public void clear() {
        messagesReceived.clear();
    }
}
