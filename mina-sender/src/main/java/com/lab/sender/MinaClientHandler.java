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
        IoSessionConfig config = session.getConfig();

        LOGGER.info("-------------------------------------------------");
        LOGGER.info("ℹ️  MINA Session Configuration:");
        LOGGER.info("   -> Read Buffer Size: {} bytes", config.getReadBufferSize());
        LOGGER.info("   -> Min Read Buffer Size: {} bytes", config.getMinReadBufferSize());
        LOGGER.info("   -> Max Read Buffer Size: {} bytes", config.getMaxReadBufferSize());
        LOGGER.info("   -> Throughput Calculation Interval: {} seconds", config.getThroughputCalculationInterval());
        LOGGER.info("   -> Note: Actual TCP socket buffers (SO_SNDBUF/SO_RCVBUF) use OS defaults");
        LOGGER.info("-------------------------------------------------");

        LOGGER.info("ℹ️ MINA session opened (ID: {}). Sending message...", session.getId());
        session.write(messageToSend);
    }

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