package org.dna.netty_test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientPayloadSender {
    public static void main(String[] args) throws Exception {
        int numClients = 10;

        final List<Thread> clients = new ArrayList<>(numClients);
        for (int i = 0; i < numClients; i++) {
            clients.add(createClientRunnerThread("runner-" + i));
        }
        System.out.println("Created " + numClients + " clients");

        // start all clients
        clients.forEach(Thread::start);
        System.out.println("Started all clients");

        // join all
        for (Thread client : clients) {
            client.join();
        }
        System.out.println("Terminated all clients");

//        try {
//            runClient("127.0.0.1", 1234);
//        } catch (Exception ex) {
//            System.out.println("Terminated with exception");
//            ex.printStackTrace(System.out);
//        }
    }

    private static Thread createClientRunnerThread(String threadName) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    runClient("127.0.0.1", 1234);
                } catch (Exception e) {
                    System.out.println(getName() + " terminating with error");
                    throw new RuntimeException(e);
                }
            }
        };
        thread.setName(threadName);
        return thread;
    }

    protected static void runClient(String host, int port) throws IOException, InterruptedException {
        Socket socket = new Socket(host, port);
        OutputStream os = socket.getOutputStream();
        byte[] payload = generatePayload(8 * 1024);
        while (true) {
            Thread.sleep(1);
            os.write(payload);
        }
    }

    private static byte[] generatePayload(int payloadSize) {
        byte[] payload = new byte[payloadSize];
        Arrays.fill(payload, (byte) 'A');
        return payload;
    }
}
