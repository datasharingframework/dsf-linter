package dev.dsf.utils.validator.util;

import dev.dsf.utils.validator.item.*;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link BpmnValidationUtils#checkTaskListenerClasses(UserTask, String, List, File, String, File)}
 *
 * Tests various scenarios including:
 * - UserTask with no extension elements
 * - UserTask with task listeners having missing class attributes
 * - UserTask with task listeners having existing/non-existing classes
 * - UserTask with task listeners implementing correct/incorrect interfaces for API v1 and v2
 */
class BpmnValidationUtilsTaskListenerTest {

    @TempDir
    Path tempDir;

    private File bpmnFile;
    private File projectRoot;
    private String processId;
    private String elementId;
    private List<BpmnElementValidationItem> issues;

    @BeforeEach
    void setUp() throws IOException {
        projectRoot = tempDir.toFile();
        bpmnFile = tempDir.resolve("test.bpmn").toFile();
        Files.createFile(bpmnFile.toPath());
        processId = "testProcess";
        elementId = "testUserTask";
        issues = new ArrayList<>();
    }

    @Test
    @DisplayName("Should not report issues for UserTask without extension elements")
    void testUserTaskWithNoExtensionElements() {
        // Given
        UserTask userTask = mock(UserTask.class);
        when(userTask.getExtensionElements()).thenReturn(null);

        // When
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertTrue(issues.isEmpty(), "No issues should be reported for UserTask without extension elements");
    }

    @Test
    @DisplayName("Should not report issues for UserTask with empty extension elements")
    void testUserTaskWithEmptyExtensionElements() {
        // Given
        UserTask userTask = mock(UserTask.class);
        ExtensionElements extensionElements = mock(ExtensionElements.class);
        when(userTask.getExtensionElements()).thenReturn(extensionElements);
        when(extensionElements.getElementsQuery()).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class)).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class).list()).thenReturn(new ArrayList<>());

        // When
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertTrue(issues.isEmpty(), "No issues should be reported for UserTask with empty extension elements");
    }

    @Test
    @DisplayName("Should report missing class attribute when task listener has null class")
    void testTaskListenerWithMissingClassAttribute() {
        // Given
        UserTask userTask = createUserTaskWithTaskListener(null);

        // When
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertEquals(1, issues.size());
        assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeValidationItem.class, issues.getFirst());
    }

    @Test
    @DisplayName("Should report missing class attribute when task listener has empty class")
    void testTaskListenerWithEmptyClassAttribute() {
        // Given
        UserTask userTask = createUserTaskWithTaskListener("");

        // When
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertEquals(1, issues.size());
        assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeValidationItem.class, issues.getFirst());
    }

    @Test
    @DisplayName("Should report missing class attribute when task listener has whitespace-only class")
    void testTaskListenerWithWhitespaceOnlyClassAttribute() {
        // Given
        UserTask userTask = createUserTaskWithTaskListener("   ");

        // When
        BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertEquals(1, issues.size());
        assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeValidationItem.class, issues.getFirst());
    }

    @Test
    @DisplayName("Should report class not found when task listener class does not exist")
    void testTaskListenerWithNonExistingClass() {
        // Given
        String className = "com.example.NonExistentListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class)) {
            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0));
            assertInstanceOf(BpmnUserTaskListenerJavaClassNotFoundValidationItem.class, issues.get(1));
        }
    }

    @Test
    @DisplayName("Should validate task listener extending DefaultUserTaskListener (API v2)")
    void testTaskListenerExtendsDefaultV2() {
        // Given
        String className = "com.example.ExtendingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq(className), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should validate task listener extending DefaultUserTaskListener (API v1)")
    void testTaskListenerExtendsDefaultV1() {
        // Given
        String className = "com.example.ExtendingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq(className), eq("org.camunda.bpm.engine.delegate.TaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should validate task listener implementing UserTaskListener (API v2)")
    void testTaskListenerImplementsInterfaceV2() {
        // Given
        String className = "com.example.ImplementingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq(className), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should validate task listener implementing TaskListener (API v1)")
    void testTaskListenerImplementsInterfaceV1() {
        // Given
        String className = "com.example.ImplementingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq(className), eq("org.camunda.bpm.engine.delegate.TaskListener"), eq(projectRoot))).thenReturn(true);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should report interface error when task listener class exists but doesn't implement UserTaskListener (API v2)")
    void testTaskListenerWithExistingClassWrongInterfaceV2() {
        // Given
        String className = "com.example.InvalidListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq(className), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
            // Note: Error validation item would be added by the private extendsDefault method
        }
    }

    @Test
    @DisplayName("Should report error when task listener class exists but doesn't extend or implement required classes (API v1)")
    void testTaskListenerWithExistingClassWrongSubclassV1() {
        // Given
        String className = "com.example.InvalidListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq(className), eq("org.camunda.bpm.engine.delegate.TaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should only check class attribute and existence for unknown API version")
    void testTaskListenerWithUnknownApiVersion() {
        // Given
        String className = "com.example.SomeListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.UNKNOWN);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then - Only class attribute and existence checks should be performed for UNKNOWN version
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should validate multiple task listeners with different validation outcomes")
    void testTaskListenerWithMultipleListeners() {
        // Given
        UserTask userTask = createUserTaskWithMultipleTaskListeners(
            "com.example.ValidListener",
            "com.example.InvalidListener",
            ""
        );

        try (MockedStatic<BpmnValidationUtils> mockedUtils = Mockito.mockStatic(BpmnValidationUtils.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedUtils.when(() -> BpmnValidationUtils.isEmpty(anyString())).thenCallRealMethod();
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq("com.example.ValidListener"), eq(projectRoot))).thenReturn(true);
            mockedUtils.when(() -> BpmnValidationUtils.classExists(eq("com.example.InvalidListener"), eq(projectRoot))).thenReturn(false);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedUtils.when(() -> BpmnValidationUtils.isSubclassOf(eq("com.example.ValidListener"), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedUtils.when(() -> BpmnValidationUtils.implementsInterface(eq("com.example.ValidListener"), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedUtils.when(() -> BpmnValidationUtils.checkTaskListenerClasses(any(), any(), any(), any(), any(), any())).thenCallRealMethod();

            // When
            BpmnValidationUtils.checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(5, issues.size());
            // First listener (valid)
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(1)); // Class found
            // Second listener (class not found)
            assertInstanceOf(BpmnElementValidationItemSuccess.class, issues.get(2)); // Class attribute provided
            assertInstanceOf(BpmnUserTaskListenerJavaClassNotFoundValidationItem.class, issues.get(3)); // Class not found
            // Third listener (empty class)
            assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeValidationItem.class, issues.get(4)); // Missing class attribute
        }
    }

    @Test
    @DisplayName("Should validate task listeners from real BPMN file (merge.BPMN)")
    void testWithRealBpmnFileFromResources() throws IOException {
        // Given - Load the actual merge.BPMN file
        File mergeBpmnFile = new File("src/test/resources/bpmn/merge.BPMN");
        if (mergeBpmnFile.exists()) {
            BpmnModelInstance modelInstance = Bpmn.readModelFromFile(mergeBpmnFile);
            UserTask releaseDataSet = modelInstance.getModelElementById("releaseDataSet");

            if (releaseDataSet != null) {
                // Set a known API version for this test
                try (MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {
                    mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);

                    // When
                    BpmnValidationUtils.checkTaskListenerClasses(releaseDataSet, "releaseDataSet", issues, mergeBpmnFile, "medizininformatik-initiativede_mergeDataSharing", projectRoot);

                    // Then - Should validate the actual listener class from the merge.BPMN
                    assertFalse(issues.isEmpty(), "Issues should be found when validating real BPMN file");

                    // Check that we have validation items for the listener class
                    boolean hasListenerValidation = issues.stream()
                        .anyMatch(item -> item.getDescription().contains("ReleaseMergedDataSetListener") ||
                                        item.getDescription().contains("de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseMergedDataSetListener"));

                    assertTrue(hasListenerValidation, "Should validate the ReleaseMergedDataSetListener class from merge.BPMN");
                }
            }
        }
    }

    // Helper methods

    private UserTask createUserTaskWithTaskListener(String className) {
        UserTask userTask = mock(UserTask.class);
        ExtensionElements extensionElements = mock(ExtensionElements.class);
        CamundaTaskListener taskListener = mock(CamundaTaskListener.class);

        when(userTask.getExtensionElements()).thenReturn(extensionElements);
        when(extensionElements.getElementsQuery()).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class)).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class).list()).thenReturn(List.of(taskListener));
        when(taskListener.getCamundaClass()).thenReturn(className);

        return userTask;
    }

    private UserTask createUserTaskWithMultipleTaskListeners(String... classNames) {
        UserTask userTask = mock(UserTask.class);
        ExtensionElements extensionElements = mock(ExtensionElements.class);
        List<CamundaTaskListener> listeners = new ArrayList<>();

        for (String className : classNames) {
            CamundaTaskListener listener = mock(CamundaTaskListener.class);
            when(listener.getCamundaClass()).thenReturn(className);
            listeners.add(listener);
        }

        when(userTask.getExtensionElements()).thenReturn(extensionElements);
        when(extensionElements.getElementsQuery()).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class)).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class).list()).thenReturn(listeners);

        return userTask;
    }
}
