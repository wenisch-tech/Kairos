package tech.wenisch.kairos.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.thymeleaf.expression.Lists;
import org.thymeleaf.expression.Numbers;
import org.thymeleaf.expression.Strings;
import org.thymeleaf.expression.Temporals;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeRuntimeHintsConfig.ThymeleafExpressionRuntimeHints.class)
public class NativeRuntimeHintsConfig {

    static class ThymeleafExpressionRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            registerExpressionHelper(hints, Lists.class);
            registerExpressionHelper(hints, Numbers.class);
            registerExpressionHelper(hints, Strings.class);
            registerExpressionHelper(hints, Temporals.class);
        }

        private void registerExpressionHelper(RuntimeHints hints, Class<?> type) {
            hints.reflection().registerType(
                type,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            );
        }
    }
}
