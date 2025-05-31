package com.lab.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.ip.tcp.connection.SocketInfo;
import org.springframework.integration.ip.tcp.connection.TcpConnection;
import org.springframework.integration.ip.tcp.connection.TcpConnectionOpenEvent;
import org.springframework.stereotype.Component;

/**
 * Escucha los eventos de apertura de conexión TCP para mostrar
 * información del socket, como los tamaños de búfer.
 */
@Component
public class TcpConnectionListener implements ApplicationListener<TcpConnectionOpenEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpConnectionListener.class);

    @Override
    public void onApplicationEvent(TcpConnectionOpenEvent event) {
        // Obtenemos la conexión del evento
        TcpConnection connection = (TcpConnection) event.getSource();

        try {
            // Obtenemos la información del socket
            SocketInfo socketInfo = connection.getSocketInfo();

            // Leemos los tamaños de búfer REALES del socket
            int sendBuffer = socketInfo.getSendBufferSize();
            int receiveBuffer = socketInfo.getReceiveBufferSize();

            LOGGER.info("-------------------------------------------------");
            LOGGER.info("ℹ️  Conexión TCP Abierta (Spring - ID: {})", event.getConnectionId());
            // --- ¡LÍNEA CORREGIDA! ---
            // Obtenemos la IP y Puerto desde 'connection', no 'socketInfo'
            LOGGER.info("   -> Host Remoto: {}:{}", connection.getHostAddress(), connection.getPort());
            // --- FIN LÍNEA CORREGIDA ---
            LOGGER.info("   -> Búfer de Envío (SO_SNDBUF):   {} bytes ({} KB)", sendBuffer, sendBuffer / 1024);
            LOGGER.info("   -> Búfer de Recepción (SO_RCVBUF): {} bytes ({} KB)", receiveBuffer, receiveBuffer / 1024);
            LOGGER.info("-------------------------------------------------");

        } catch (Exception e) {
            LOGGER.warn("⚠️ No se pudieron obtener los tamaños de búfer para la conexión {}: {}",
                    event.getConnectionId(), e.getMessage());
        }
    }
}