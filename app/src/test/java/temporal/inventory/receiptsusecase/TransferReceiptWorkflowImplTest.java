package temporal.inventory.receiptsusecase;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.util.UUID;

@SpringBootTest(
        classes = {
                TransferReceiptWorkflowImplTest.Configuration.class,
        })
// @ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@EnableAutoConfiguration()
@DirtiesContext
public class TransferReceiptWorkflowImplTest {
        @Autowired
        ConfigurableApplicationContext applicationContext;

        @Autowired
        TestWorkflowEnvironment testWorkflowEnvironment;

        @Autowired
        WorkflowClient workflowClient;

        @Value("${spring.temporal.workers[0].task-queue}")
        String taskQueue;

        @AfterEach
        public void after() {
                testWorkflowEnvironment.close();
        }

        @BeforeEach
        void beforeEach() {
                applicationContext.start();
        }
        @Test
        public void givenValidArgs_itShouldExecuteWorkflow() {
                // poor man's fixture
                String filePath = "/Users/mnichols/dev/temporal-inventory-pipeline-receiptsusecase/TransferEvents.json";

                // Read the file content into a string
                String eventData = "";
                try {
                        eventData = FileUtils.readFileAsString(filePath);
                } catch (IOException e) {
                        Assertions.fail(e);
                }
                String wid = UUID.randomUUID().toString();
                TransferReceiptWorkflow sut =
                        workflowClient.newWorkflowStub(TransferReceiptWorkflow.class,
                                WorkflowOptions.newBuilder().setTaskQueue(taskQueue).setWorkflowId(wid).build());
                String finalEventData = eventData;
                Assertions.assertDoesNotThrow(()->{
                        WorkflowClient.start(sut::processEvents, finalEventData);
                });
        }

        @ComponentScan
        public static class Configuration {

        }
}