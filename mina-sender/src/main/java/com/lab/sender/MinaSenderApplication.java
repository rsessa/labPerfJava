package com.lab.sender;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Aplicación cliente que usa Apache MINA para enviar muchos mensajes TCP y
 * medir velocidad.
 */
public class MinaSenderApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaSenderApplication.class);
    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final long CONNECT_TIMEOUT = 30000;
    private static final int NUM_MESSAGES = 100000; // Número de mensajes a enviar
    private static final String MESSAGE_PAYLOAD = "Este es un mensaje de prueba para medir velocidad.";

    public static void main(String[] args) {
        LOGGER.info("🚀 Iniciando Cliente TCP Apache MINA para prueba de velocidad...");

        NioSocketConnector connector = new NioSocketConnector();
        connector.setConnectTimeoutMillis(CONNECT_TIMEOUT);

        TextLineCodecFactory codecFactory = new TextLineCodecFactory(
                StandardCharsets.UTF_8, "\r\n", "\r\n");
        connector.getFilterChain().addLast("codec", new ProtocolCodecFilter(codecFactory));
        // Opcional: Descomenta para ver logs detallados de cada mensaje
        // connector.getFilterChain().addLast("logger", new LoggingFilter());

        connector.setHandler(new IoHandlerAdapter() {
            @Override
            public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
                LOGGER.error("❌ Error en MINA: {}", cause.getMessage());
                session.closeNow();
            }
        });

        IoSession session = null;
        try {
            LOGGER.info("🔌 Intentando conectar a {}:{}...", HOST, PORT);
            ConnectFuture future = connector.connect(new InetSocketAddress(HOST, PORT));
            future.awaitUninterruptibly();

            if (future.isConnected()) {
                session = future.getSession();
                LOGGER.info("🔗 Conexión establecida. Sesión ID: {}. Iniciando envío...", session.getId());

                long startTime = System.nanoTime();

                for (int i = 0; i < NUM_MESSAGES; i++) {
                    session.write(MESSAGE_PAYLOAD + " #" + (i + 1));
                }
                // Envía un mensaje final para indicar que hemos terminado
                session.write("END_OF_TRANSMISSION").awaitUninterruptibly();

                long endTime = System.nanoTime();
                long duration = endTime - startTime;
                long totalBytes = (long) NUM_MESSAGES * MESSAGE_PAYLOAD.getBytes(StandardCharsets.UTF_8).length;

                double durationSeconds = duration / 1_000_000_000.0;
                double megabytes = totalBytes / (1024.0 * 1024.0);
                double mbps = megabytes / durationSeconds;
                double mbitps = mbps * 8;

                LOGGER.info("-------------------------------------------------");
                LOGGER.info("📊 Envío Completado:");
                LOGGER.info("   Mensajes Enviados: {}", NUM_MESSAGES);
                LOGGER.info("   Bytes Totales (aprox): {}", totalBytes);
                LOGGER.info("   Tiempo Transcurrido: {} ms ({} s)", duration / 1_000_000,
                        String.format("%.3f", durationSeconds));
                LOGGER.info("   Velocidad: {} MB/s ({} Mbps)", String.format("%.2f", mbps),
                        String.format("%.2f", mbitps));
                LOGGER.info("-------------------------------------------------");

                session.getCloseFuture().awaitUninterruptibly(5000);
            } else {
                LOGGER.error("🔥 No se pudo conectar al servidor.");
            }

        } catch (Exception e) {
            LOGGER.error("🔥 Error durante la conexión o envío: {}", e.getMessage(), e);
        } finally {
            if (session != null)
                session.closeNow();
            connector.dispose();
            LOGGER.info("🧹 Recursos del conector liberados.");
        }
    }
}