package com.zhwxp.sample.spring.boot.tcp.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.zhwxp.sample.spring.boot.tcp.client.gateway.TcpClientGateway;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the client over a real TCP connection: a stub server accepts the connection, reads the
 * request and writes a reply, and the reply is asserted to come back out of the gateway.
 *
 * <p>The stub listens on an OS-assigned port, which keeps the test independent of a locally running
 * server. The scheduled job is switched off so the only traffic on that port is what the test sends.
 */
@SpringBootTest(properties = "tcp.client.scheduler.enabled=false")
class TcpClientIntegrationTests {

    private static final int SOCKET_TIMEOUT_MILLIS = 5_000;

    private static final long REQUEST_TIMEOUT_SECONDS = 10L;

    private static ServerSocket serverSocket;

    private ExecutorService executor;

    /**
     * Opens the stub server before the application context is created, so the client can be pointed
     * at the port the OS handed out.
     */
    @DynamicPropertySource
    static void tcpServerProperties(DynamicPropertyRegistry registry) {
        registry.add("tcp.server.host", () -> InetAddress.getLoopbackAddress().getHostAddress());
        registry.add("tcp.server.port", () -> serverSocket.getLocalPort());
    }

    @BeforeAll
    static void startStubServer() throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serverSocket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
    }

    @AfterAll
    static void stopStubServer() throws IOException {
        serverSocket.close();
    }

    @Autowired
    private TcpClientGateway tcpClientGateway;

    @BeforeEach
    void startExecutor() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    @Test
    void sendsARequestAndReturnsTheReply() throws Exception {
        Future<List<String>> requests = serve(1);

        byte[] reply = tcpClientGateway.send("Hello".getBytes(UTF_8));

        assertThat(new String(reply, UTF_8)).isEqualTo("Message \"Hello\" is processed");
        assertThat(await(requests)).containsExactly("Hello");
    }

    @Test
    void reusesAPooledConnectionForFurtherRequests() throws Exception {
        Future<List<String>> requests = serve(2);

        byte[] first = tcpClientGateway.send("first".getBytes(UTF_8));
        byte[] second = tcpClientGateway.send("second".getBytes(UTF_8));

        assertThat(new String(first, UTF_8)).isEqualTo("Message \"first\" is processed");
        assertThat(new String(second, UTF_8)).isEqualTo("Message \"second\" is processed");
        // Both exchanges were served on a single accepted connection, so the pooled connection was
        // handed back and reused instead of a second one being opened.
        assertThat(await(requests)).containsExactly("first", "second");
    }

    /**
     * Accepts one connection and answers {@code count} requests on it the way
     * spring-boot-tcp-server-sample does, then returns the payloads that were received.
     */
    private Future<List<String>> serve(int count) {
        return executor.submit(() -> {
            List<String> requests = new ArrayList<>();
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
                OutputStream outputStream = socket.getOutputStream();
                for (int i = 0; i < count; i++) {
                    String request = reader.readLine();
                    requests.add(request);
                    outputStream.write(reply(request));
                    outputStream.flush();
                }
            }
            return requests;
        });
    }

    /**
     * The connection factory uses the default {@code ByteArrayCrLfSerializer}, so the client only
     * treats the reply as complete once it sees CRLF — and reading requests with
     * {@link BufferedReader#readLine()} above only works because the client appends it as well.
     */
    private byte[] reply(String request) {
        return ("Message \"" + request + "\" is processed\r\n").getBytes(UTF_8);
    }

    private List<String> await(Future<List<String>> requests) throws Exception {
        return requests.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

}
