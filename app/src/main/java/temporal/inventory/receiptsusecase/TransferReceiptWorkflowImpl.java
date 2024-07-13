package com.example.temporal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.Iterator;

public class TransferReceiptWorkflowImpl implements TransferReceiptWorkflow {

    private final EmbassyTransformDataActivity activities = Workflow.newActivityStub(
            EmbassyTransformDataActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(1))
                    .build()
    );

    @Override
    public void processJson(String jsonData) {
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode rootNode = objectMapper.readTree(jsonData);

            if (rootNode.isArray()) {
                Iterator<JsonNode> records = rootNode.elements();
                while (records.hasNext()) {
                    JsonNode record = records.next();
                    String eventType = record.path("header").path("eventType").asText();

                    if ("LOGICAL_MOVE".equals(eventType) || 
                        "LOGICAL_MOVE_ADJUST".equals(eventType) || 
                        "TRANSFER_RECEIPT".equals(eventType)) {
                        activities.processRecord(eventType, record.toString());
                    } else {
                        System.out.println("Unsupported event type. Skipping record.");
                    }
                }
            } else {
                System.out.println("Input JSON is not an array. Ending workflow.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
