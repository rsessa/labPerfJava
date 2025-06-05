package com.lab.sender;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MinaClientHandler extends IoHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaClientHandler.class);
    // Ya no necesitamos messageToSend si no lo vamos a enviar desde aquí.
    // Si lo dejas, asegúrate de que MinaSenderApplication lo pase como null o vacío.
    // private final String messageToSend;

    // Constructor modificado o usar el por defecto si messageToSend no se usa
    public MinaClientHandler(/* String messageToSend */) {
        // this.messageToSend = messageToSend;
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

        LOGGER.info("CLIENT HANDLER - ℹ️ Sesión MINA abierta (ID: {}).", session.getId());
        // NO ENVIAMOS NADA DESDE AQUÍ si el main se encarga del envío principal
        // if (this.messageToSend != null && !this.messageToSend.isEmpty()) {
        //     session.write(this.messageToSend); // Esto causaba el error sin un encoder para String
        // }
    }

    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        LOGGER.info("CLIENT HANDLER - 💬 Mensaje recibido del servidor: {}", message.toString());
        // El cliente no espera respuestas activamente en este escenario.
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
        if (message instanceof IoBuffer ioBuffer) { // Usar pattern matching
            LOGGER.info("CLIENT HANDLER - ✈️  MINA ha procesado el envío de: IoBuffer ({} bytes restantes en el buffer enviado)", ioBuffer.remaining());
        } else if (message != null) {
            LOGGER.info("CLIENT HANDLER - ✈️  MINA ha procesado el envío de: {}", message.toString());
        } else {
            LOGGER.info("CLIENT HANDLER - ✈️  MINA ha procesado el envío de un mensaje nulo.");
        }
    }
}