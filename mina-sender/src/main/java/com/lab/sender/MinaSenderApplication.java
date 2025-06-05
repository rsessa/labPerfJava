package com.lab.sender;

import java.net.InetSocketAddress;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession; // Opcional para depuración
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import java.util.Arrays; // Descomentar si quieres rellenar el buffer con datos específicos

public class MinaSenderApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaSenderApplication.class);
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int TOTAL_BYTES_TO_SEND = 82178160;
    private static final long CONNECT_TIMEOUT = 30000; // 30 segundos

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA para prueba de datos grandes...");

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);

        // No se añaden codecs, MINA enviará IoBuffer directamente.
        // No se configuran buffers explícitamente, MINA usará los del SO.

        // LoggingFilter es opcional y puede ser MUY verboso con datos grandes.
        // Coméntalo para pruebas de rendimiento puro si es necesario.
        // connector.getFilterChain().addLast("logger", new LoggingFilter());

        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void sessionOpened(IoSession session) {
                LOGGER.info("ℹ️ Sesión MINA abierta (ID: {}).", session.getId());
                // Aquí podrías añadir el código para consultar los buffers de MINA
                // si existiera una forma fácil en la API 2.x,
                // pero como discutimos, no es directo.
            }

            @Override
            public void exceptionCaught(IoSession session, Throwable cause) {
                LOGGER.error("❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
                session.closeNow();
            }

            @Override
            public void sessionClosed(IoSession session) {
                LOGGER.info("🚪 Sesión MINA cerrada (ID: {}).", session.getId());
            }
            
            @Override
            public void messageSent(IoSession session, Object message) throws Exception {
                // Se llama cuando MINA ha procesado el envío.
                // No necesariamente significa que el receptor lo haya recibido.
                // LOGGER.info("✈️  MINA ha procesado el envío de un buffer.");
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
                // Opcional: Rellenar el array con datos, ej: Arrays.fill(largeDataArray, (byte) 'A');

                IoBuffer ioBuffer = IoBuffer.allocate(TOTAL_BYTES_TO_SEND);
                ioBuffer.put(largeDataArray);
                ioBuffer.flip(); // Prepara el buffer para ser leído/enviado

                LOGGER.info("Enviando {} bytes...", ioBuffer.remaining());
                long startTime = System.nanoTime();

                WriteFuture writeFuture = session.write(ioBuffer);
                writeFuture.awaitUninterruptibly(); // Espera a que la operación de escritura se complete (enviado al SO)

                if (writeFuture.isWritten()) {
                    long endTime = System.nanoTime();
                    long duration = endTime - startTime;

                    double durationSeconds = duration / 1_000_000_000.0;
                    double megabytes = TOTAL_BYTES_TO_SEND / (1024.0 * 1024.0);
                    double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
                    double mbitps = mbps * 8;

                    LOGGER.info("-------------------------------------------------");
                    LOGGER.info("📊 Envío de Bloque Completado:");
                    LOGGER.info("   Bytes Totales Enviados: {}", TOTAL_BYTES_TO_SEND);
                    LOGGER.info("   Tiempo Transcurrido: {} ms ({} s)", duration / 1_000_000, String.format("%.3f", durationSeconds));
                    LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps), String.format("%.2f", mbitps));
                    LOGGER.info("-------------------------------------------------");
                } else {
                    LOGGER.error("🔥 Falló el envío del buffer.");
                }
                
                // Damos tiempo para que los logs se procesen y para el cierre ordenado
                try {
                    Thread.sleep(1000); // Espera 1 segundo antes de cerrar
                } catch (InterruptedException ignored) {}
                
                session.closeNow().awaitUninterruptibly();

            } else {
                 LOGGER.error("🔥 No se pudo conectar al servidor.");
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error durante la conexión o envío: {}", e.getMessage(), e);
        } finally {
            if (connector != null && !connector.isDisposed()) {
                connector.dispose();
            }
            LOGGER.info("🧹 Recursos del conector liberados.");
        }
    }
}