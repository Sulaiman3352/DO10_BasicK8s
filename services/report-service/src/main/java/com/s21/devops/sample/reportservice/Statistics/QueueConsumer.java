package com.s21.devops.sample.reportservice.Statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.s21.devops.sample.reportservice.Communication.BookingStatisticsMessage;
import com.s21.devops.sample.reportservice.Service.BookingStatsService;
import org.springframework.stereotype.Component;

// 1. Add Micrometer imports
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class QueueConsumer {
    
    // 2. Make dependencies private final
    private final BookingStatsService bookingStatsService;
    private final Counter processedCounter;
    private ObjectMapper objectMapper = new ObjectMapper();

    // 3. Use constructor injection for both the service and the registry
    public QueueConsumer(BookingStatsService bookingStatsService, MeterRegistry meterRegistry) {
        this.bookingStatsService = bookingStatsService;
        this.processedCounter = Counter.builder("rabbitmq_messages_processed")
                .description("Total messages successfully processed from RabbitMQ")
                .register(meterRegistry);
    }

    public void receiveMessage(String message) throws JsonProcessingException {
        System.out.println("Received !!! (String) " + message);
        processMessage(message);
    }

    public void receiveMessage(byte[] message) throws JsonProcessingException {
        String strMessage = new String(message);
        System.out.println("Received !!! (No String) " + strMessage);
        processMessage(strMessage);
    }

    private void processMessage(String message) throws JsonProcessingException {
        BookingStatisticsMessage bsm = objectMapper.readValue(message, BookingStatisticsMessage.class);
        bookingStatsService.postBookingStatsMessage(bsm);
        
        // 4. Increment the counter at the very end!
        processedCounter.increment();
        System.out.println("Message processed and counter incremented!");
    }
}
