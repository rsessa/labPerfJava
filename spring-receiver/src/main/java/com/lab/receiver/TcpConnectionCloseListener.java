package com.lab.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.integration.ip.tcp.connection.TcpConnectionCloseEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TcpConnectionCloseListener implements ApplicationListener<TcpConnectionCloseEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TcpConnectionCloseListener.class);

    @Override
    public void onApplicationEvent(@NonNull TcpConnectionCloseEvent event) {
        Throwable cause = event.getCause(); // Almacenar el resultado una sola vez
        
        LOGGER.warn("-------------------------------------------------");
        LOGGER.warn("🚫 Conexión TCP Cerrada (Spring - ID: {})", event.getConnectionId());
        LOGGER.warn("   Causa: {}", cause != null ? cause.getMessage() : "Cierre normal o desconocido");
        if (cause != null) {
             LOGGER.warn("   Stacktrace de Causa:", cause);
        }
        LOGGER.warn("-------------------------------------------------");
    }
}