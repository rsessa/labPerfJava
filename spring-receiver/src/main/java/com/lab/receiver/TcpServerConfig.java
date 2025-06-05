package com.lab.receiver;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNioServerConnectionFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayRawSerializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

@Configuration
public class TcpServerConfig {

    private static final Logger LOGGER_HANDLER = LoggerFactory.getLogger(TcpServerConfig.class.getName() + ".MessageCountingHandler");
    private static final int PORT = 12345;
    private static final int BUFFER_SIZE = 131072;
    private static final long EXPECTED_TOTAL_BYTES = 82178160L;

    private final AtomicLong currentTotalBytesReceived = new AtomicLong(0);
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private volatile long startTimeReception = 0;

    @Bean
    public AbstractServerConnectionFactory serverConnectionFactory() {
        TcpNioServerConnectionFactory factory = new TcpNioServerConnectionFactory(PORT);
        factory.setSerializer(new ByteArrayRawSerializer());
        factory.setDeserializer(new ByteArrayRawSerializer());
        factory.setSoTimeout(30000);
        factory.setSoReceiveBufferSize(BUFFER_SIZE);
        factory.setSoSendBufferSize(BUFFER_SIZE);
        System.out.println("-> Fábrica de Conexiones TCP Creada en Puerto " + PORT + " (Raw Serializer)");
        System.out.println("   -> Búfer Solicitado Envío/Recepción: " + BUFFER_SIZE + " bytes");
        return factory;
    }

    @Bean
    public MessageChannel inboundTcpChannel() {
        return new DirectChannel();
    }

    @Bean
    public TcpReceivingChannelAdapter inboundAdapter(AbstractServerConnectionFactory serverConnectionFactory, MessageChannel inboundTcpChannel) {
        TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
        adapter.setConnectionFactory(serverConnectionFactory);
        adapter.setOutputChannel(inboundTcpChannel);
        return adapter;
    }

    @ServiceActivator(inputChannel = "inboundTcpChannel")
    public void handleMessage(Message<byte[]> message) {
        // *** CAMBIO: Log al inicio del método ***
        LOGGER_HANDLER.info(">>> SERVER: handleMessage LLAMADO con un chunk de {} bytes", message.getPayload().length);

        if (startTimeReception == 0) {
            startTimeReception = System.nanoTime();
            LOGGER_HANDLER.info("⏱️  Recepción de Bloque Iniciada...");
        }

        byte[] chunk = message.getPayload();
        long bytesInThisChunk = chunk.length;
        long newTotal = currentTotalBytesReceived.addAndGet(bytesInThisChunk);
        int currentChunkCount = chunkCount.incrementAndGet();

        if (currentChunkCount % 500 == 0 && newTotal < EXPECTED_TOTAL_BYTES) {
            LOGGER_HANDLER.info("   ... {} bytes recibidos en este chunk (chunk #{}), Total acumulado: {} bytes.",
                bytesInThisChunk, currentChunkCount, newTotal);
        }

        if (newTotal >= EXPECTED_TOTAL_BYTES) {
            long endTimeReception = System.nanoTime();
            long duration = endTimeReception - startTimeReception;
            long finalTotalBytes = Math.min(newTotal, EXPECTED_TOTAL_BYTES);

            double durationSeconds = duration / 1_000_000_000.0;
            double megabytes = finalTotalBytes / (1024.0 * 1024.0);
            double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
            double mbitps = mbps * 8;
            double averageBytesPerChunk = (currentChunkCount > 0) ? (double) finalTotalBytes / currentChunkCount : 0;

            LOGGER_HANDLER.info("-------------------------------------------------");
            LOGGER_HANDLER.info("📊 Recepción de Bloque Completada:");
            LOGGER_HANDLER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
            LOGGER_HANDLER.info("--- ESTADÍSTICAS REQUERIDAS (Bloque) ---");
            LOGGER_HANDLER.info("   Total sum of read bytes: {}", finalTotalBytes);
            LOGGER_HANDLER.info("   Number of read buffer (chunks): {}", currentChunkCount);
            LOGGER_HANDLER.info("   average bytes per chunk: {}", String.format("%.2f", averageBytesPerChunk));
            LOGGER_HANDLER.info("-------------------------------------------------");

            startTimeReception = 0;
            currentTotalBytesReceived.set(0);
            chunkCount.set(0);
        }
    }
}