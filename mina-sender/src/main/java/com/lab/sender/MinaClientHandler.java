package com.lab.sender;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinaClientHandler extends IoHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaClientHandler.class);
    private final String messageToSend; // Este mensaje se enviará al abrir la sesión

    public MinaClientHandler(String messageToSend) {
        this.messageToSend = messageToSend;
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        IoSessionConfig config = session.getConfig();

        LOGGER.info("-------------------------------------------------");
        LOGGER.info("ℹ️  MINA Session Configuration (Cliente):");
        LOGGER.info("   -> Read Buffer Size (MINA app): {} bytes", config.getReadBufferSize());
        LOGGER.info("   -> Min Read Buffer Size (MINA app): {} bytes", config.getMinReadBufferSize());
        LOGGER.info("   -> Max Read Buffer Size (MINA app): {} bytes", config.getMaxReadBufferSize());
        LOGGER.info("   -> Throughput Calculation Interval: {} seconds", config.getThroughputCalculationInterval());
        LOGGER.info("   -> Nota: Los buffers TCP del Socket real (SO_SNDBUF/SO_RCVBUF) usarán los defaults del SO.");
        LOGGER.info("-------------------------------------------------");

        LOGGER.info("CLIENT HANDLER - ℹ️ Sesión MINA abierta (ID: {}). Enviando mensaje inicial...", session.getId());
        if (this.messageToSend != null && !this.messageToSend.isEmpty()) {
            session.write(this.messageToSend); // Envía el mensaje pequeño
        }
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        LOGGER.info("CLIENT HANDLER - 💬 Mensaje recibido del servidor: {}", message.toString());
        // El cliente no espera respuestas en este escenario de envío masivo,
        // pero si las hubiera, se manejarían aquí.
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        LOGGER.error("CLIENT HANDLER - ❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
        session.closeNow();
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        LOGGER.info("CLIENT HANDLER - 🚪 Sesión MINA cerrada (ID: {}).", session.getId());
    }

    @Override
    public void messageSent(IoSession session, Object message) throws Exception {
        // Este se llamará para el messageToSend y para cada chunk del IoBuffer
        // si no se usa un codec específico para el messageToSend que lo convierta a IoBuffer.
        // Si messageToSend es un String y no hay un TextLineCodec, podría dar error o no llamarse.
        // Para el IoBuffer, el LoggingFilter dará más info.
        LOGGER.info("CLIENT HANDLER - ✈️  MINA ha procesado el envío de: {}", message.getClass().getSimpleName());
    }
}