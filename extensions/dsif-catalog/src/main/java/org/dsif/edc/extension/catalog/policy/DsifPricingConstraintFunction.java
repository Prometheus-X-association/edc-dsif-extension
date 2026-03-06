/*
 * DSIF Pricing Constraint Function
 * SPDX-License-Identifier: Apache-2.0
 */
package org.dsif.edc.extension.catalog.policy;

import org.eclipse.edc.policy.engine.spi.DynamicAtomicConstraintRuleFunction;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Rule;

/**
 * Always-pass constraint function for DSIF pricing constraints.
 * <p>
 * Handles leftOperands with the DSIF namespace, such as:
 * {@code https://spec.dsif.dev/ns#costPerAPICall}, {@code https://spec.dsif.dev/ns#setupFee}, etc.
 * <p>
 * These constraints are descriptive: they express pricing terms in the ODRL policy
 * but are not enforced at the connector level.
 */
public class DsifPricingConstraintFunction<R extends Rule, C extends PolicyContext> implements DynamicAtomicConstraintRuleFunction<R, C> {

    public static final String DSIF_NAMESPACE = "https://spec.dsif.dev/ns#";

    @Override
    public boolean canHandle(Object leftValue) {
        return leftValue instanceof String s && s.startsWith(DSIF_NAMESPACE);
    }

    @Override
    public boolean evaluate(Object leftValue, Operator operator, Object rightValue, R rule, C context) {
        return true;
    }
}
