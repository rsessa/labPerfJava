package com.lab.sender;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter; // Asegúrate de que esta importación esté
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
// import java.util.Arrays; // Descomentar si quieres rellenar el buffer con datos específicos

public class MinaSenderApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaSenderApplication.class);
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int TOTAL_BYTES_TO_SEND = 82178160;
    private static final long CONNECT_TIMEOUT = 30000;

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA para prueba de datos grandes (CON LOGGING DETALLADO)...");

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);

        // *** CAMBIO: LoggingFilter DESCOMENTADO ***
        connector.getFilterChain().addLast("logger", new LoggingFilter());

        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void sessionOpened(IoSession session) {
                // Usamos el logger de la clase principal para consistencia
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ℹ️ Sesión MINA abierta (ID: {}).", session.getId());
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                MinaSenderApplication.LOGGER.error("CLIENT HANDLER - ❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
                session.closeNow();
            }

            @Override
            public void sessionClosed(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - 🚪 Sesión MINA cerrada (ID: {}).", session.getId());
            }
            
            @Override
            public void messageSent(IoSession session, Object message) throws Exception {
                if (message instanceof IoBuffer) {
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha procesado el envío de IoBuffer ({} bytes restantes en buffer).", ((IoBuffer)message).remaining());
                } else {
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha procesado el envío de un mensaje: {}", message.toString());
                }
            }
        });

        IoSession session = null;
        try {
            LOGGER.info("🔌 Intentando conectar a {}:{}...", HOST, PORT);
            ConnectFuture future = connector.connect(new InetSocketAddress(HOST, PORT));
            future.awaitUninterruptibly();

            if (future.isConnected()) {
                session = future.getSession();
                LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Preparando y enviando datos...", session.getId());

                byte[] largeDataArray = new byte[TOTAL_BYTES_TO_SEND];
                // Opcional: Rellenar el array, ej: Arrays.fill(largeDataArray, (byte) 'A');

                IoBuffer ioBuffer = IoBuffer.allocate(TOTAL_BYTES_TO_SEND);
                ioBuffer.put(largeDataArray);
                ioBuffer.flip();

                LOGGER.info("Enviando {} bytes...", ioBuffer.remaining());
                long startTime = System.nanoTime();

                WriteFuture writeFuture = session.write(ioBuffer);
                writeFuture.awaitUninterruptibly(); 

                if (writeFuture.isWritten()) {
                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;

                    double durationSeconds = duration / 1_000_000_000.0;
                    double megabytes = TOTAL_BYTES_TO_SEND / (1024.0 * 1024.0);
                    double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
                    double mbitps = mbps * 8;

                    LOGGER.info("-------------------------------------------------");
                    LOGGER.info("📊 Envío de Bloque (intento) Completado:");
                    LOGGER.info("   WriteFuture.isWritten() fue true.");
                    LOGGER.info("   Bytes Totales Enviados: {}", TOTAL_BYTES_TO_SEND);
                    LOGGER.info("   Tiempo Transcurrido para write(): {} ms ({} s)", duration / 1_000_000, String.format("%.3f", durationSeconds));
                    LOGGER.info("   Velocidad (basada en write()): {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
                    LOGGER.info("-------------------------------------------------");
                } else {
                    LOGGER.error("🔥 Falló el envío del buffer (WriteFuture.isWritten() fue false).");
                    if (writeFuture.getException() != null) {
                        LOGGER.error("   Excepción en WriteFuture: ", writeFuture.getException());
                    }
                }
                
                LOGGER.info("Cliente: Esperando 5 segundos antes de considerar cerrar la sesión...");
                Thread.sleep(5000); // Aumentamos la espera para dar más tiempo al receptor

                // *** CAMBIO: Comentamos el cierre explícito para ver si el servidor lo hace o si los datos fluyen ***
                // if (session != null && session.isConnected()) {
                //    LOGGER.info("Cliente: Cerrando sesión explícitamente...");
                //    session.closeNow().awaitUninterruptibly();
                // }

            } else {
                 LOGGER.error("🔥 No se pudo conectar al servidor.");
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error durante la conexión o envío: {}", e.getMessage(), e);
        } finally {
            // Dejamos que el dispose se encargue de cerrar si la sesión sigue abierta.
            // El dispose también cierra las conexiones activas.
            if (connector != null && !connector.isDisposed()) {
                 LOGGER.info("Cliente: Llamando a connector.dispose()...");
                connector.dispose(true); // true para esperar a que las sesiones se cierren
            }
            LOGGER.info("🧹 Recursos del conector (intento de) liberados.");
        }
    }
}