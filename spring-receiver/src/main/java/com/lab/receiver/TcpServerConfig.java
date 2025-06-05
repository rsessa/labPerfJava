package com.lab.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor; // Para el executor
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNioServerConnectionFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayRawSerializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        // No es necesario setMaxMessageSize con ByteArrayRawSerializer para este escenario,
        // ya que debería emitir datos a medida que llegan o al cierre.
        // serializer.setMaxMessageSize(Integer.MAX_VALUE); 

        factory.setSerializer(serializer);
        factory.setDeserializer(serializer);
        
        factory.setSoTimeout(60000); // Aumentado a 60s para inactividad del socket
        factory.setSoReceiveBufferSize(BUFFER_SIZE);
        factory.setSoSendBufferSize(BUFFER_SIZE);

        // Usar un task executor dedicado puede ayudar con el manejo de conexiones
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("tcp-server-io-"); // Nombre para el hilo
        taskExecutor.setConcurrencyLimit(10); // Número de hilos para manejar conexiones/IO
        factory.setTaskExecutor(taskExecutor);

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
        // La concurrencia se maneja mejor con el TaskExecutor en la ConnectionFactory
        // adapter.setPoolSize(10); 
        CLASS_LOGGER.info("-> Adaptador TCP Receptor Creado.");
        return adapter;
    }

    @ServiceActivator(inputChannel = "inboundTcpChannel")
    public void handleMessage(Message<byte[]> message) {
        byte[] chunk = message.getPayload();
        long bytesInThisChunk = chunk.length;
        HANDLER_LOGGER.info(">>> SERVER: handleMessage LLAMADO con un chunk de {} bytes", bytesInThisChunk);

        // Si el chunk está vacío, podría ser un EOF o un problema.
        // No iniciamos el cronómetro ni lo contamos como datos si es la primera vez y está vacío.
        if (bytesInThisChunk == 0 && currentTotalBytesReceived.get() == 0 && startTimeReception == 0) {
            HANDLER_LOGGER.warn(">>> SERVER: Recibido chunk vacío inicial. Ignorando para estadísticas.");
            return; // No procesar chunks vacíos si no hemos empezado a recibir
        }
        
        if (startTimeReception == 0) { // Inicia el cronómetro con el primer chunk CON datos
            startTimeReception = System.nanoTime();
            HANDLER_LOGGER.info("⏱️  Recepción de Bloque Iniciada...");
        }

        long newTotal = currentTotalBytesReceived.addAndGet(bytesInThisChunk);
        int currentChunkCount = chunkCount.incrementAndGet();

        // Loguea progreso, pero no si es un chunk vacío al final
        if (bytesInThisChunk > 0 && currentChunkCount % 500 == 0 && newTotal < EXPECTED_TOTAL_BYTES) {
            HANDLER_LOGGER.info("   ... {} bytes recibidos en este chunk (chunk #{}), Total acumulado: {} bytes.",
                bytesInThisChunk, currentChunkCount, newTotal);
        }

        // Comprobamos si hemos recibido todos los datos esperados O si es un chunk vacío (posible EOF)
        // y ya habíamos empezado a recibir datos.
        boolean allBytesExpected = (newTotal >= EXPECTED_TOTAL_BYTES);
        boolean eofDetected = (bytesInThisChunk == 0 && currentTotalBytesReceived.get() > 0 && startTimeReception > 0);

        if (allBytesExpected || eofDetected) {
            if (eofDetected && !allBytesExpected) {
                HANDLER_LOGGER.warn("   Finalizando por chunk vacío (EOF?) antes de alcanzar {} bytes esperados. Total recibido: {}", EXPECTED_TOTAL_BYTES, newTotal);
            }

            long endTimeReception = System.nanoTime();
            // Asegurarse que startTimeReception no es 0 para evitar división por cero o NaN
            if (startTimeReception == 0) { 
                 HANDLER_LOGGER.error("Error: Finalizando estadísticas pero startTimeReception es 0. No se recibieron datos válidos para cronometrar.");
                 // Resetear por si acaso, aunque esto no debería pasar si el primer chunk vacío se ignora
                 currentTotalBytesReceived.set(0);
                 chunkCount.set(0);
                 return;
            }

            long duration = endTimeReception - startTimeReception;
            long finalTotalBytes = currentTotalBytesReceived.get(); 

            double durationSeconds = duration / 1_000_000_000.0;
            double megabytes = finalTotalBytes / (1024.0 * 1024.0);
            double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0; // Evitar división por cero
            double mbitps = mbps * 8;
            double averageBytesPerChunk = (currentChunkCount > 0) ? (double) finalTotalBytes / currentChunkCount : 0;

            HANDLER_LOGGER.info("-------------------------------------------------");
            HANDLER_LOGGER.info("📊 Recepción de Bloque Completada:");
            HANDLER_LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
            HANDLER_LOGGER.info("--- ESTADÍSTICAS REQUERIDAS (Bloque) ---");
            HANDLER_LOGGER.info("   Total sum of read bytes: {}", finalTotalBytes);
            HANDLER_LOGGER.info("   Number of read buffer (chunks): {}", currentChunkCount);
            HANDLER_LOGGER.info("   average bytes per chunk: {}", String.format("%.2f", averageBytesPerChunk));
            HANDLER_LOGGER.info("-------------------------------------------------");

            // Reinicia contadores para la próxima conexión/prueba
            startTimeReception = 0;
            currentTotalBytesReceived.set(0);
            chunkCount.set(0);
        }
    }
}