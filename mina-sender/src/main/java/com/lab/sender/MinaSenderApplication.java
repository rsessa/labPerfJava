package com.lab.sender;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter; // Usaremos este directamente
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Arrays;

public class MinaSenderApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaSenderApplication.class);
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int TOTAL_BYTES_TO_SEND = 82178160;
    private static final long CONNECT_TIMEOUT = 30000;
    private static final int CHUNK_SIZE = 65536; 
    private static final long CHUNK_WRITE_TIMEOUT = 25000;

    public static void main(String[] args) {
        LOGGER.info("🚀 Cliente MINA (Chunks SÚPER Simplificado). Chunks: {} bytes, Timeout/chunk: {}ms", CHUNK_SIZE, CHUNK_WRITE_TIMEOUT);

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);
        connector.getFilterChain().addLast("logger", new LoggingFilter());

        // *** USAREMOS UN IoHandlerAdapter VACÍO O CON LOGS MÍNIMOS ***
        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void sessionOpened(IoSession session) {
                LOGGER.info("MINA IoHandler - Sesión ABIERTA: {}", session.getId());
            }
            @Override
            public void sessionClosed(IoSession session) {
                LOGGER.info("MINA IoHandler - Sesión CERRADA: {}", session.getId());
            }
            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                LOGGER.error("MINA IoHandler - EXCEPCIÓN: {} en sesión {}", cause.getMessage(), session.getId(), cause);
                session.closeNow(); // Importante cerrar si hay excepción no manejada
            }
            // No necesitamos messageSent ni messageReceived para este test de envío
        });

        IoSession session = null;
        long totalBytesSuccessfullyWritten = 0;
        long startTimeTotal = 0; // Para medir el tiempo total de envío
        int chunkCounter = 0;

        try {
            LOGGER.info("CLIENTE MAIN - Conectando a {}:{}...", HOST, PORT);
            ConnectFuture connectFuture = connector.connect(new InetSocketAddress(HOST, PORT));
            connectFuture.awaitUninterruptibly();

            if (!connectFuture.isConnected()) {
                LOGGER.error("CLIENTE MAIN - 🔥 No se pudo conectar.");
                return;
            }
            session = connectFuture.getSession();
            LOGGER.info("CLIENTE MAIN - 🔗 Conectado. Sesión ID: {}. Preparando envío.", session.getId());

            byte[] fullDataArray = new byte[TOTAL_BYTES_TO_SEND];
            // Arrays.fill(fullDataArray, (byte) 'S');

            startTimeTotal = System.nanoTime(); // Inicia cronómetro general

            for (int offset = 0; offset < TOTAL_BYTES_TO_SEND; offset += CHUNK_SIZE) {
                if (!session.isConnected() || session.isClosing()) {
                    LOGGER.warn("CLIENTE MAIN - Sesión cerrada o cerrándose antes de enviar chunk #{}. Bytes enviados: {}", chunkCounter + 1, totalBytesSuccessfullyWritten);
                    break;
                }

                int length = Math.min(CHUNK_SIZE, TOTAL_BYTES_TO_SEND - offset);
                IoBuffer chunkBuffer = IoBuffer.allocate(length);
                chunkBuffer.put(fullDataArray, offset, length);
                chunkBuffer.flip();
                chunkCounter++;
                
                // LOGGER.info("CLIENTE MAIN - Intentando enviar chunk #{} ({} bytes)...", chunkCounter, length); // Reducir logs
                WriteFuture writeFuture = session.write(chunkBuffer);

                if (!writeFuture.awaitUninterruptibly(CHUNK_WRITE_TIMEOUT)) {
                     LOGGER.error("CLIENTE MAIN - 🔥 TIMEOUT ({}ms) enviando chunk #{}", CHUNK_WRITE_TIMEOUT, chunkCounter);
                     throw new Exception("Timeout en escritura de chunk #" + chunkCounter);
                }
                if(!writeFuture.isWritten()){
                    LOGGER.error("CLIENTE MAIN - 🔥 FALLÓ escritura del chunk #{}", chunkCounter);
                    if(writeFuture.getException() != null) {
                        LOGGER.error("   Excepción en WriteFuture para chunk #{}: ", chunkCounter, writeFuture.getException());
                    }
                    throw new Exception("Fallo en escritura de chunk #" + chunkCounter);
                }
                totalBytesSuccessfullyWritten += length;
                if (chunkCounter % 100 == 0) { // Loguear progreso
                    LOGGER.info("CLIENTE MAIN - Chunk #{} enviado. Total escrito: {} bytes", chunkCounter, totalBytesSuccessfullyWritten);
                }
            }

            long endTimeTotal = System.nanoTime();
            if (totalBytesSuccessfullyWritten > 0) { // Solo mostrar estadísticas si se envió algo
                long duration = endTimeTotal - startTimeTotal;
                double durationSeconds = duration / 1_000_000_000.0;
                double megabytes = totalBytesSuccessfullyWritten / (1024.0 * 1024.0);
                double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
                double mbitps = mbps * 8;

                LOGGER.info("-------------------------------------------------");
                LOGGER.info("📊 Envío en Chunks Completado:");
                LOGGER.info("   Chunks Enviados (intentados): {}", chunkCounter);
                LOGGER.info("   Bytes Totales Escritos (confirmados por WriteFuture): {}", totalBytesSuccessfullyWritten);
                LOGGER.info("   Tiempo Transcurrido Total: {} ms ({} s)", duration / 1_000_000, String.format("%.3f", durationSeconds));
                LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
                LOGGER.info("-------------------------------------------------");
            }


        } catch (Exception e) {
            LOGGER.error("CLIENTE MAIN - 🔥 Error en bucle de envío o conexión: {}", e.getMessage());
        } finally {
            if (session != null) {
                if (session.isConnected() || session.isClosing() ) {
                     LOGGER.info("CLIENTE MAIN - Finalizando. Solicitando cierre de sesión con flush...");
                     session.closeOnFlush().awaitUninterruptibly(5000);
                } else {
                    LOGGER.info("CLIENTE MAIN - Finalizando. Sesión ya estaba cerrada.");
                }
            }
            if (connector != null && !connector.isDisposed()) {
                LOGGER.info("CLIENTE MAIN - Disponiendo del conector...");
                connector.dispose(true);
            }
            LOGGER.info("🧹 Cliente finalizado.");
        }
    }
}