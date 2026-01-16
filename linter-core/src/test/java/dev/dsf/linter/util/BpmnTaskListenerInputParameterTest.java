package dev.dsf.linter.util;

import dev.dsf.linter.classloading.ClassInspector;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import dev.dsf.linter.util.api.ApiVersion;
import dev.dsf.linter.util.api.ApiVersionHolder;
import dev.dsf.linter.util.resource.FhirAuthorizationCache;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.dsf.linter.bpmn.BpmnElementLinter.checkTaskListenerClasses;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Test class for validating input parameters (practitionerRole and practitioners)
 * and taskOutput field injections (taskOutputSystem, taskOutputCode, taskOutputVersion)
 * in task listeners that extend DefaultUserTaskListener (API v2).
 *
 * Tests various scenarios including:
 * - Task listener with practitionerRole having empty/null value
 * - Task listener with practitioners having empty/null value
 * - Task listener with both parameters having valid values
 * - Validation only for API v2 and DefaultUserTaskListener
 * - Different value formats (string, list, etc.)
 * - TaskOutput field injections completeness check
 * - TaskOutput FHIR resource validation (CodeSystem, code, placeholder)
 * - Success reporting for valid taskOutput fields
 */
class BpmnTaskListenerInputParameterTest {

    @TempDir
    Path tempDir;

    private File bpmnFile;
    private File projectRoot;
    private String processId;
    private String elementId;
    private List<BpmnElementLintItem> issues;
    private static final String DEFAULT_USER_TASK_LISTENER_V2 = "dev.dsf.bpe.v2.activity.DefaultUserTaskListener";

    @BeforeEach
    public void setUp() throws IOException {
        projectRoot = tempDir.toFile();
        bpmnFile = tempDir.resolve("test.bpmn").toFile();
        Files.createFile(bpmnFile.toPath());
        processId = "testProcess";
        elementId = "testUserTask";
        issues = new ArrayList<>();
    }

    /**
     * Helper method to assert that a lint item has a specific LintingType.
     */
    private void assertLintType(LintingType expectedType, BpmnElementLintItem item) {
        assertNotNull(item, "Item should not be null");
        assertEquals(expectedType, item.getType(), 
                "Item should have type " + expectedType);
    }

    private BpmnModelInstance createModelFromXml(String bpmnXml) {
        return Bpmn.readModelFromStream(
                new java.io.ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Should report ERROR when practitionerRole input parameter has empty value for DefaultUserTaskListener (API v2)")
    public void testPractitionerRoleWithEmptyValueForDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitionerRole"></camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL),
                    "Should report ERROR for empty practitionerRole when extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report WARN when practitionerRole input parameter has empty value for non-DefaultUserTaskListener (API v2)")
    public void testPractitionerRoleWithEmptyValueForNonDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.OtherListener";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitionerRole"></camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> warnings = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.WARN)
                    .toList();
            assertTrue(warnings.stream().anyMatch(item -> item.getType() == LintingType.BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL),
                    "Should report WARN for empty practitionerRole when NOT extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report ERROR when practitioners input parameter has empty value for DefaultUserTaskListener (API v2)")
    public void testPractitionersWithEmptyValueForDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitioners"></camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL),
                    "Should report ERROR for empty practitioners when extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report WARN when practitioners input parameter has empty value for non-DefaultUserTaskListener (API v2)")
    public void testPractitionersWithEmptyValueForNonDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.OtherListener";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitioners"></camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> warnings = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.WARN)
                    .toList();
            assertTrue(warnings.stream().anyMatch(item -> item.getType() == LintingType.BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL),
                    "Should report WARN for empty practitioners when NOT extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report success when practitionerRole has valid string value for DefaultUserTaskListener (API v2)")
    public void testPractitionerRoleWithValidStringValueForDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitionerRole">
                              <camunda:string>Practitioner/12345</camunda:string>
                            </camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> successItems = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                    .filter(item -> item.getDescription().contains("practitionerRole"))
                    .toList();
            assertFalse(successItems.isEmpty(),
                    "Should report success for practitionerRole with valid value when extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report success when practitionerRole has valid string value for non-DefaultUserTaskListener (API v2)")
    public void testPractitionerRoleWithValidStringValueForNonDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.OtherListener";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitionerRole">
                              <camunda:string>Practitioner/12345</camunda:string>
                            </camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> successItems = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                    .filter(item -> item.getDescription().contains("practitionerRole"))
                    .toList();
            assertFalse(successItems.isEmpty(),
                    "Should report success for practitionerRole with valid value when NOT extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report success when practitioners has valid list value for DefaultUserTaskListener (API v2)")
    public void testPractitionersWithValidListValueForDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitioners">
                              <camunda:list>
                                <camunda:value>Practitioner/12345</camunda:value>
                              </camunda:list>
                            </camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> successItems = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                    .filter(item -> item.getDescription().contains("practitioners"))
                    .toList();
            assertFalse(successItems.isEmpty(),
                    "Should report success for practitioners with valid list value when extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should report success when practitioners has valid list value for non-DefaultUserTaskListener (API v2)")
    public void testPractitionersWithValidListValueForNonDefaultUserTaskListener() throws Exception {
        // Given
        String className = "com.example.OtherListener";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitioners">
                              <camunda:list>
                                <camunda:value>Practitioner/12345</camunda:value>
                              </camunda:list>
                            </camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(false);
            mockedInspector.when(() -> ClassInspector.implementsInterface(eq(className), eq("dev.dsf.bpe.v2.activity.UserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> successItems = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                    .filter(item -> item.getDescription().contains("practitioners"))
                    .toList();
            assertFalse(successItems.isEmpty(),
                    "Should report success for practitioners with valid list value when NOT extending DefaultUserTaskListener");
        }
    }

    @Test
    @DisplayName("Should not validate input parameters for API v1")
    public void testNoValidationForApiV1() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitionerRole"></camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then - should not have any warnings for input parameters
            List<BpmnElementLintItem> warnings = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.WARN)
                    .filter(item -> item.getType() == LintingType.BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL
                            || item.getType() == LintingType.BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL)
                    .toList();
            assertTrue(warnings.isEmpty(),
                    "Should not validate input parameters for API v1");
        }
    }


    @Test
    @DisplayName("Should validate both practitionerRole and practitioners when both are present")
    public void testBothParametersValidation() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:inputOutput>
                            <camunda:inputParameter name="practitionerRole">
                              <camunda:string>Practitioner/12345</camunda:string>
                            </camunda:inputParameter>
                            <camunda:inputParameter name="practitioners"></camunda:inputParameter>
                          </camunda:inputOutput>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL),
                    "Should report ERROR for empty practitioners when extending DefaultUserTaskListener");
            
            List<BpmnElementLintItem> successItems = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                    .filter(item -> item.getDescription().contains("practitionerRole"))
                    .toList();
            assertFalse(successItems.isEmpty(),
                    "Should report success for practitionerRole with valid value");
        }
    }

    @Test
    @DisplayName("Should skip validation when input parameter is not present")
    public void testSkipValidationWhenParameterNotPresent() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then - should not have any errors or warnings for input parameters when they are not present
            List<BpmnElementLintItem> inputParamIssues = issues.stream()
                    .filter(item -> item.getType() == LintingType.BPMN_PRACTITIONER_ROLE_HAS_NO_VALUE_OR_NULL
                            || item.getType() == LintingType.BPMN_PRACTITIONERS_HAS_NO_VALUE_OR_NULL)
                    .toList();
            assertTrue(inputParamIssues.isEmpty(),
                    "Should not validate when input parameters are not present");
        }
    }

    // ========== TaskOutput Field Injections Validation Tests ==========

    @Test
    @DisplayName("Should report ERROR when only taskOutputSystem is set (incomplete fields)")
    public void testTaskOutputIncompleteOnlySystem() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputSystem">
                              <camunda:string>http://dsf.dev/fhir/CodeSystem/test</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS),
                    "Should report ERROR for incomplete taskOutput fields when only taskOutputSystem is set");
        }
    }

    @Test
    @DisplayName("Should report ERROR when only taskOutputCode is set (incomplete fields)")
    public void testTaskOutputIncompleteOnlyCode() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputCode">
                              <camunda:string>TEST_CODE</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS),
                    "Should report ERROR for incomplete taskOutput fields when only taskOutputCode is set");
        }
    }

    @Test
    @DisplayName("Should report ERROR when taskOutputSystem references unknown CodeSystem")
    public void testTaskOutputSystemUnknownCodeSystem() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String systemUrl = "http://dsf.dev/fhir/CodeSystem/unknown";
        String code = "TEST_CODE";
        String version = "#{version}";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputSystem">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputCode">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputVersion">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className, systemUrl, code, version);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class);
             MockedStatic<FhirAuthorizationCache> mockedCache = Mockito.mockStatic(FhirAuthorizationCache.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedCache.when(() -> FhirAuthorizationCache.containsSystem(eq(systemUrl))).thenReturn(false);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_SYSTEM_INVALID_FHIR_RESOURCE),
                    "Should report ERROR for unknown CodeSystem");
        }
    }

    @Test
    @DisplayName("Should report ERROR when taskOutputCode is unknown in CodeSystem")
    public void testTaskOutputCodeUnknownInCodeSystem() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String systemUrl = "http://dsf.dev/fhir/CodeSystem/test";
        String code = "UNKNOWN_CODE";
        String version = "#{version}";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputSystem">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputCode">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputVersion">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className, systemUrl, code, version);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class);
             MockedStatic<FhirAuthorizationCache> mockedCache = Mockito.mockStatic(FhirAuthorizationCache.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedCache.when(() -> FhirAuthorizationCache.containsSystem(eq(systemUrl))).thenReturn(true);
            mockedCache.when(() -> FhirAuthorizationCache.isUnknown(eq(systemUrl), eq(code))).thenReturn(true);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> errors = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.ERROR)
                    .toList();
            assertTrue(errors.stream().anyMatch(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_CODE_INVALID_FHIR_RESOURCE),
                    "Should report ERROR for unknown code in CodeSystem");
        }
    }

    @Test
    @DisplayName("Should report WARN when taskOutputVersion has no placeholder")
    public void testTaskOutputVersionNoPlaceholder() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String systemUrl = "http://dsf.dev/fhir/CodeSystem/test";
        String code = "TEST_CODE";
        String version = "1.0.0"; // No placeholder
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputSystem">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputCode">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputVersion">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className, systemUrl, code, version);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class);
             MockedStatic<FhirAuthorizationCache> mockedCache = Mockito.mockStatic(FhirAuthorizationCache.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedCache.when(() -> FhirAuthorizationCache.containsSystem(eq(systemUrl))).thenReturn(true);
            mockedCache.when(() -> FhirAuthorizationCache.isUnknown(eq(systemUrl), eq(code))).thenReturn(false);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> warnings = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.WARN)
                    .toList();
            assertTrue(warnings.stream().anyMatch(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_VERSION_NO_PLACEHOLDER),
                    "Should report WARN for taskOutputVersion without placeholder");
        }
    }

    @Test
    @DisplayName("Should report SUCCESS when all taskOutput fields are valid")
    public void testTaskOutputAllFieldsValid() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String systemUrl = "http://dsf.dev/fhir/CodeSystem/test";
        String code = "TEST_CODE";
        String version = "#{version}";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputSystem">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputCode">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                            <camunda:field name="taskOutputVersion">
                              <camunda:string>%s</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className, systemUrl, code, version);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class);
             MockedStatic<FhirAuthorizationCache> mockedCache = Mockito.mockStatic(FhirAuthorizationCache.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);
            mockedCache.when(() -> FhirAuthorizationCache.containsSystem(eq(systemUrl))).thenReturn(true);
            mockedCache.when(() -> FhirAuthorizationCache.isUnknown(eq(systemUrl), eq(code))).thenReturn(false);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then
            List<BpmnElementLintItem> successItems = issues.stream()
                    .filter(item -> item.getSeverity() == LinterSeverity.SUCCESS)
                    .toList();
            assertTrue(successItems.stream().anyMatch(item -> item.getDescription().contains("taskOutputSystem")),
                    "Should report SUCCESS for valid taskOutputSystem");
            assertTrue(successItems.stream().anyMatch(item -> item.getDescription().contains("taskOutputCode")),
                    "Should report SUCCESS for valid taskOutputCode");
            assertTrue(successItems.stream().anyMatch(item -> item.getDescription().contains("taskOutputVersion")),
                    "Should report SUCCESS for valid taskOutputVersion");
        }
    }

    @Test
    @DisplayName("Should skip taskOutput validation when no fields are set")
    public void testTaskOutputSkipWhenNoFieldsSet() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq(DEFAULT_USER_TASK_LISTENER_V2), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V2);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then - should not have any taskOutput-related errors
            List<BpmnElementLintItem> taskOutputErrors = issues.stream()
                    .filter(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS
                            || item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_SYSTEM_INVALID_FHIR_RESOURCE
                            || item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_CODE_INVALID_FHIR_RESOURCE
                            || item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_VERSION_NO_PLACEHOLDER)
                    .toList();
            assertTrue(taskOutputErrors.isEmpty(),
                    "Should not validate taskOutput fields when none are set");
        }
    }

    @Test
    @DisplayName("Should not validate taskOutput fields for API v1")
    public void testTaskOutputNoValidationForApiV1() throws Exception {
        // Given
        String className = "com.example.DefaultUserTaskListenerImpl";
        String bpmnXml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  id="Definitions_1"
                                  targetNamespace="http://bpmn.io/schema/bpmn">
                  <bpmn:process id="%s" isExecutable="true">
                    <bpmn:userTask id="%s">
                      <bpmn:extensionElements>
                        <camunda:taskListener class="%s" event="create">
                          <camunda:extensionElements>
                            <camunda:field name="taskOutputSystem">
                              <camunda:string>http://dsf.dev/fhir/CodeSystem/test</camunda:string>
                            </camunda:field>
                          </camunda:extensionElements>
                        </camunda:taskListener>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """, processId, elementId, className);
        
        BpmnModelInstance model = createModelFromXml(bpmnXml);
        UserTask userTask = model.getModelElementById(elementId);

        try (MockedStatic<ClassInspector> mockedInspector = Mockito.mockStatic(ClassInspector.class);
             MockedStatic<ApiVersionHolder> mockedApiHolder = Mockito.mockStatic(ApiVersionHolder.class)) {

            mockedInspector.when(() -> ClassInspector.classExists(eq(className), eq(projectRoot))).thenReturn(true);
            mockedInspector.when(() -> ClassInspector.isSubclassOf(eq(className), eq("dev.dsf.bpe.v1.activity.DefaultUserTaskListener"), eq(projectRoot))).thenReturn(true);
            mockedApiHolder.when(ApiVersionHolder::getVersion).thenReturn(ApiVersion.V1);

            // When
            checkTaskListenerClasses(userTask, elementId, issues, bpmnFile, processId, projectRoot);

            // Then - should not have any taskOutput-related errors
            List<BpmnElementLintItem> taskOutputErrors = issues.stream()
                    .filter(item -> item.getType() == LintingType.BPMN_USER_TASK_LISTENER_INCOMPLETE_TASK_OUTPUT_FIELDS
                            || item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_SYSTEM_INVALID_FHIR_RESOURCE
                            || item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_CODE_INVALID_FHIR_RESOURCE
                            || item.getType() == LintingType.BPMN_USER_TASK_LISTENER_TASK_OUTPUT_VERSION_NO_PLACEHOLDER)
                    .toList();
            assertTrue(taskOutputErrors.isEmpty(),
                    "Should not validate taskOutput fields for API v1");
        }
    }
}
