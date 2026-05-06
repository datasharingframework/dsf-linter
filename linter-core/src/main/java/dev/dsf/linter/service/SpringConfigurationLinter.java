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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validates DSF process plugins against how Spring provides Camunda delegates
 * and listeners: bean registration and (for covered beans) {@code @Scope} plus
 * mutable instance fields.
 *
 * <h2>Background</h2>
 * <p>
 * In the DSF environment the Camunda engine does not instantiate Java delegate
 * or listener classes directly. Spring creates those instances via {@code @Bean}
 * methods on {@code @Configuration} classes that must be returned by
 * {@code ProcessPluginDefinition#getSpringConfigurations()}. A missing
 * {@code @Bean} for a BPMN-referenced class often appears only at deployment
 * time as a {@code BeanCreationException} or {@code ClassNotFoundException}.
 * </p>
 * <p>
 * DSF best practice is <strong>prototype</strong> scope for such beans. Omitting
 * {@code @Scope} defaults Spring to <strong>singleton</strong>, which is unsafe if
 * the implementation ever holds mutable instance state (non-{@code static},
 * non-{@code final} fields) across concurrent process executions.
 * </p>
 *
 * <h2>Validation pipeline (see {@link #lint})</h2>
 * <ol>
 *   <li>Load registered {@code @Configuration} classes from
 *       {@code getSpringConfigurations()}.</li>
 *   <li>Scan plugin BPMN files for {@code camunda:class} references (service
 *       and send tasks, throw events with message definitions, execution and
 *       task listeners).</li>
 *   <li>Build {@code @Bean} return types per configuration class; check each
 *       referenced class is covered (exact FQN or supertype assignability).</li>
 *   <li>For each <em>covered</em> class, find the covering {@code @Bean} method
 *       and inspect its {@code @Scope} and the implementation class for mutable
 *       instance fields.</li>
 * </ol>
 *
 * <h2>Emitted {@link LintingType} values</h2>
 * <h3>Bean registration</h3>
 * <ul>
 *   <li><b>ERROR</b> – {@link LintingType#PLUGIN_DEFINITION_SPRING_CONFIGURATION_MISSING}:
 *       BPMN references a class that is not provided as a {@code @Bean} in any
 *       registered configuration.</li>
 *   <li><b>SUCCESS</b> – {@link LintingType#SUCCESS}: all references are
 *       covered by a registered {@code @Bean} and there are no registration
 *       errors (a summary item is also emitted when the reference set is
 *       non-empty and fully covered; see {@link #lint}).</li>
 *   <li>When there are no BPMN delegate/listener references, a single success
 *       item is emitted and the run returns early (scope checks do not run).</li>
 * </ul>
 * <h3>Scope and mutable state (covered classes only)</h3>
 * <ul>
 *   <li><b>SUCCESS</b> – {@link LintingType#SPRING_BEAN_SCOPE_PROTOTYPE}:
 *       the covering {@code @Bean} has {@code @Scope} with value
 *       {@code "prototype"} (recommended for Camunda hooks).</li>
 *   <li><b>ERROR</b> – {@link LintingType#SPRING_BEAN_SCOPE_MUTABLE_SINGLETON}:
 *       the bean is effectively singleton (no {@code @Scope} or explicit
 *       non-prototype scope) <em>and</em> the implementation class has mutable
 *       instance fields. Emitted before the corresponding scope warning for that
 *       reference.</li>
 *   <li><b>WARN</b> – {@link LintingType#SPRING_BEAN_SCOPE_MISSING}:
 *       the covering {@code @Bean} has no {@code @Scope} (Spring defaults to
 *       singleton).</li>
 *   <li><b>WARN</b> – {@link LintingType#SPRING_BEAN_SCOPE_SINGLETON_EXPLICIT}:
 *       the covering {@code @Bean} has an explicit non-prototype
 *       {@code @Scope} (e.g. {@code "singleton"}); the implementation must be
 *       provably stateless for a shared instance.</li>
 * </ul>
 */
public final class SpringConfigurationLinter {

    private static final String CAMUNDA_NS = "http://camunda.org/schema/1.0/bpmn";

    /** Fully qualified name of the Spring {@code @Bean} annotation. */
    private static final String BEAN_ANNOTATION = "org.springframework.context.annotation.Bean";

    /** Fully qualified name of the Spring {@code @Scope} annotation. */
    private static final String SCOPE_ANNOTATION = "org.springframework.context.annotation.Scope";

    private SpringConfigurationLinter() {
    }

    /**
     * Runs Spring configuration validation: {@code @Bean} coverage for all BPMN
     * delegate/listener references, then for each <em>covered</em> class an
     * optional scope/mutability pass ({@code @Scope} on the covering
     * {@code @Bean} method and mutable instance fields on the implementation
     * type).
     *
     * <p>When {@code bpmnFiles} yields no references, returns a single success item
     * and does not run registration or scope checks.</p>
     *
     * <p>When there are references, emits one error per uncovered class, then for
     * each covered class (if a covering {@code @Bean} method was resolved):
     * prototype scope → one success item; otherwise if mutable fields → error,
     * then either missing {@code @Scope} → warning or explicit non-prototype scope
     * → warning. If every reference is covered by some {@code @Bean}, a summary
     * success item for full registration coverage is appended.</p>
     *
     * @param adapter    the plugin adapter used to invoke
     *                     {@code getSpringConfigurations()} reflectively
     * @param bpmnFiles    BPMN files belonging to this plugin (used to collect
     *                     delegate/listener class references)
     * @param projectDir   the extracted project root (used only for the file
     *                     label on lint items; may be {@code null})
     * @param logger       logger for diagnostic output (may be {@code null})
     * @return ordered list of lint items; never {@code null}
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

        // Step 5.5: For each covered class check @Scope on the covering @Bean method
        //           and report mutable-field hazards for effective-singleton beans.
        Map<String, Map<String, Method>> configBeanMethods = new LinkedHashMap<>();
        for (Class<?> configClass : registered) {
            if (configClass != null) {
                configBeanMethods.put(configClass.getName(),
                        extractBeanMethodMap(configClass, logger));
            }
        }
        for (String refClass : referencedBpmnClasses) {
            if (uncoveredClasses.contains(refClass)) {
                continue; // already reported as ERROR in step 5
            }
            Optional<Method> coveringMethod = findCoveringMethod(configBeanMethods, refClass, cl);
            if (coveringMethod.isEmpty()) {
                continue;
            }
            String scopeValue = getScopeValue(coveringMethod.get());
            boolean mutable = hasMutableInstanceFields(refClass, cl);

            if ("prototype".equals(scopeValue)) {
                items.add(new PluginLintItem(
                        LinterSeverity.SUCCESS,
                        LintingType.SPRING_BEAN_SCOPE_PROTOTYPE,
                        locationFile,
                        refClass,
                        "BPMN-referenced class '" + simpleName(refClass) + "' ("
                                + refClass + ") is correctly configured as a prototype-scoped @Bean."
                ));
                continue; // prototype is safe – no further scope checks needed
            }

            // Effective singleton (missing @Scope or explicit non-prototype): check mutable fields first.
            if (mutable) {
                items.add(new PluginLintItem(
                        LinterSeverity.ERROR,
                        LintingType.SPRING_BEAN_SCOPE_MUTABLE_SINGLETON,
                        locationFile,
                        refClass,
                        "BPMN-referenced class '" + simpleName(refClass) + "' ("
                                + refClass + ") is effectively singleton-scoped and "
                                + "contains mutable (non-static, non-final) instance fields. "
                                + "This will cause race conditions under concurrent process execution."
                ));
            }

            if (scopeValue == null) {
                items.add(new PluginLintItem(
                        LinterSeverity.WARN,
                        LintingType.SPRING_BEAN_SCOPE_MISSING,
                        locationFile,
                        refClass,
                        "BPMN-referenced class '" + simpleName(refClass) + "' ("
                                + refClass + ") has a @Bean method without an explicit @Scope "
                                + "annotation. Spring defaults to singleton scope, which is risky "
                                + "for Camunda delegates and listeners. Consider adding "
                                + "@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)."
                ));
            } else {
                // Explicit non-prototype scope (typically "singleton")
                items.add(new PluginLintItem(
                        LinterSeverity.WARN,
                        LintingType.SPRING_BEAN_SCOPE_SINGLETON_EXPLICIT,
                        locationFile,
                        refClass,
                        "BPMN-referenced class '" + simpleName(refClass) + "' ("
                                + refClass + ") is explicitly configured with @Scope(\""
                                + scopeValue + "\"). Singleton-scoped Camunda delegates and "
                                + "listeners must be actually completely stateless."
                ));
            }
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

    // ==================== @Bean METHOD MAP (for scope checks) ====================

    /**
     * Returns a map from return-type name to the {@code @Bean}-annotated {@link Method}
     * for all bean methods declared directly on {@code configClass}.
     * Analogous to {@link #extractBeanReturnTypes} but retains the {@code Method}
     * object so callers can inspect further annotations such as {@code @Scope}.
     */
    private static Map<String, Method> extractBeanMethodMap(Class<?> configClass, Logger logger) {
        Map<String, Method> result = new LinkedHashMap<>();
        try {
            for (Method m : configClass.getDeclaredMethods()) {
                if (hasAnnotationByName(m.getAnnotations())) {
                    Class<?> rt = m.getReturnType();
                    if (rt != void.class && rt != Void.class) {
                        result.putIfAbsent(rt.getName(), m);
                    }
                }
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.debug("Could not read @Bean methods of '"
                        + configClass.getName() + "' for scope check: " + t.getMessage());
            }
        }
        return result;
    }

    /**
     * Searches all registered config bean-method maps for the first {@code @Bean}
     * method whose return type covers {@code referencedClass} – either by exact name
     * or by assignability ({@code returnType.isAssignableFrom(referencedClass)}).
     */
    private static Optional<Method> findCoveringMethod(
            Map<String, Map<String, Method>> configBeanMethods,
            String referencedClass,
            ClassLoader cl) {

        // 1. Exact match across all configs
        for (Map<String, Method> methodMap : configBeanMethods.values()) {
            Method m = methodMap.get(referencedClass);
            if (m != null) {
                return Optional.of(m);
            }
        }

        // 2. Assignability fallback
        if (cl == null) {
            return Optional.empty();
        }
        Class<?> ref;
        try {
            ref = Class.forName(referencedClass, false, cl);
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
        for (Map<String, Method> methodMap : configBeanMethods.values()) {
            for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
                try {
                    Class<?> returnType = Class.forName(entry.getKey(), false, cl);
                    if (returnType.isAssignableFrom(ref)) {
                        return Optional.of(entry.getValue());
                    }
                } catch (ClassNotFoundException | LinkageError ignored) {
                    // skip unresolvable types
                }
            }
        }
        return Optional.empty();
    }

    // ==================== @Scope READING ====================

    /**
     * Returns the {@code value()} of the {@code @Scope} annotation present on
     * {@code beanMethod}, or {@code null} if no {@code @Scope} annotation is found.
     *
     * <p>The annotation is identified by its fully-qualified class name to tolerate
     * cases where the annotation was loaded by a different {@link ClassLoader} than
     * the linter's own class loader.</p>
     */
    private static String getScopeValue(Method beanMethod) {
        for (Annotation a : beanMethod.getAnnotations()) {
            if (SCOPE_ANNOTATION.equals(a.annotationType().getName())) {
                try {
                    Object value = a.annotationType().getMethod("value").invoke(a);
                    if (value instanceof String s && !s.isBlank()) {
                        return s;
                    }
                    // scopeName() is an alias for value() in some Spring versions
                    Object scopeName = a.annotationType().getMethod("scopeName").invoke(a);
                    if (scopeName instanceof String s && !s.isBlank()) {
                        return s;
                    }
                } catch (Exception ignored) {
                    // Annotation structure unexpected – treat as no scope
                }
                return null;
            }
        }
        return null;
    }

    // ==================== MUTABLE FIELD DETECTION ====================

    /**
     * Returns {@code true} when the class identified by {@code className} declares at
     * least one instance field that is neither {@code static} nor {@code final}.
     * Such fields are a concurrency hazard when the bean is singleton-scoped.
     */
    private static boolean hasMutableInstanceFields(String className, ClassLoader cl) {
        if (cl == null) {
            return false;
        }
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            for (Field f : clazz.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isStatic(mod) && !Modifier.isFinal(mod)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Cannot load class – skip mutable-field check
        }
        return false;
    }
}
