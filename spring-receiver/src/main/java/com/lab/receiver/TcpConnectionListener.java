package com.lab.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.ip.tcp.connection.SocketInfo;
import org.springframework.integration.ip.tcp.connection.TcpConnection;
import org.springframework.integration.ip.tcp.connection.TcpConnectionOpenEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TcpConnectionListener implements ApplicationListener<TcpConnectionOpenEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpConnectionListener.class);

    @Override
    public void onApplicationEvent(@NonNull TcpConnectionOpenEvent event) {
        TcpConnection connection = (TcpConnection) event.getSource();
        try {
            SocketInfo socketInfo = connection.getSocketInfo();
            int sendBuffer = socketInfo.getSendBufferSize();
            int receiveBuffer = socketInfo.getReceiveBufferSize();

            LOGGER.info("-------------------------------------------------");
            LOGGER.info("ℹ️  Conexión TCP Abierta (Spring - ID: {})", event.getConnectionId());
            LOGGER.info("   -> Host Remoto: {}:{}", connection.getHostAddress(), connection.getPort());
            LOGGER.info("   -> Búfer de Envío (SO_SNDBUF):   {} bytes ({} KB)", sendBuffer, sendBuffer / 1024);
            LOGGER.info("   -> Búfer de Recepción (SO_RCVBUF): {} bytes ({} KB)", receiveBuffer, receiveBuffer / 1024);
            LOGGER.info("-------------------------------------------------");

        } catch (Exception e) {
            LOGGER.warn("⚠️ No se pudieron obtener los tamaños de búfer para la conexión {}: {}", event.getConnectionId(), e.getMessage());
        }
    }
}