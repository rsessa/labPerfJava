package com.lab.sender;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
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
    private static final long CONNECT_TIMEOUT = 30000; // 30 segundos para conectar
    private static final long WRITE_TIMEOUT = 90000;   // 90 segundos para que WriteFuture confirme

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA (v. depuración envío con timeouts extendidos)...");

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);
        // LoggingFilter es crucial para depurar qué se envía y cuándo
        connector.getFilterChain().addLast("logger", new LoggingFilter());

        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void sessionOpened(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ℹ️ Sesión MINA abierta (ID: {}).", session.getId());
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                MinaSenderApplication.LOGGER.error("CLIENT HANDLER - ❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
                session.close(true); // Cierre inmediato si hay error en el handler
            }

            @Override
            public void sessionClosed(IoSession session) {
                MinaSenderApplication.LOGGER.info("CLIENT HANDLER - 🚪 Sesión MINA cerrada (ID: {}).", session.getId());
            }
            
            @Override
            public void messageSent(IoSession session, Object message) throws Exception {
                // Este log nos ayuda a ver si MINA considera que el mensaje (o parte de él) fue enviado
                if (message instanceof IoBuffer) {
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha procesado el envío de IoBuffer ({} bytes restantes en el buffer enviado).", ((IoBuffer)message).remaining());
                } else {
                    MinaSenderApplication.LOGGER.info("CLIENT HANDLER - ✈️ MINA ha procesado el envío de un mensaje: {}", message.toString());
                }
            }
        });

        IoSession session = null;
        try {
            LOGGER.info("🔌 Intentando conectar a {}:{}...", HOST, PORT);
            ConnectFuture connectFuture = connector.connect(new InetSocketAddress(HOST, PORT));
            connectFuture.awaitUninterruptibly(); 

            if (!connectFuture.isConnected()) {
                LOGGER.error("🔥 No se pudo conectar al servidor.");
                // No es necesario llamar a dispose() aquí si el conector no se usó realmente
                // pero si se quiere ser exhaustivo:
                // if (!connector.isDisposed()) { connector.dispose(); }
                return; // Salir si no hay conexión
            }

            session = connectFuture.getSession();
            LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Preparando datos...", session.getId());

            byte[] largeDataArray = new byte[TOTAL_BYTES_TO_SEND];
            // Opcional: Rellenar el array, ej: Arrays.fill(largeDataArray, (byte) 'A');
            IoBuffer ioBuffer = IoBuffer.allocate(TOTAL_BYTES_TO_SEND).put(largeDataArray).flip();

            LOGGER.info("Enviando {} bytes...", ioBuffer.remaining());
            long startTime = System.nanoTime();

            WriteFuture writeFuture = session.write(ioBuffer);
            
            // Esperar a que la escritura se complete o falle, con un timeout más largo
            if (writeFuture.awaitUninterruptibly(WRITE_TIMEOUT)) { 
                if (writeFuture.isWritten()) {
                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;
                    double durationSeconds = duration / 1_000_000_000.0;
                    double megabytes = TOTAL_BYTES_TO_SEND / (1024.0 * 1024.0);
                    double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
                    double mbitps = mbps * 8;

                    LOGGER.info("-------------------------------------------------");
                    LOGGER.info("📊 Envío de Bloque (WriteFuture.isWritten() == true):");
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
            } else {
                LOGGER.error("🔥 Timeout ({ } ms) esperando la confirmación de escritura del WriteFuture.", WRITE_TIMEOUT);
            }

            // Cierra la sesión explícitamente después de intentar el envío.
            // El servidor debería detectar esto y procesar los bytes recibidos.
            if (session != null && session.isConnected()) {
                LOGGER.info("Cliente: Envío (o intento) completado. Cerrando sesión...");
                // session.closeOnFlush().awaitUninterruptibly(); // Espera a que se envíe lo que quede y cierra
                session.closeNow().awaitUninterruptibly(); // Cierra más inmediatamente
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error general en el cliente: {}", e.getMessage(), e);
            if (session != null && session.isActive()) { // isActive() es mejor que isConnected() aquí
                session.closeNow();
            }
        } finally {
            // Esperar a que todas las sesiones se cierren si no lo hemos hecho ya.
            // El dispose se encarga de limpiar los recursos del conector.
            if (connector != null && !connector.isDisposed()) {
                LOGGER.info("Cliente: Limpiando y disponiendo del conector...");
                connector.dispose(true);  // 'true' para esperar a que las sesiones se cierren.
            }
            LOGGER.info("🧹 Cliente finalizado.");
        }
    }
}