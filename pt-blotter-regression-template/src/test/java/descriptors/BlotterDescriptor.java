package com.bbot.template.descriptors;

import com.bbot.core.registry.AppDescriptor;
import com.bbot.core.registry.DslFactory;
import com.bbot.template.utils.PtBlotterDsl;

/**
 * {@link AppDescriptor} for the PT-Blotter application.
 *
 * <p>Supplies only the DSL factory. All environment-specific config lives in HOCON:
 * <pre>{@code
 * b-bot.apps.blotter {
 *   descriptor-class  = "com.bbot.template.descriptors.BlotterDescriptor"
 *   health-check-path = "/api/inquiries"
 *   webUrl            = "http://..."
 *   apiBase           = "http://..."
 * }
 * }</pre>
 *
 * <h2>Template customisation points</h2>
 * <ul>
 *   <li>Change the generic type and DSL class to your own DSL implementation.</li>
 *   <li>Move the {@code descriptor-class} key in HOCON if you rename this class or its package.</li>
 * </ul>
 */
public final class BlotterDescriptor implements AppDescriptor<PtBlotterDsl> {

    @Override public DslFactory<PtBlotterDsl> dslFactory() {
        return (ctx, page) -> new PtBlotterDsl(page, ctx);
    }
}
