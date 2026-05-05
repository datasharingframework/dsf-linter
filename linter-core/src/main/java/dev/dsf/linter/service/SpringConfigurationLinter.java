package dev.dsf.linter.service;

import dev.dsf.linter.logger.Logger;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.item.AbstractLintItem;
import dev.dsf.linter.output.item.PluginLintItem;
import dev.dsf.linter.plugin.PluginDefinitionDiscovery.PluginAdapter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.MessageEventDefinition;
import org.camunda.bpm.model.bpmn.instance.SendTask;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.bpmn.instance.ThrowEvent;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaExecutionListener;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaTaskListener;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates that every class referenced as a Camunda delegate or listener in a
 * BPMN file is provided as a {@code @Bean} in at least one of the
 * {@code @Configuration} classes registered via
 * {@code ProcessPluginDefinition#getSpringConfigurations()}.
 *
 * <p>Background:</p>
 * <p>
 * In the DSF environment the Camunda engine does not instantiate Java delegate
 * or listener classes directly. Spring creates those instances via {@code @Bean}
 * methods declared in {@code @Configuration} classes. For those beans to be
 * available at runtime, every BPMN-referenced class must have a corresponding
 * {@code @Bean} method in a configuration class that is explicitly returned by
 * {@code ProcessPluginDefinition#getSpringConfigurations()}.
 * A missing entry typically surfaces as a {@code BeanCreationException} or
 * {@code ClassNotFoundException} only at deployment time.
 * </p>
 *
 * <p>Emitted lint items:</p>
 * <ul>
 *   <li><b>ERROR</b> {@link LintingType#PLUGIN_DEFINITION_SPRING_CONFIGURATION_MISSING}
 *       for every BPMN-referenced class that is not provided as a {@code @Bean}
 *       in any registered {@code @Configuration} class.</li>
 *   <li><b>SUCCESS</b> when every BPMN delegate/listener reference is covered
 *       by a {@code @Bean} in a registered configuration, or when no BPMN
 *       delegate/listener references exist.</li>
 * </ul>
 */
public final class SpringConfigurationLinter {

    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    /** Fully qualified name of the Spring {@code @Bean} annotation. */
    private static final String BEAN_ANNOTATION = "org.springframework.context.annotation.Bean";

    private SpringConfigurationLinter() {
    }

    /**
     * Runs the Spring-configuration validation.
     *
     * @param adapter   the plugin adapter used to invoke
     *                  {@code getSpringConfigurations()} reflectively.
     * @param bpmnFiles BPMN files belonging to this plugin (used to collect
     *                  delegate/listener class references).
     * @param projectDir the extracted project root (used only to derive the
     *                  location label for lint items; may be {@code null}).
     * @param logger    logger for diagnostic output (may be {@code null}).
     * @return list of lint items; never {@code null}.
     */
    public static List<AbstractLintItem> lint(PluginAdapter adapter,
                                              List<File> bpmnFiles,
                                              File projectDir,
                                              Logger logger) {
        List<AbstractLintItem> items = new ArrayList<>();
        if (adapter == null) {
            return items;
        }

        String pluginLocation = adapter.sourceClass().getName();
        File locationFile = projectDir != null ? projectDir : new File(".");

        // Step 1: Get the registered @Configuration classes
        List<Class<?>> registered;
        try {
            registered = adapter.getSpringConfigurations();
        } catch (RuntimeException e) {
            if (logger != null) {
                logger.debug("Could not invoke getSpringConfigurations() on plugin '"
                        + pluginLocation + "': " + e.getMessage());
            }
            registered = Collections.emptyList();
        }
        if (registered == null) {
            registered = Collections.emptyList();
        }

        // Step 2: Collect all BPMN-referenced delegate/listener class names
        Set<String> referencedBpmnClasses = collectBpmnDelegateReferences(bpmnFiles, logger);
        if (logger != null) {
            logger.debug("Spring configuration check: " + referencedBpmnClasses.size()
                    + " BPMN delegate/listener class reference(s) found.");
        }

        if (referencedBpmnClasses.isEmpty()) {
            items.add(PluginLintItem.success(
                    locationFile,
                    pluginLocation,
                    "No BPMN delegate/listener references found; Spring configuration check skipped."
            ));
            return items;
        }

        // Step 3: Build a map of configClassName -> set of @Bean return-type names
        //         from the REGISTERED configurations only.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Map<String, Set<String>> registeredConfigBeans = new LinkedHashMap<>();
        for (Class<?> configClass : registered) {
            if (configClass == null) {
                continue;
            }
            Set<String> beanTypes = extractBeanReturnTypes(configClass, logger);
            registeredConfigBeans.put(configClass.getName(), beanTypes);
            if (logger != null) {
                logger.debug("Registered @Configuration '" + configClass.getName()
                        + "' exposes " + beanTypes.size() + " @Bean type(s).");
            }
        }

        // Step 4: For each BPMN-referenced class check whether any registered
        //         configuration provides a @Bean for it (exact type or supertype).
        List<String> uncoveredClasses = new ArrayList<>();
        for (String refClass : referencedBpmnClasses) {
            boolean covered = false;
            for (Set<String> beanTypes : registeredConfigBeans.values()) {
                if (configProvidesClass(beanTypes, refClass, cl)) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                uncoveredClasses.add(refClass);
            }
        }

        // Step 5: Emit one ERROR per uncovered BPMN-referenced class
        for (String uncoveredClass : uncoveredClasses) {
            items.add(new PluginLintItem(
                    LinterSeverity.ERROR,
                    LintingType.PLUGIN_DEFINITION_SPRING_CONFIGURATION_MISSING,
                    locationFile,
                    uncoveredClass,
                    "BPMN-referenced class '" + simpleName(uncoveredClass) + "' ("
                            + uncoveredClass + ") is not provided as a @Bean "
                            + "in any of the " + registeredConfigBeans.size()
                            + " @Configuration class(es) registered via getSpringConfigurations() "
                            + "of plugin '" + adapter.getName() + "'. "
                            + "Add a @Bean method returning " + simpleName(uncoveredClass)
                            + " to one of the registered @Configuration classes."
            ));
        }

        if (uncoveredClasses.isEmpty()) {
            items.add(PluginLintItem.success(
                    locationFile,
                    pluginLocation,
                    "getSpringConfigurations() registers " + registered.size()
                            + " @Configuration class(es); all " + referencedBpmnClasses.size()
                            + " BPMN delegate/listener reference(s) are covered by a registered @Bean."
            ));
        }

        return items;
    }

    // ==================== BPMN REFERENCE COLLECTION ====================

    private static Set<String> collectBpmnDelegateReferences(List<File> bpmnFiles, Logger logger) {
        Set<String> refs = new LinkedHashSet<>();
        if (bpmnFiles == null) {
            return refs;
        }
        for (File bpmnFile : bpmnFiles) {
            if (bpmnFile == null || !bpmnFile.isFile()) {
                continue;
            }
            try {
                BpmnModelInstance model = Bpmn.readModelFromFile(bpmnFile);
                collectFromModel(model, refs);
            } catch (Exception e) {
                if (logger != null) {
                    logger.debug("Could not parse BPMN file for Spring config check: "
                            + bpmnFile.getAbsolutePath() + " - " + e.getMessage());
                }
            }
        }
        return refs;
    }

    private static void collectFromModel(BpmnModelInstance model, Set<String> refs) {
        for (ServiceTask t : model.getModelElementsByType(ServiceTask.class)) {
            addIfNotBlank(t.getCamundaClass(), refs);
            addListenerClasses(t, refs);
        }
        for (SendTask t : model.getModelElementsByType(SendTask.class)) {
            addIfNotBlank(t.getCamundaClass(), refs);
            addListenerClasses(t, refs);
        }
        for (ThrowEvent event : model.getModelElementsByType(ThrowEvent.class)) {
            addDirectCamundaClass(event, refs);
            addMessageEventClasses(event.getEventDefinitions(), refs);
            addListenerClasses(event, refs);
        }
        for (BaseElement e : model.getModelElementsByType(BaseElement.class)) {
            addListenerClasses(e, refs);
        }
    }

    private static void addDirectCamundaClass(BaseElement element, Set<String> refs) {
        String direct = element.getAttributeValueNs(CAMUNDA_NS, "class");
        addIfNotBlank(direct, refs);
    }

    private static void addMessageEventClasses(Collection<EventDefinition> defs, Set<String> refs) {
        if (defs == null) return;
        for (EventDefinition d : defs) {
            if (d instanceof MessageEventDefinition med) {
                addIfNotBlank(med.getCamundaClass(), refs);
            }
        }
    }

    private static void addListenerClasses(BaseElement element, Set<String> refs) {
        try {
            for (CamundaExecutionListener l : element.getChildElementsByType(CamundaExecutionListener.class)) {
                addIfNotBlank(l.getCamundaClass(), refs);
            }
        } catch (RuntimeException ignored) {
            // Element does not support execution listeners – skip silently.
        }
        try {
            for (CamundaTaskListener l : element.getChildElementsByType(CamundaTaskListener.class)) {
                addIfNotBlank(l.getCamundaClass(), refs);
            }
        } catch (RuntimeException ignored) {
            // Element does not support task listeners – skip silently.
        }
    }

    private static void addIfNotBlank(String v, Set<String> target) {
        if (v != null && !v.isBlank()) {
            target.add(v.trim());
        }
    }

    // ==================== @Bean DISCOVERY ON REGISTERED CONFIGS ====================

    /**
     * Returns the set of return-type names of all {@code @Bean}-annotated methods
     * declared directly on {@code configClass}.
     */
    private static Set<String> extractBeanReturnTypes(Class<?> configClass, Logger logger) {
        Set<String> beanReturnTypes = new LinkedHashSet<>();
        try {
            for (Method m : configClass.getDeclaredMethods()) {
                if (hasAnnotationByName(m.getAnnotations())) {
                    Class<?> rt = m.getReturnType();
                    if (rt != void.class && rt != Void.class) {
                        beanReturnTypes.add(rt.getName());
                    }
                }
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.debug("Could not read @Bean methods of '"
                        + configClass.getName() + "': " + t.getMessage());
            }
        }
        return beanReturnTypes;
    }

    private static boolean hasAnnotationByName(Annotation[] annotations) {
        if (annotations == null) return false;
        for (Annotation a : annotations) {
            if (a == null) continue;
            Class<? extends Annotation> type = a.annotationType();
            if (type != null && SpringConfigurationLinter.BEAN_ANNOTATION.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the set of {@code @Bean} return-type names includes
     * {@code referencedClass} exactly, or if any listed type is a supertype of
     * {@code referencedClass} (to handle abstract/interface return types).
     */
    private static boolean configProvidesClass(Set<String> beanReturnTypes,
                                               String referencedClass,
                                               ClassLoader cl) {
        if (beanReturnTypes.contains(referencedClass)) {
            return true;
        }
        if (cl == null) {
            return false;
        }
        Class<?> ref;
        try {
            ref = Class.forName(referencedClass, false, cl);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
        for (String rt : beanReturnTypes) {
            try {
                Class<?> returnType = Class.forName(rt, false, cl);
                if (returnType.isAssignableFrom(ref)) {
                    return true;
                }
            } catch (ClassNotFoundException | LinkageError ignored) {
                // ignore and continue
            }
        }
        return false;
    }

    private static String simpleName(String fqn) {
        int i = fqn.lastIndexOf('.');
        return (i >= 0) ? fqn.substring(i + 1) : fqn;
    }
}
