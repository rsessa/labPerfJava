package com.lab.receiver;

import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.ip.tcp.connection.SocketInfo;
import org.springframework.integration.ip.tcp.connection.TcpConnection;
import org.springframework.integration.ip.tcp.connection.TcpConnectionOpenEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Listens for TCP connection open events to display
 * socket information, such as buffer sizes.
 */
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
            LOGGER.info("ℹ️  TCP Connection Opened (Spring - ID: {})", event.getConnectionId());
            LOGGER.info("   -> Remote Host: {}:{}", connection.getHostAddress(), connection.getPort());
            LOGGER.info("   -> Send Buffer (SO_SNDBUF):   {} bytes ({} KB)", sendBuffer, sendBuffer / 1024);
            LOGGER.info("   -> Receive Buffer (SO_RCVBUF): {} bytes ({} KB)", receiveBuffer, receiveBuffer / 1024);
            LOGGER.info("-------------------------------------------------");

        } catch (SocketException e) {
            LOGGER.warn("⚠️ Socket exception while getting buffer sizes for connection {}: {}", event.getConnectionId(), e.getMessage());
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("⚠️ Socket info not available for connection {}: {}", event.getConnectionId(), e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.warn("⚠️ Could not get buffer sizes for connection {}: {}", event.getConnectionId(), e.getMessage());
        }
    }
}