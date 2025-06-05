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

    private static final Logger CLASS_LOGGER = LoggerFactory.getLogger(TcpServerConfig.class);
    private static final Logger HANDLER_LOGGER = LoggerFactory.getLogger(TcpServerConfig.class.getName() + ".MessageHandler");

    private static final int PORT = 12345;
    private static final int BUFFER_SIZE = 131072;
    private static final long EXPECTED_TOTAL_BYTES = 82178160L;

    private final AtomicLong currentTotalBytesReceived = new AtomicLong(0);
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private volatile long startTimeReception = 0;

    @Bean
    public AbstractServerConnectionFactory serverConnectionFactory() {
        TcpNioServerConnectionFactory factory = new TcpNioServerConnectionFactory(PORT);
        
        ByteArrayRawSerializer serializer = new ByteArrayRawSerializer();
        factory.setSerializer(serializer);
        factory.setDeserializer(serializer);
        
        factory.setSoTimeout(60000); 
        factory.setSoReceiveBufferSize(BUFFER_SIZE);
        factory.setSoSendBufferSize(BUFFER_SIZE);

        // Quitado el TaskExecutor para simplificar
        // SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("tcp-server-io-");
        // taskExecutor.setConcurrencyLimit(10); 
        // factory.setTaskExecutor(taskExecutor);

        CLASS_LOGGER.info("-> Fábrica de Conexiones TCP Creada en Puerto {} (Raw Serializer)", PORT);
        CLASS_LOGGER.info("   -> Búfer Solicitado Envío/Recepción: {} bytes", BUFFER_SIZE);
        CLASS_LOGGER.info("   -> SO_TIMEOUT: {} ms", factory.getSoTimeout());
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
        CLASS_LOGGER.info("-> Adaptador TCP Receptor Creado.");
        return adapter;
    }

    @ServiceActivator(inputChannel = "inboundTcpChannel")
    public void handleMessage(Message<byte[]> message) {
        byte[] chunk = message.getPayload();
        long bytesInThisChunk = chunk.length;
        // Este es el log más importante ahora:
        HANDLER_LOGGER.info(">>> SERVER: handleMessage LLAMADO con un chunk de {} bytes", bytesInThisChunk);

        if (bytesInThisChunk == 0 && currentTotalBytesReceived.get() == 0 && startTimeReception == 0) {
            HANDLER_LOGGER.warn(">>> SERVER: Recibido chunk vacío inicial. Ignorando para estadísticas.");
            return; 
        }
        
        if (startTimeReception == 0 && bytesInThisChunk > 0) { 
            startTimeReception = System.nanoTime();
            HANDLER_LOGGER.info("⏱️  Recepción de Bloque Iniciada...");
        }

        long newTotal = currentTotalBytesReceived.addAndGet(bytesInThisChunk);
        int currentChunkCount = chunkCount.incrementAndGet();

        // No loguear progreso por chunk para reducir la carga de logs
        // if (bytesInThisChunk > 0 && currentChunkCount % 500 == 0 && newTotal < EXPECTED_TOTAL_BYTES) {
        //     HANDLER_LOGGER.info("   ... {} bytes recibidos en este chunk (chunk #{}), Total acumulado: {} bytes.",
        //         bytesInThisChunk, currentChunkCount, newTotal);
        // }

        boolean allBytesExpected = (newTotal >= EXPECTED_TOTAL_BYTES);
        boolean eofDetected = (bytesInThisChunk == 0 && currentTotalBytesReceived.get() > 0 && startTimeReception > 0 && currentChunkCount > 1);

        if (allBytesExpected || eofDetected) {
            // ... (resto de la lógica de estadísticas y reset como estaba) ...
            if (eofDetected && !allBytesExpected) {
                HANDLER_LOGGER.warn("   Finalizando por chunk vacío (EOF?) antes de alcanzar {} bytes esperados. Total recibido: {}", EXPECTED_TOTAL_BYTES, newTotal);
            }
            long endTimeReception = System.nanoTime();
            if (startTimeReception == 0) { 
                 HANDLER_LOGGER.error("Error: Finalizando estadísticas pero startTimeReception es 0.");
                 currentTotalBytesReceived.set(0);
                 chunkCount.set(0);
                 return;
            }
            long duration = endTimeReception - startTimeReception;
            long finalTotalBytes = currentTotalBytesReceived.get(); 
            double durationSeconds = duration / 1_000_000_000.0;
            double megabytes = finalTotalBytes / (1024.0 * 1024.0);
            double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
            double mbitps = mbps * 8;
            int effectiveChunkCount = eofDetected ? currentChunkCount -1 : currentChunkCount;
            if(effectiveChunkCount <= 0) effectiveChunkCount = 1; 
            double averageBytesPerChunk = (double) finalTotalBytes / effectiveChunkCount;

            HANDLER_LOGGER.info("-------------------------------------------------");
            HANDLER_LOGGER.info("📊 Recepción de Bloque Completada:");
            HANDLER_LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
            HANDLER_LOGGER.info("--- ESTADÍSTICAS REQUERIDAS (Bloque) ---");
            HANDLER_LOGGER.info("   Total sum of read bytes: {}", finalTotalBytes);
            HANDLER_LOGGER.info("   Number of read buffer (chunks): {}", currentChunkCount); 
            HANDLER_LOGGER.info("   average bytes per chunk (datos): {}", String.format("%.2f", averageBytesPerChunk));
            HANDLER_LOGGER.info("-------------------------------------------------");

            startTimeReception = 0;
            currentTotalBytesReceived.set(0);
            chunkCount.set(0);
        }
    }
}