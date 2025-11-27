package dev.dsf.linter.util;

import dev.dsf.linter.bpmn.BpmnElementLinter;
import dev.dsf.linter.classloading.ClassInspector;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
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

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkTaskListenerClasses;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link BpmnElementLinter#checkTaskListenerClasses(UserTask, String, List, File, String, File)}
 *
 * Tests various scenarios including:
 * - UserTask with no extension elements
 * - UserTask with task listeners having missing class attributes
 * - UserTask with task listeners having existing/non-existing classes
 * - UserTask with task listeners implementing correct/incorrect interfaces for API v1 and v2
 */
class BpmnElementLinterTaskListenerTest {

    @TempDir
    Path tempDir;

    private File bpmnFile;
    private File projectRoot;
    private String processId;
    private String elementId;
    private List<BpmnElementLintItem> issues;

    @BeforeEach
    public void setUp() throws IOException {
        projectRoot = tempDir.toFile();
        bpmnFile = tempDir.resolve("test.bpmn").toFile();
        Files.createFile(bpmnFile.toPath());
        processId = "testProcess";
        elementId = "testUserTask";
        issues = new ArrayList<>();
    }

    @Test
    @DisplayName("Should not report issues for UserTask without extension elements")
    public void testUserTaskWithNoExtensionElements() {
        // Given
        UserTask userTask = mock(UserTask.class);
        when(userTask.getExtensionElements()).thenReturn(null);

        // When
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertTrue(issues.isEmpty(), "No issues should be reported for UserTask without extension elements");
    }

    @Test
    @DisplayName("Should not report issues for UserTask with empty extension elements")
    @SuppressWarnings("unchecked") // Suppress warning for mocking raw generic Query type
    public void testUserTaskWithEmptyExtensionElements() {
        // Given
        UserTask userTask = mock(UserTask.class);
        ExtensionElements extensionElements = mock(ExtensionElements.class);
        when(userTask.getExtensionElements()).thenReturn(extensionElements);
        when(extensionElements.getElementsQuery()).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class)).thenReturn(mock(org.camunda.bpm.model.bpmn.Query.class));
        when(extensionElements.getElementsQuery().filterByType(CamundaTaskListener.class).list()).thenReturn(new ArrayList<>());

        // When
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertTrue(issues.isEmpty(), "No issues should be reported for UserTask with empty extension elements");
    }

    @Test
    @DisplayName("Should report missing class attribute when task listener has null class")
    public void testTaskListenerWithMissingClassAttribute() {
        // Given
        UserTask userTask = createUserTaskWithTaskListener(null);

        // When
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertEquals(1, issues.size());
        assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeLintItem.class, issues.get(0));
    }

    @Test
    @DisplayName("Should report missing class attribute when task listener has empty class")
    public void testTaskListenerWithEmptyClassAttribute() {
        // Given
        UserTask userTask = createUserTaskWithTaskListener("");

        // When
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertEquals(1, issues.size());
        assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeLintItem.class, issues.get(0));
    }

    @Test
    @DisplayName("Should report missing class attribute when task listener has whitespace-only class")
    public void testTaskListenerWithWhitespaceOnlyClassAttribute() {
        // Given
        UserTask userTask = createUserTaskWithTaskListener("   ");

        // When
        checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

        // Then
        assertEquals(1, issues.size());
        assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeLintItem.class, issues.get(0));
    }

    @Test
    @DisplayName("Should report class not found when task listener class does not exist")
    public void testTaskListenerWithNonExistingClass() {
        // Given
        String className = "com.example.NonExistentListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class)) {
            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(false);


            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0));
            assertInstanceOf(BpmnUserTaskListenerJavaClassNotFoundLintItem.class, issues.get(1));
        }
    }

    // Assuming the necessary static imports for ClassInspector and BpmnElementLinter are present

    @Test
    @DisplayName("Should lint task listener extending DefaultUserTaskListener (API v2)")
    public void testTaskListenerExtendsDefaultV2() {
        // Given
        String className = "com.example.ExtendingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(false);

            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(3, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(2)); // Correct interface implemented
        }
    }

    @Test
    @DisplayName("Should lint task listener extending DefaultUserTaskListener (API v1)")
    public void testTaskListenerExtendsDefaultV1() {
        // Given
        String className = "com.example.ExtendingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("org.camunda.bpm.engine.delegate.TaskListener"), eq(projectRoot))).thenReturn(false);


            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);



            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(3, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(2)); // Correct interface implemented
        }
    }

    @Test
    @DisplayName("Should lint task listener implementing UserTaskListener (API v2)")
    public void testTaskListenerImplementsInterfaceV2() {
        // Given
        String className = "com.example.ImplementingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);

            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(3, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(2)); // Correct interface implemented
        }
    }

    @Test
    @DisplayName("Should lint task listener implementing TaskListener (API v1)")
    public void testTaskListenerImplementsInterfaceV1() {
        // Given
        String className = "com.example.ImplementingListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("org.camunda.bpm.engine.delegate.TaskListener"), eq(projectRoot))).thenReturn(true);

            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(3, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(2)); // Correct interface implemented
        }
    }

    @Test
    @DisplayName("Should report interface error when task listener class exists but doesn't implement UserTaskListener (API v2)")
    public void testTaskListenerWithExistingClassWrongInterfaceV2() {
        // Given
        String className = "com.example.InvalidListener";
        UserTask userTask = createUserTaskWithTaskListener(className);


        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(false);


            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(3, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found
            assertInstanceOf(BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem.class, issues.get(2));
        }
    }

    @Test
    @DisplayName("Should report error when task listener class exists but doesn't extend or implement required classes (API v1)")
    public void testTaskListenerWithExistingClassWrongSubclassV1() {
        // Given
        String className = "com.example.InvalidListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("org.camunda.bpm.engine.delegate.TaskListener"), eq(projectRoot))).thenReturn(false);

            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(3, issues.size());

            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found

            assertInstanceOf(BpmnUserTaskListenerNotExtendingOrImplementingRequiredClassLintItem.class, issues.get(2));
        }
    }


    @Test
    @DisplayName("Should only check class attribute and existence for unknown API version")
    public void testTaskListenerWithUnknownApiVersion() {
        // Given
        String className = "com.example.SomeListener";
        UserTask userTask = createUserTaskWithTaskListener(className);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);

            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.UNKNOWN);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            // For UNKNOWN API version, validateTaskListenerInheritance returns early without adding any lint item
            assertEquals(2, issues.size());
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0)); // Class attribute provided
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1)); // Class found
        }
    }

    @Test
    @DisplayName("Should lint multiple task listeners with different lint outcomes")
    public void testTaskListenerWithMultipleListeners() {
        // Given
        UserTask userTask = createUserTaskWithMultipleTaskListeners(
                "com.example.ValidListener",
                "com.example.InvalidListener",
                ""
        );

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq("com.example.ValidListener"), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.classExists(eq("com.example.InvalidListener"), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq("com.example.ValidListener"), eq("dev.dsf.bpe.v2.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq("com.example.ValidListener"), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);

            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            assertEquals(6, issues.size());

            // 2. Verify the items for each listener in the correct order
            // Listener 1 (Valid)
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(0));
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(1));
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(2));
            // Listener 2 (Class Not Found)
            assertInstanceOf(BpmnElementLintItemSuccess.class, issues.get(3));
            assertInstanceOf(BpmnUserTaskListenerJavaClassNotFoundLintItem.class, issues.get(4));
            // Listener 3 (Missing Attribute)
            assertInstanceOf(BpmnUserTaskListenerMissingClassAttributeLintItem.class, issues.get(5));
        }
    }

    @Test
    @DisplayName("Should lint task listeners from real BPMN file (merge.BPMN)")
    public void testWithRealBpmnFileFromResources() throws IOException {
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
                    checkTaskListenerClasses(releaseDataSet, "releaseDataSet", issues, mergeBpmnFile, "medizininformatik-initiativede_mergeDataSharing", projectRoot);

                    // Then - Should lint the actual listener class from the merge.BPMN
                    assertFalse(issues.isEmpty(), "Issues should be found when linting real BPMN file");

                    // Check that we have lint items for the listener class
                    boolean hasListenerLint = issues.stream()
                            .anyMatch(item -> item.getDescription().contains("ReleaseMergedDataSetListener") ||
                                    item.getDescription().contains("de.medizininformatik_initiative.process.data_sharing.questionnaire.ReleaseMergedDataSetListener"));

                    assertTrue(hasListenerLint, "Should lint the ReleaseMergedDataSetListener class from merge.BPMN");
                }
            }
        }
    }

    // Helper methods

    @SuppressWarnings("unchecked") // Suppress warning for mocking raw generic Query type
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

    @SuppressWarnings("unchecked") // Suppress warning for mocking raw generic Query type
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