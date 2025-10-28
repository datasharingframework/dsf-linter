package dev.dsf.linter;


import org.camunda.bpm.model.bpmn.impl.BpmnModelInstanceImpl;
import org.camunda.bpm.model.bpmn.impl.BpmnParser;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class BpmnLoadingTest {

    @Test
    public void testBpmnLoading() throws FileNotFoundException {
        BpmnParser parser = new BpmnParser();
        BpmnModelInstanceImpl model = parser.parseModelFromStream(
                this.getClass().getClassLoader().getResourceAsStream("bpmn/ping.bpmn"));
        // https://github.com/datasharingframework/dsf-process-tutorial/blob/82dc8b6c5eb7bb1a0edd64a816e9c69fc3b62dbe/tutorial-process/src/test/java/dev/dsf/process/tutorial/exercise_7/BpmnAndUserTaskListenerTest.java#L68
        System.out.println(model);
    }
}
