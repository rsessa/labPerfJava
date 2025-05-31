package com.lab.sender;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.session.IoSessionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinaClientHandler extends IoHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinaClientHandler.class);
    private final String messageToSend;

    public MinaClientHandler(String messageToSend) {
        this.messageToSend = messageToSend;
    }

    @Override
    public void sessionOpened(IoSession session) throws Exception {
        // Obtenemos la config de la sesión (aunque no podamos leer los buffers)
        IoSessionConfig config = session.getConfig();

        LOGGER.info("-------------------------------------------------");
        LOGGER.info("ℹ️  Valores de Buffer TCP (MINA):");
        LOGGER.info("   -> MINA usará los valores por defecto del SO, ya que");
        LOGGER.info("   -> la API 2.x no permite leerlos fácilmente tras la conexión.");
        LOGGER.info("-------------------------------------------------");

        LOGGER.info("ℹ️ Sesión MINA abierta (ID: {}). Enviando mensaje...", session.getId());
        // Enviamos el mensaje
        session.write(messageToSend);
    }

    // ... (El resto de los métodos: messageReceived, exceptionCaught, etc., quedan igual) ...
    @Override
    public void messageReceived(IoSession session, Object message) throws Exception {
        LOGGER.info("💬 Mensaje recibido del servidor: {}", message.toString());
        session.closeNow();
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        LOGGER.error("❌ Error en MINA (Sesión ID: {}): {}", session.getId(), cause.getMessage(), cause);
        session.closeNow();
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        LOGGER.info("🚪 Sesión MINA cerrada (ID: {}).", session.getId());
    }

    @Override
    public void messageSent(IoSession session, Object message) throws Exception {
        LOGGER.info("✈️  >>> Mensaje Enviado: {}", message.toString().trim());
    }
}