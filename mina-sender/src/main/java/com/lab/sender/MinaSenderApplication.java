package com.lab.sender;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinaSenderApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaSenderApplication.class);
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final int MESSAGE_COUNT = 100000;
    private static final long CONNECT_TIMEOUT = 30000;

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA (Enviando {} mensajes pequeños)...", MESSAGE_COUNT);

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);

        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(new TextLineCodecFactory(StandardCharsets.UTF_8)));
        // Opcional: añadir el logger para ver más detalle
        // connector.getFilterChain().addLast("logger", new LoggingFilter());

        // Usar un handler simple inline en lugar de MinaClientHandler
        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
                LOGGER.error("❌ Error en MINA: {}", cause.getMessage());
                session.closeNow();
            }
        });

        try {
            ConnectFuture future = connector.connect(new InetSocketAddress(HOST, PORT));
            future.awaitUninterruptibly();

            if (!future.isConnected()) {
                LOGGER.error("🔥 No se pudo conectar al servidor.");
                return;
            }

            IoSession session = future.getSession();
            LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Enviando mensajes...", session.getId());
            long startTime = System.nanoTime();

            String payload = "Este es un mensaje de prueba con algo de contenido para rellenar.";
            for (int i = 0; i < MESSAGE_COUNT; i++) {
                String message = payload + " #" + i;
                session.write(message);
            }

            // Enviar mensaje final para indicar que terminamos
            session.write("END_OF_TRANSMISSION").awaitUninterruptibly();
            
            // Damos un pequeño margen para el último envío y luego cerramos.
            Thread.sleep(2000);
            LOGGER.info("Todos los mensajes han sido enviados a la cola. Cerrando sesión...");

            session.closeNow().awaitUninterruptibly();

            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            double durationSeconds = duration / 1_000_000_000.0;
            long totalBytesApprox = (long) MESSAGE_COUNT * (payload.length() + 8);
            double mbps = (durationSeconds > 0) ? (totalBytesApprox * 8 / (1024.0 * 1024.0)) / durationSeconds : 0;

            LOGGER.info("-------------------------------------------------");
            LOGGER.info("📊 Envío de Mensajes Pequeños Completado:");
            LOGGER.info("   Mensajes Enviados: {}", MESSAGE_COUNT);
            LOGGER.info("   Bytes Totales (aprox): {}", totalBytesApprox);
            LOGGER.info("   Tiempo Transcurrido: {} ms ({} s)", duration / 1_000_000, String.format("%.3f", durationSeconds));
            LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps/8), String.format("%.2f", mbps));
            LOGGER.info("-------------------------------------------------");

        } catch (InterruptedException e) {
            LOGGER.error("🔥 Error de interrupción durante el envío: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOGGER.error("🔥 Error de runtime durante el envío: {}", e.getMessage(), e);
        } finally {
            if (!connector.isDisposed()) { // Eliminada verificación innecesaria de null
                LOGGER.info("Cliente: Limpiando y disponiendo del conector...");
                connector.dispose(true);
            }
            LOGGER.info("🧹 Cliente finalizado.");
        }
    }
}