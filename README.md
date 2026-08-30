# Spring Boot TCP Client Sample

A minimal TCP client built with Spring Boot and Spring Integration IP. A scheduled job asks a messaging gateway to send a message, the message goes out over a pooled TCP connection, and the server's reply comes back as the return value of the same call (request/reply via `TcpOutboundGateway`).

The matching server sample is [spring-boot-tcp-server-sample](https://github.com/zhwxp/spring-boot-tcp-server-sample).

## Requirements

* JDK 8 or later
* No Maven installation required — the bundled wrapper (`mvnw` / `mvnw.cmd`) provisions Maven 3.9.16 on first use
* A TCP server on `tcp.server.host:tcp.server.port` that answers with a CRLF-terminated line

Built with Spring Boot 2.7.18 — the last release line that supports Java 8. Spring Boot 3.x requires Java 17.

## Build

```bash
./mvnw clean package
```

```powershell
.\mvnw.cmd clean package
```

## Run

Start a server first — [spring-boot-tcp-server-sample](https://github.com/zhwxp/spring-boot-tcp-server-sample) listens on port 10001 and needs no configuration to match the defaults here. Then:

```bash
java -jar target/spring-boot-tcp-client-sample-0.0.1-SNAPSHOT.jar
```

Or during development:

```bash
./mvnw spring-boot:run
```

The client logs one request/reply pair per second:

```
Send message: 2026-08-30T16:36:45.959
Receive response: Message "2026-08-30T16:36:45.959" is processed
```

Without a server on the other end every attempt fails with `Connection refused`, logged by the scheduler's error handler.

## Wire protocol

The connection factory uses Spring Integration's default `ByteArrayCrLfSerializer`, so **the CRLF (`\r\n`) terminator is added to every outgoing message and expected on every reply**. The payload passed to the gateway must not contain the terminator itself. A reply without it leaves the client waiting until the read times out.

To try the protocol by hand, stand up a listener that echoes a CRLF-terminated line and point the client at it:

```powershell
$listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 10001)
$listener.Start()
$socket = $listener.AcceptTcpClient()
$stream = $socket.GetStream()
$buffer = New-Object byte[] 1024
$read = $stream.Read($buffer, 0, $buffer.Length)
[System.Text.Encoding]::UTF8.GetString($buffer, 0, $read)
$reply = [System.Text.Encoding]::UTF8.GetBytes("Ack`r`n")
$stream.Write($reply, 0, $reply.Length)
$socket.Close()
$listener.Stop()
```

## Configuration

`src/main/resources/application.properties`:

| Property | Default | Description |
| --- | --- | --- |
| `tcp.server.host` | `localhost` | Host of the TCP server to send to |
| `tcp.server.port` | `10001` | Port of the TCP server to send to |
| `tcp.client.connection.poolSize` | `50` | Maximum number of pooled connections |

Override at startup when needed:

```bash
java -jar target/spring-boot-tcp-client-sample-0.0.1-SNAPSHOT.jar --tcp.server.host=192.168.1.10 --tcp.server.port=20001
```

## How it works

```
MessageJobScheduler → MessageService → TcpClientGateway → outboundChannel
                                                               ↓
                                     CachingClientConnectionFactory → TcpOutboundGateway → TCP server
                                                               ↑
MessageService ←──────────────── reply ────────────────────────┘
```

| Class | Responsibility |
| --- | --- |
| `Application` | Spring Boot entry point |
| `config/TcpClientConfig` | NIO client connection factory with pooling, outbound channel, outbound gateway |
| `gateway/TcpClientGateway` | `@MessagingGateway` on `outboundChannel`; `send` blocks until the reply arrives |
| `scheduler/MessageJobScheduler` | `@Scheduled` job that triggers one send per second |
| `service/MessageService` | Message sending contract |
| `service/impl/MessageServiceImpl` | Sends a timestamp and logs the reply |

`CachingClientConnectionFactory` wraps the NIO factory in a connection pool, so concurrent senders each get their own connection and replies cannot be mixed up. To change the framing (length header, custom delimiter, fixed length), set a serializer/deserializer on the connection factory in `TcpClientConfig`. To change what is sent, edit `MessageServiceImpl`.

## Note

The client sends in plain text and performs no authentication or server verification — it is a sample, not something to point at an untrusted network.

## Built With

* [Maven](https://maven.apache.org) — dependency management
* [Spring Boot](https://spring.io/projects/spring-boot) — application framework, executable jar
* [Spring Integration IP](https://docs.spring.io/spring-integration/reference/ip.html) — TCP outbound gateway
