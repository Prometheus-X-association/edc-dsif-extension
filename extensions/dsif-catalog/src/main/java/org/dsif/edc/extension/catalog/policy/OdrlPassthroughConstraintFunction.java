/*
 * ODRL Passthrough Constraint Function
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.policy;

import org.eclipse.edc.policy.engine.spi.DynamicAtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Rule;

/**
 * Always-pass constraint function for ODRL namespace leftOperands.
 * <p>
 * Handles descriptive ODRL constraints like {@code odrl:event}, {@code odrl:policyUsage}, etc.
 * These constraints express policy semantics but are not enforced at the connector level.
 */
public class OdrlPassthroughConstraintFunction<R extends Rule, C extends PolicyContext> implements DynamicAtomicConstraintRuleFunction<R, C> {

    private static final String ODRL_NS = "http://www.w3.org/ns/odrl/2/";
    private static final String ODRL_COMPACT_PREFIX = "odrl:";

    @Override
    public boolean canHandle(Object leftValue) {
        return leftValue instanceof String s && (s.startsWith(ODRL_NS) || s.startsWith(ODRL_COMPACT_PREFIX));
    }

    @Override
    public boolean evaluate(Object leftValue, Operator operator, Object rightValue, R rule, C context) {
        return true;
    }
}
