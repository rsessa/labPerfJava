package com.lab.sender;

import java.net.InetSocketAddress;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
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
    private static final long WRITE_TIMEOUT = 90000;

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA (v. depuración envío con timeouts extendidos)...");

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);
        connector.getFilterChain().addLast("logger", new LoggingFilter());

        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void sessionOpened(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ℹ️ Sesión MINA abierta (ID: {}).", session.getId());
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                MinaSenderApplication.LOGGER.error("CLIENT HANDLER - ❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
                session.closeNow(); // Usar closeNow() en lugar de close(boolean)
            }

            @Override
            public void sessionClosed(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - 🚪 Sesión MINA cerrada (ID: {}).", session.getId());
            }
            
            @Override
            public void messageSent(IoSession session, Object message) throws Exception {
                if (message instanceof IoBuffer ioBuffer) { // Usar pattern matching
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha procesado el envío de IoBuffer ({} bytes restantes en el buffer enviado).", ioBuffer.remaining());
                } else if (message != null) { // Verificar null antes de toString()
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha procesado el envío de un mensaje: {}", message.toString());
                }
            }
        });

        try {
            LOGGER.info("🔌 Intentando conectar a {}:{}...", HOST, PORT);
            ConnectFuture connectFuture = connector.connect(new InetSocketAddress(HOST, PORT));
            connectFuture.awaitUninterruptibly();

            if (!connectFuture.isConnected()) {
                LOGGER.error("🔥 No se pudo conectar al servidor.");
                return;
            }

            final IoSession session = connectFuture.getSession(); // Hacer final para lambda
            LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Preparando datos...", session.getId());

            byte[] largeDataArray = new byte[TOTAL_BYTES_TO_SEND];
            IoBuffer ioBuffer = IoBuffer.allocate(TOTAL_BYTES_TO_SEND).put(largeDataArray).flip();

            LOGGER.info("Enviando {} bytes...", ioBuffer.remaining());
            long startTime = System.nanoTime();

            WriteFuture writeFuture = session.write(ioBuffer);

            // Añadir un listener al WriteFuture
            writeFuture.addListener(future -> {
                WriteFuture wf = (WriteFuture) future;
                if (wf.isDone() && wf.getException() == null) { // Usar isDone() en lugar de isWritten()
                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;
                    double durationSeconds = duration / 1_000_000_000.0;
                    double megabytes = TOTAL_BYTES_TO_SEND / (1024.0 * 1024.0);
                    double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
                    double mbitps = mbps * 8;

                    LOGGER.info("-------------------------------------------------");
                    LOGGER.info("📊 Envío de Bloque (Listener - isDone() && sin excepción):");
                    LOGGER.info("   Bytes Totales Enviados: {}", TOTAL_BYTES_TO_SEND);
                    LOGGER.info("   Tiempo Transcurrido para write(): {} ms ({} s)", duration / 1_000_000, String.format("%.3f", durationSeconds));
                    LOGGER.info("   Velocidad (basada en write()): {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
                    LOGGER.info("-------------------------------------------------");
                } else {
                    LOGGER.error("🔥 Falló el envío del buffer (Listener - isDone() falso o con excepción).");
                    if (wf.getException() != null) {
                        LOGGER.error("   Excepción en WriteFuture (Listener): ", wf.getException());
                    }
                }
                LOGGER.info("Cliente (Listener): Escritura procesada. Solicitando cierre con flush...");
                session.closeOnFlush().awaitUninterruptibly(5000);
            });
            
            LOGGER.info("Cliente: Hilo principal esperando cierre de sesión...");
            session.getCloseFuture().awaitUninterruptibly(WRITE_TIMEOUT + 10000);

            if (session.isClosing() || !session.isConnected()) {
                LOGGER.info("Cliente: Sesión cerrada o en proceso de cierre.");
            } else {
                LOGGER.warn("Cliente: Sesión aún activa después de la espera. Forzando cierre.");
                session.closeNow().awaitUninterruptibly();
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error general en el cliente: {}", e.getMessage(), e);
        } finally {
            if (!connector.isDisposed()) { // Eliminar verificación innecesaria de null
                LOGGER.info("Cliente: Limpiando y disponiendo del conector...");
                connector.dispose(true);
            }
            LOGGER.info("🧹 Cliente finalizado.");
        }
    }
}