
package temporal.inventory.receiptsusecase;

import java.time.Duration;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.SearchAttributeKey;
import io.temporal.workflow.Workflow;

public class TransferReceiptWorkflowImpl implements TransferReceiptWorkflow {
        
        // set up logger
        private static final Logger log = LoggerFactory.getLogger(TransferReceiptWorkflowImpl.class);

        // transfer state stored in a local object and status pushed to Temporal Advanced Visibility
        static final SearchAttributeKey<String> TRANSFER_EVENT_TYPE = SearchAttributeKey.forKeyword("TRANSFER_EVENT_TYPE");
        static final SearchAttributeKey<String> TRANSFER_EVENT_STATUS = SearchAttributeKey.forKeyword("TRANSFER_EVENT_STATUS");
        static final SearchAttributeKey<String> CORRELATION_ID = SearchAttributeKey.forKeyword("CORRELATION_ID");

        // activity retry policy
        private final ActivityOptions options = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(10)
                        .setDoNotRetry(IllegalArgumentException.class.getName())
                        .build())
                .build();
        
        // Activity stubs

        private final EmbassyTransformValidateDataActivity tvactivities = Workflow.newActivityStub(
                EmbassyTransformValidateDataActivity.class, options);

        private final GEOValidateDataActivity evalactivities = Workflow.newActivityStub(
                        GEOValidateDataActivity.class, options);

        private final GEOTransformModelActivity geotransformactivities = Workflow.newActivityStub(
                        GEOTransformModelActivity.class, options);

        private final SaveStatusActivity ssactivities = Workflow.newActivityStub(
                        SaveStatusActivity.class, options);
                        
        private final GEOEnrichmentActivity enrichactivities = Workflow.newActivityStub(
                        GEOEnrichmentActivity.class, options);

        private final PublishActivity publishactivities = Workflow.newActivityStub(
                        PublishActivity.class, options);
                        
        private final EmbassyAcknowledgeDataActivity ackactivities = Workflow.newActivityStub(
                        EmbassyAcknowledgeDataActivity.class, options);

        @Override
        public void processEvents(String eventData) {
                ObjectMapper objectMapper = new ObjectMapper();

                try {
                        System.out.println("Processing Receipt Event - Child WF");
                        System.out.println("Input String:" + eventData);

                        String status = "ACKNOWLEDGEMENT";
                        ssactivities.savestatus(status);
                        Workflow.upsertTypedSearchAttributes(TRANSFER_EVENT_STATUS.valueSet(status));
                        
                        // Parse and process transfer record
                        JsonNode record = objectMapper.readTree(eventData);
                        String eventType = record.path("header").path("eventType").asText();
                        String correlationId = record.path("header").path("correlationId").asText();
                        Workflow.upsertTypedSearchAttributes(CORRELATION_ID.valueSet(correlationId));

                        Workflow.upsertTypedSearchAttributes(TRANSFER_EVENT_TYPE.valueSet(eventType));
                        tvactivities.processRecord(eventType);

                        status = "ENRICHMENT";
                        enrichactivities.enrichData(record);
                        ssactivities.savestatus(status);
                        Workflow.upsertTypedSearchAttributes(TRANSFER_EVENT_STATUS.valueSet(status));

                        status = "VALIDATION";
                        evalactivities.validateEvents();
                        ssactivities.savestatus(status);
                        Workflow.upsertTypedSearchAttributes(TRANSFER_EVENT_STATUS.valueSet(status));

                        status = "TRANFORMATION";
                        geotransformactivities.tranformtoeventmodel();
                        ssactivities.savestatus(status);
                        Workflow.upsertTypedSearchAttributes(TRANSFER_EVENT_STATUS.valueSet(status));

                        status = "PUBLISHED";
                        publishactivities.publishEvents();
                        ssactivities.savestatus(status);
                        Workflow.upsertTypedSearchAttributes(TRANSFER_EVENT_STATUS.valueSet(status));

                        

                        // if (rootNode.isArray()) {
                        //         Iterator<JsonNode> records = rootNode.elements();
                        //         while (records.hasNext()) {
                        //                 JsonNode record = records.next();
                        //                 String eventType = record.path("header").path("eventType").asText();

                        //                 if ("LOGICAL_MOVE".equals(eventType) ||
                        //                                 "LOGICAL_MOVE_ADJUST".equals(eventType) ||
                        //                                 "TRANSFER_RECEIPT".equals(eventType)) {
                        //                         // Workflow.sleep(Duration.ofSeconds(30));

                        //                         // Transfer Receipt Pipeline Workflow logic

                        //                         tvactivities.processRecord(eventType);

                        //                         enrichactivities.enrichData(record);

                        //                         String statusenriched = "ENRICHMENT";

                        //                         ssactivities.savestatus(statusenriched);

                        //                         String statusvalidation = "VALIDATION";
                        //                         evalactivities.validateEvents();
                        //                         ssactivities.savestatus(statusvalidation);

                        //                         String statustranformationgeo = "TRANFORMATION";
                        //                         geotransformactivities.tranformtoeventmodel();
                        //                         ssactivities.savestatus(statustranformationgeo);

                        //                         String statuspublish = "PUBLISHED";
                        //                         publishactivities.publishEvents();
                        //                         ssactivities.savestatus(statuspublish);

                        //                 } else {
                        //                         Async.procedure(() -> tvactivities.rejectRecord(eventType));
                        //                         // tvactivities.rejectRecord(eventType);
                        //                 }
                        //         }
                        // } else {
                        //         System.out.println("Input JSON is not an array. Ending workflow.");
                        // }
                } catch (JsonProcessingException ex) {
                        // Handle JSON processing exceptions
                        log.error("Failed to process JSON: {}", ex.getMessage(), ex);
                        // Provide user-friendly feedback or rethrow a custom exception
                        throw new CustomJsonProcessingException("An error occurred while processing the JSON data", ex);
                } // catch (RuntimeException ex) {
                  // Handle other runtime exceptions
                  // log.error("Runtime exception occurred: {}", ex.getMessage(), ex);
                  // Provide user-friendly feedback or rethrow a custom exception
                  // throw new CustomJsonProcessingException("A runtime error occurred while
                  // processing the event data", ex);
                  // }
        }

        class CustomJsonProcessingException extends RuntimeException {
                public CustomJsonProcessingException(String message, Throwable cause) {
                        super(message, cause);
                }
        }
}