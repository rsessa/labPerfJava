package com.lab.receiver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNioServerConnectionFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuración para el Servidor TCP con Spring Integration y medición de
 * rendimiento.
 */
@Configuration
@EnableIntegration
@IntegrationComponentScan
public class TcpServerConfig {

    // Definimos el puerto en el que escucharemos.
    // Puedes usar otro si 12345 está ocupado.
    private static final int PORT = 12345;
    private static final int BUFFER_SIZE = 131072; // 128 KB

    // --- Variables para Medición ---
    private final AtomicLong messageCount = new AtomicLong(0);
    private final AtomicLong byteCount = new AtomicLong(0);
    private volatile long startTime = 0;
    // --- Fin Variables para Medición ---

    /**
     * Define la "Fábrica de Conexiones" del servidor.
     * Es la encargada de abrir el puerto y aceptar conexiones entrantes.
     * Usamos TcpNioServerConnectionFactory para E/S no bloqueante (más eficiente).
     */
    @Bean
    public AbstractServerConnectionFactory serverConnectionFactory() {
        TcpNioServerConnectionFactory factory = new TcpNioServerConnectionFactory(PORT);

        // ¡¡PUNTO CLAVE!! Definimos cómo se leen y escriben los mensajes.
        // ByteArrayCrLfSerializer asume que cada mensaje termina con
        // un retorno de carro (\r) y un salto de línea (\n).
        // El cliente (MINA) DEBERÁ enviar los mensajes con este final.
        factory.setSerializer(new ByteArrayCrLfSerializer());
        factory.setDeserializer(new ByteArrayCrLfSerializer());

        // Opcional: Establece un timeout para las conexiones inactivas.
        factory.setSoTimeout(10000); // 10 segundos

        // --- ¡NUEVAS LÍNEAS! ---
        // Establecemos el tamaño del búfer de recepción del socket TCP (SO_RCVBUF)
        factory.setSoReceiveBufferSize(BUFFER_SIZE);
        // Establecemos el tamaño del búfer de envío del socket TCP (SO_SNDBUF)
        factory.setSoSendBufferSize(BUFFER_SIZE);
        // --- FIN NUEVAS LÍNEAS ---

        System.out.println("-> Fábrica de Conexiones TCP Creada en Puerto " + PORT);
        System.out.println("   -> Tamaño Búfer Envío/Recepción: " + BUFFER_SIZE + " bytes"); // Mensaje de log añadido
        return factory;
    }

    /**
     * Define un "Canal de Mensajes" de entrada.
     * Cuando el adaptador TCP recibe un mensaje, lo envía a este canal.
     */
    @Bean
    public MessageChannel inboundTcpChannel() {
        return new DirectChannel();
    }

    /**
     * Define el "Adaptador de Canal Receptor".
     * Conecta la Fábrica de Conexiones (que recibe datos de la red)
     * con nuestro Canal de Mensajes (por donde fluyen los datos en Spring).
     */
    @Bean
    public TcpReceivingChannelAdapter inboundAdapter(AbstractServerConnectionFactory serverConnectionFactory,
            MessageChannel inboundTcpChannel) {
        TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
        adapter.setConnectionFactory(serverConnectionFactory);
        adapter.setOutputChannel(inboundTcpChannel); // Envía a nuestro canal de entrada
        System.out.println("-> Adaptador TCP Receptor Creado.");
        return adapter;
    }

    /**
     * Define el "Activador de Servicio".
     * Este método 'escucha' en el canal 'inboundTcpChannel'.
     * Cuando llega un mensaje al canal, este método se ejecuta.
     * Aquí es donde procesamos el mensaje recibido.
     *
     * @param message El mensaje recibido (el payload será un array de bytes).
     */
    @ServiceActivator(inputChannel = "inboundTcpChannel")
    public void handleMessage(Message<byte[]> message) {
        // Inicia el cronómetro al primer mensaje
        if (startTime == 0) {
            startTime = System.nanoTime();
            System.out.println("⏱️  Recepción Iniciada...");
        }

        long count = messageCount.incrementAndGet();
        long bytes = message.getPayload().length;
        byteCount.addAndGet(bytes);

        String receivedData = new String(message.getPayload());

        // Si es el mensaje final, calcula y muestra resultados
        if (receivedData.trim().equals("END_OF_TRANSMISSION")) {
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            long totalMessages = count - 1; // No contamos el mensaje final
            long totalBytes = byteCount.get() - bytes; // Restamos el mensaje final
            double average = (totalMessages > 0) ? (double) totalBytes / totalMessages : 0; // Media de bytes por
                                                                                            // mensaje

            double durationSeconds = duration / 1_000_000_000.0;
            double megabytes = totalBytes / (1024.0 * 1024.0);
            double mbps = (durationSeconds > 0) ? megabytes / durationSeconds : 0;
            double mbitps = mbps * 8;

            System.out.println("-------------------------------------------------");
            System.out.println("📊 Recepción Completada:");
            System.out.println("   Velocidad: " + String.format("%.2f", mbps) + " MB/s ("
                    + String.format("%.2f", mbitps) + " Mbps)");
            System.out.println("--- ESTADÍSTICAS REQUERIDAS ---");
            System.out.println("   Total sum of read bytes: " + totalBytes);
            System.out.println("   Number of read buffer: " + totalMessages);
            System.out.println("   average: " + String.format("%.4f", average));
            System.out.println("-------------------------------------------------");

            // Reinicia contadores para la próxima prueba
            startTime = 0;
            messageCount.set(0);
            byteCount.set(0);
        } else if (count % 10000 == 0) { // Muestra progreso cada 10000 mensajes
            System.out.println("   ... " + count + " mensajes recibidos.");
        }
    }
}