package com.lab.receiver;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
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
    private static final int SPRING_BUFFER_SIZE = 131072; // Renombrado para claridad
    private static final long EXPECTED_TOTAL_BYTES = 82178160L;
    // Tamaño máximo de mensaje para que ByteArrayRawSerializer emita un Message.
    // Debe ser lo suficientemente grande para un chunk de MINA, o un poco más.
    private static final int MAX_MESSAGE_SIZE_SERIALIZER = 131072; // Ej: 128 KB, el doble del chunk de MINA

    private final AtomicLong currentTotalBytesReceived = new AtomicLong(0);
    private final AtomicInteger individualChunkCounter = new AtomicInteger(0); // Renombrado para claridad
    private volatile long startTimeReception = 0;

    @Bean
    public AbstractServerConnectionFactory serverConnectionFactory() {
        TcpNioServerConnectionFactory factory = new TcpNioServerConnectionFactory(PORT);
        
        ByteArrayRawSerializer serializer = new ByteArrayRawSerializer();
        // *** CAMBIO IMPORTANTE: Establecer un maxMessageSize razonable ***
        serializer.setMaxMessageSize(MAX_MESSAGE_SIZE_SERIALIZER); 

        factory.setSerializer(serializer);
        factory.setDeserializer(serializer);
        
        factory.setSoTimeout(60000); 
        factory.setSoReceiveBufferSize(SPRING_BUFFER_SIZE);
        factory.setSoSendBufferSize(SPRING_BUFFER_SIZE);

        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("tcp-server-io-");
        taskExecutor.setConcurrencyLimit(10); 
        factory.setTaskExecutor(taskExecutor);

        CLASS_LOGGER.info("-> Fábrica de Conexiones TCP Creada en Puerto {} (Raw Serializer)", PORT);
        CLASS_LOGGER.info("   -> Búfer Solicitado Envío/Recepción: {} bytes", SPRING_BUFFER_SIZE);
        CLASS_LOGGER.info("   -> SO_TIMEOUT: {} ms", factory.getSoTimeout());
        CLASS_LOGGER.info("   -> RawSerializer MaxMessageSize: {} bytes", MAX_MESSAGE_SIZE_SERIALIZER);
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
        byte[] chunkPayload = message.getPayload(); // Renombrado para claridad
        long bytesInThisMessage = chunkPayload.length;
        HANDLER_LOGGER.info(">>> SERVER: handleMessage LLAMADO con un Mensaje de {} bytes", bytesInThisMessage);

        if (bytesInThisMessage == 0 && currentTotalBytesReceived.get() == 0 && startTimeReception == 0) {
            HANDLER_LOGGER.warn(">>> SERVER: Recibido mensaje vacío inicial. Ignorando para estadísticas.");
            return; 
        }
        
        if (startTimeReception == 0 && bytesInThisMessage > 0) { 
            startTimeReception = System.nanoTime();
            HANDLER_LOGGER.info("⏱️  Recepción de Bloque Iniciada...");
        }

        long newTotal = currentTotalBytesReceived.addAndGet(bytesInThisMessage);
        int currentMessageCount = individualChunkCounter.incrementAndGet(); // Usar el contador renombrado

        // Loguear progreso cada X mensajes recibidos por handleMessage
        if (bytesInThisMessage > 0 && currentMessageCount % 100 == 0 && newTotal < EXPECTED_TOTAL_BYTES) {
            HANDLER_LOGGER.info("   ... {} bytes recibidos en este Mensaje (Mensaje #{}), Total acumulado: {} bytes.",
                bytesInThisMessage, currentMessageCount, newTotal);
        }

        boolean allBytesExpected = (newTotal >= EXPECTED_TOTAL_BYTES);
        // EOF lo manejaremos por el cierre de conexión del cliente
        // No esperamos un mensaje de 0 bytes para finalizar si ya se alcanzó el total.

        if (allBytesExpected) {
            long endTimeReception = System.nanoTime();
            if (startTimeReception == 0) { 
                 HANDLER_LOGGER.error("Error: Finalizando estadísticas pero startTimeReception es 0.");
                 currentTotalBytesReceived.set(0);
                 individualChunkCounter.set(0);
                 return;
            }
            long duration = endTimeReception - startTimeReception;
            long finalTotalBytes = currentTotalBytesReceived.get(); 

            double durationSeconds = duration / 1_000_000_000.0;
            double megabytes = finalTotalBytes / (1024.0 * 1024.0);
            double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
            double mbitps = mbps * 8;
            double averageBytesPerMessage = (currentMessageCount > 0) ? (double) finalTotalBytes / currentMessageCount : 0;

            HANDLER_LOGGER.info("-------------------------------------------------");
            HANDLER_LOGGER.info("📊 Recepción de Bloque Completada:");
            HANDLER_LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
            HANDLER_LOGGER.info("--- ESTADÍSTICAS REQUERIDAS (Bloque) ---");
            HANDLER_LOGGER.info("   Total sum of read bytes: {}", finalTotalBytes);
            HANDLER_LOGGER.info("   Number of messages processed: {}", currentMessageCount); 
            HANDLER_LOGGER.info("   average bytes per message: {}", String.format("%.2f", averageBytesPerMessage));
            HANDLER_LOGGER.info("-------------------------------------------------");

            startTimeReception = 0;
            currentTotalBytesReceived.set(0);
            individualChunkCounter.set(0);
        }
    }
}