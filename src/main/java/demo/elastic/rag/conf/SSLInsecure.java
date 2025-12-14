package demo.elastic.rag.conf;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class SSLInsecure {

    private static final String CONNECTION_PROVIDER_NAME = "customConnectionProvider";
    private static final int MAX_CONNECTIONS = 10;
    private static final int ACQUIRE_TIMEOUT = 60;
    private static final int CONNECT_TIMEOUT_MILLIS = 6000;
    private static final int READ_WRITE_TIMEOUT_SECONDS = 60;
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_PENDING_ACQUIRES = 5;

    private static final Iterable<String> allowedCiphers = List.of("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384", "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384");

    @Primary
    @Bean(name = "webclientwithnossl")
    WebClient.Builder webClientBuilderStage() throws SSLException {
        ConnectionProvider connectionProvider = buildConnectionProvider();
        SslContext sslContext = buildSslContext();
        HttpClient httpClient = buildHttpClient(connectionProvider, sslContext);

        return buildWebClient(httpClient);
    }

    private ConnectionProvider buildConnectionProvider() {
        return ConnectionProvider.builder(CONNECTION_PROVIDER_NAME)
                .maxConnections(MAX_CONNECTIONS)
                .maxLifeTime(Duration.ofSeconds(TIMEOUT_SECONDS))
                .pendingAcquireTimeout(Duration.ofMillis(ACQUIRE_TIMEOUT))
                .pendingAcquireMaxCount(MAX_PENDING_ACQUIRES)
                .maxIdleTime(Duration.ofSeconds(ACQUIRE_TIMEOUT))
                .lifo()
                .build();
    }

    private SslContext buildSslContext() throws SSLException {
        return SslContextBuilder.forClient()
                .protocols("SSLv3","TLSv1","TLSv1.1","TLSv1.2")
                .ciphers(allowedCiphers)
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    }

    private HttpClient buildHttpClient(ConnectionProvider connectionProvider, SslContext sslContext) {
        return HttpClient.create(connectionProvider)
                .compress(true)
                .followRedirect(true)
                .resolver(DefaultAddressResolverGroup.INSTANCE)
                .secure(t -> t.sslContext(sslContext).handshakeTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)))
                .keepAlive(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .doOnConnected(connection -> {
                    connection.addHandlerLast(new ReadTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS));
                    connection.addHandlerLast(new WriteTimeoutHandler(READ_WRITE_TIMEOUT_SECONDS));
                })
                .option(UnixChannelOption.SO_KEEPALIVE, true);
    }

    private WebClient.Builder buildWebClient(HttpClient httpClient) {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.toString())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}