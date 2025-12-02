package dev.dsf.linter.util.bpmn.linters;

import dev.dsf.linter.output.FloatingElementType;
import dev.dsf.linter.output.LinterSeverity;
import dev.dsf.linter.output.LintingType;
import dev.dsf.linter.output.item.*;
import org.camunda.bpm.model.bpmn.instance.Expression;
import org.camunda.bpm.model.bpmn.instance.TimerEventDefinition;

import java.io.File;
import java.util.List;

import static dev.dsf.linter.util.linting.LintingUtils.containsPlaceholder;
import static dev.dsf.linter.util.linting.LintingUtils.isEmpty;

/**
 * Utility class for validating BPMN timer event definitions.
 * <p>
 * This class provides validation methods to ensure that timer events have proper
 * timer type definitions (timeDate, timeCycle, or timeDuration) and contain
 * appropriate placeholders for dynamic configuration.
 * </p>
 */
public final class BpmnTimerLinter {

    private BpmnTimerLinter() {
        // Utility class - no instantiation
    }

    /**
     * Checks timer definition for intermediate catch events.
     *
     * @param elementId the identifier of the BPMN element
     * @param issues    the list of {@link BpmnElementLintItem} to which lint issues or success items will be added
     * @param bpmnFile  the BPMN file under lint
     * @param processId the identifier of the BPMN process containing the element
     * @param timerDef  the timer event definition to validate
     */
    public static void checkTimerDefinition(
            String elementId,
            List<BpmnElementLintItem> issues,
            File bpmnFile,
            String processId,
            TimerEventDefinition timerDef) {
        Expression timeDateExpr = timerDef.getTimeDate();
        Expression timeCycleExpr = timerDef.getTimeCycle();
        Expression timeDurationExpr = timerDef.getTimeDuration();

        boolean isTimeDateEmpty = (timeDateExpr == null || isEmpty(timeDateExpr.getTextContent()));
        boolean isTimeCycleEmpty = (timeCycleExpr == null || isEmpty(timeCycleExpr.getTextContent()));
        boolean isTimeDurationEmpty = (timeDurationExpr == null || isEmpty(timeDurationExpr.getTextContent()));

        if (isTimeDateEmpty && isTimeCycleEmpty && isTimeDurationEmpty) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Timer type is empty (no timeDate, timeCycle, or timeDuration)",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.ERROR,
                    FloatingElementType.TIMER_TYPE_IS_EMPTY));
            return;
        }

        issues.add(new BpmnElementLintItemSuccess(
                elementId, bpmnFile, processId, "Timer type is provided."));

        if (!isTimeDateEmpty) {
            issues.add(new BpmnFloatingElementLintItem(
                    elementId, bpmnFile, processId,
                    "Timer type is a fixed date/time (timeDate) – please verify if this is intended",
                    LintingType.BPMN_FLOATING_ELEMENT,
                    LinterSeverity.INFO,
                    FloatingElementType.TIMER_TYPE_IS_A_FIXED_DATE_TIME));
            issues.add(new BpmnElementLintItemSuccess(
                    elementId, bpmnFile, processId,
                    "Fixed date/time (timeDate) provided: '" + timeDateExpr.getTextContent() + "'"));
        } else {
            String timerValue = !isTimeCycleEmpty
                    ? timeCycleExpr.getTextContent()
                    : timeDurationExpr.getTextContent();

            if (!containsPlaceholder(timerValue)) {
                issues.add(new BpmnFloatingElementLintItem(
                        elementId, bpmnFile, processId,
                        "Timer value appears fixed (no placeholder found)",
                        LintingType.BPMN_FLOATING_ELEMENT,
                        LinterSeverity.WARN,
                        FloatingElementType.TIMER_VALUE_APPEARS_FIXED_NO_PLACEHOLDER_FOUND));
            } else {
                issues.add(new BpmnElementLintItemSuccess(
                        elementId, bpmnFile, processId,
                        "Timer value contains a valid placeholder: '" + timerValue + "'"));
            }
        }
    }
}

