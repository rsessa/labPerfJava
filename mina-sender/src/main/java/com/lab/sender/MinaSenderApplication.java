package com.lab.sender;

import java.net.InetSocketAddress;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinaSenderApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaSenderApplication.class);
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int TOTAL_BYTES_TO_SEND = 82178160;
    private static final long CONNECT_TIMEOUT = 30000;
    private static final int CHUNK_SIZE = 65536; // 64 KB por chunk
    private static final long CHUNK_WRITE_TIMEOUT = 15000; // 15 segundos de timeout por chunk

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA (Enviando en chunks de {} bytes)...", CHUNK_SIZE);

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);
        connector.getFilterChain().addLast("logger", new LoggingFilter());

        // Usamos el MinaClientHandler que nos proporcionaste
        connector.setHandler(new MinaClientHandler("Mensaje inicial del cliente MINA"));

        IoSession session = null;
        try {
            LOGGER.info("🔌 Intentando conectar a {}:{}...", HOST, PORT);
            ConnectFuture connectFuture = connector.connect(new InetSocketAddress(HOST, PORT));
            connectFuture.awaitUninterruptibly();

            if (!connectFuture.isConnected()) {
                LOGGER.error("🔥 No se pudo conectar al servidor.");
                return;
            }

            session = connectFuture.getSession();
            LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Preparando datos para enviar en chunks...", session.getId());

            byte[] fullDataArray = new byte[TOTAL_BYTES_TO_SEND];
            // Opcional: Rellenar el array para propósitos de prueba, ej: Arrays.fill(fullDataArray, (byte) 'X');

            long totalBytesSuccessfullyWritten = 0;
            long startTime = System.nanoTime();
            int chunkCounter = 0;

            for (int offset = 0; offset < TOTAL_BYTES_TO_SEND; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, TOTAL_BYTES_TO_SEND - offset);
                IoBuffer chunkBuffer = IoBuffer.allocate(length);
                chunkBuffer.put(fullDataArray, offset, length);
                chunkBuffer.flip();
                chunkCounter++;

                if (chunkCounter % 100 == 0) { // Loguear progreso cada 100 chunks
                    LOGGER.info("   Enviando chunk #{} ({} bytes)...", chunkCounter, length);
                }

                WriteFuture writeFuture = session.write(chunkBuffer);
                if (!writeFuture.awaitUninterruptibly(CHUNK_WRITE_TIMEOUT)) {
                     LOGGER.error("🔥 Timeout ({ }ms) enviando chunk #{}", CHUNK_WRITE_TIMEOUT, chunkCounter);
                     throw new Exception("Timeout en escritura de chunk #" + chunkCounter);
                }
                if(!writeFuture.isWritten()){
                    LOGGER.error("🔥 Falló la escritura del chunk #{}", chunkCounter);
                    if(writeFuture.getException() != null) {
                        LOGGER.error("   Excepción en WriteFuture para chunk #{}: ", chunkCounter, writeFuture.getException());
                    }
                    throw new Exception("Fallo en escritura de chunk #" + chunkCounter);
                }
                totalBytesSuccessfullyWritten += length;
            }

            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            double durationSeconds = duration / 1_000_000_000.0;
            double megabytes = totalBytesSuccessfullyWritten / (1024.0 * 1024.0);
            double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
            double mbitps = mbps * 8;

            LOGGER.info("-------------------------------------------------");
            LOGGER.info("📊 Envío en Chunks Completado:");
            LOGGER.info("   Chunks Enviados: {}", chunkCounter);
            LOGGER.info("   Bytes Totales Realmente Escritos (según futures): {}", totalBytesSuccessfullyWritten);
            LOGGER.info("   Tiempo Transcurrido: {} ms ({} s)", duration / 1_000_000, String.format("%.3f", durationSeconds));
            LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
            LOGGER.info("-------------------------------------------------");

            if (session != null && session.isConnected()) {
                LOGGER.info("Cliente: Envío de todos los chunks completado. Solicitando cierre con flush...");
                session.closeOnFlush().awaitUninterruptibly(5000); 
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error general en el cliente: {}", e.getMessage(), e);
            if (session != null && session.isActive()) {
                session.closeNow();
            }
        } finally {
            if (connector != null && !connector.isDisposed()) {
                LOGGER.info("Cliente: Limpiando y disponiendo del conector...");
                connector.dispose(true);
            }
            LOGGER.info("🧹 Cliente finalizado.");
        }
    }
}