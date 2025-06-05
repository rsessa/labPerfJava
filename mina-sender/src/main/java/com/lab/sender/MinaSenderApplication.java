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

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA (v. depuración envío)...");

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);
        connector.getFilterChain().addLast("logger", new LoggingFilter()); // Mantenemos el logger

        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void sessionOpened(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ℹ️ Sesión MINA abierta (ID: {}).", session.getId());
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                MinaSenderApplication.LOGGER.error("CLIENT HANDLER - ❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
                session.close(true); // Usar close(true) para un cierre inmediato
            }

            @Override
            public void sessionClosed(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - 🚪 Sesión MINA cerrada (ID: {}).", session.getId());
            }
            
            @Override
            public void messageSent(IoSession session, Object message) throws Exception {
                if (message instanceof IoBuffer) {
                     // Este log es útil, pero puede ser que el WriteFuture ya nos dé la info
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha pasado un IoBuffer al procesador de E/S.");
                }
            }
        });

        IoSession session = null;
        try {
            LOGGER.info("🔌 Intentando conectar a {}:{}...", HOST, PORT);
            ConnectFuture connectFuture = connector.connect(new InetSocketAddress(HOST, PORT));
            connectFuture.awaitUninterruptibly(); // Esperar a que la conexión se establezca

            if (!connectFuture.isConnected()) {
                LOGGER.error("🔥 No se pudo conectar al servidor.");
                connector.dispose();
                return;
            }

            session = connectFuture.getSession();
            LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Preparando datos...", session.getId());

            byte[] largeDataArray = new byte[TOTAL_BYTES_TO_SEND];
            // Arrays.fill(largeDataArray, (byte) 'A'); // Opcional
            IoBuffer ioBuffer = IoBuffer.allocate(TOTAL_BYTES_TO_SEND).put(largeDataArray).flip();

            LOGGER.info("Enviando {} bytes...", ioBuffer.remaining());
            long startTime = System.nanoTime();

            WriteFuture writeFuture = session.write(ioBuffer);
            
            // Esperar a que la escritura se complete o falle
            // Un timeout aquí es buena idea para no bloquear indefinidamente
            if (writeFuture.awaitUninterruptibly(CONNECT_TIMEOUT + 5000)) { // Espera un poco más que el timeout de conexión
                if (writeFuture.isWritten()) {
                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;
                    // ... (cálculos de velocidad como antes) ...
                    LOGGER.info("-------------------------------------------------");
                    LOGGER.info("📊 Envío de Bloque (WriteFuture.isWritten() == true):");
                    // ... (resto de los logs de estadísticas) ...
                    LOGGER.info("   Bytes Totales Enviados: {}", TOTAL_BYTES_TO_SEND);
                    LOGGER.info("   Tiempo Transcurrido para write(): {} ms", duration / 1_000_000);
                    LOGGER.info("-------------------------------------------------");

                    // Después de un envío exitoso de un gran bloque, es común cerrar la conexión
                    // si no se espera más interacción, o esperar una confirmación.
                    // Por ahora, cerraremos desde el cliente después de enviar.
                    LOGGER.info("Cliente: Envío completado según WriteFuture. Esperando un poco y cerrando sesión...");
                    Thread.sleep(2000); // Da tiempo a que los datos fluyan y logs
                    session.closeNow().awaitUninterruptibly(); // Cierre inmediato después de la escritura
                    
                } else {
                    LOGGER.error("🔥 Falló el envío del buffer (WriteFuture.isWritten() fue false).");
                    if (writeFuture.getException() != null) {
                        LOGGER.error("   Excepción en WriteFuture: ", writeFuture.getException());
                    }
                     session.closeNow().awaitUninterruptibly(); // Cierra si falla
                }
            } else {
                LOGGER.error("🔥 Timeout esperando la confirmación de escritura del WriteFuture.");
                session.closeNow().awaitUninterruptibly(); // Cierra si hay timeout
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error general en el cliente: {}", e.getMessage(), e);
            if (session != null) {
                session.closeNow();
            }
        } finally {
            // Esperar a que todas las sesiones se cierren si no lo hemos hecho ya.
            // El dispose se encarga de limpiar los recursos del conector.
            if (!connector.isDisposed()) {
                LOGGER.info("Cliente: Limpiando y disponiendo del conector...");
                // Esperamos a que el cierre de sesión de arriba se complete antes de disponer.
                // connector.dispose(true); // 'true' para esperar que las sesiones se cierren.
                                        // Esto puede ser redundante si ya cerramos la sesión explícitamente.
                                        // Si la sesión se cierra en exceptionCaught, este dispose es importante.
                connector.dispose(); // Intentemos un dispose simple
            }
            LOGGER.info("🧹 Cliente finalizado.");
        }
    }
}