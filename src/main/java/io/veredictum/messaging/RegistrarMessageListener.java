package io.veredictum.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.veredictum.registrar.RegistrarRequest;
import io.veredictum.registrar.RegistrarRequestHandler;
import io.veredictum.util.Hasher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class RegistrarMessageListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private ExecutorService executorService = Executors.newCachedThreadPool();

    @Value("${registrar.ack.kafka.topic}")
    private String registrarAckTopic;

    private final RegistrarRequestHandler requestHandler;

    private final KafkaTemplate<String, String> kafkaTemplate; // KafkaTemplate instance is thread safe

    @Autowired
    public RegistrarMessageListener(RegistrarRequestHandler requestHandler, KafkaTemplate<String, String> kafkaTemplate) {
        this.requestHandler = requestHandler;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "registrarTopic")
    public void listen(ConsumerRecord<String, String> cr) throws Exception {
        logger.info("Received message: " + cr.value());
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        RegistrarRequest request = mapper.readValue(cr.value(), RegistrarRequest.class);
        request.setOriginalFileHash(Hasher.hashString(request.getContentId()));
        request.setTranscodedFileHash(Hasher.hashString(request.getContentId() + request.getContentId()));
        Future<TransactionReceipt> receiptFuture = requestHandler.handle(request);
        executorService.submit(new RegistrarReceiptSender(kafkaTemplate, registrarAckTopic, receiptFuture));
    }
}