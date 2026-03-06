/*
 * ODRL dateTime Constraint Function
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.policy;

import org.eclipse.edc.policy.engine.spi.AtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Evaluates the standard ODRL {@code dateTime} left operand.
 * <p>
 * Compares the current time against an ISO-8601 UTC right operand.
 * <p>
 * Example policy constraint:
 * <pre>
 * {
 *   "leftOperand": "odrl:dateTime",
 *   "operator": "lteq",
 *   "rightOperand": "2026-12-31T12:00:00Z"
 * }
 * </pre>
 * <p>
 * All operators except {@code IN} and {@code IS_ANY_OF} are supported.
 */
public class OdrlDateTimeConstraintFunction<C extends PolicyContext> implements AtomicConstraintRuleFunction<Permission, C> {

    public static final String ODRL_DATETIME_KEY = "http://www.w3.org/ns/odrl/2/dateTime";

    @Override
    public boolean evaluate(Operator operator, Object rightValue, Permission rule, C context) {
        if (rightValue == null) {
            context.reportProblem("dateTime constraint: right operand is null");
            return false;
        }

        if (!(rightValue instanceof String rightValueStr)) {
            context.reportProblem("dateTime constraint: right operand must be a String, got " + rightValue.getClass());
            return false;
        }

        Instant bound;
        try {
            bound = Instant.parse(rightValueStr);
        } catch (DateTimeParseException e) {
            context.reportProblem("dateTime constraint: cannot parse '%s' as ISO-8601 instant".formatted(rightValueStr));
            return false;
        }

        var now = Instant.now();
        var comparison = now.compareTo(bound);

        return switch (operator) {
            case EQ -> comparison == 0;
            case NEQ -> comparison != 0;
            case GT -> comparison > 0;
            case GEQ -> comparison >= 0;
            case LT -> comparison < 0;
            case LEQ -> comparison <= 0;
            default -> {
                context.reportProblem("dateTime constraint: unsupported operator '%s'".formatted(operator));
                yield false;
            }
        };
    }
}
