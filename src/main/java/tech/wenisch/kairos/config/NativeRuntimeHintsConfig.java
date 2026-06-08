package tech.wenisch.kairos.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.data.domain.PageImpl;
import tech.wenisch.kairos.dto.AdminResourceGroupViewModel;
import tech.wenisch.kairos.dto.AnnouncementDTO;
import tech.wenisch.kairos.dto.DashboardGroupShell;
import tech.wenisch.kairos.dto.GroupSummaryDTO;
import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
import tech.wenisch.kairos.dto.LatencySampleDTO;
import tech.wenisch.kairos.dto.OutageDTO;
import tech.wenisch.kairos.dto.ResourceDTO;
import tech.wenisch.kairos.dto.ResourceDetailsDTO;
import tech.wenisch.kairos.dto.ResourceGroupViewModel;
import tech.wenisch.kairos.dto.ResourceStatusUpdateDTO;
import tech.wenisch.kairos.dto.ResourceViewModel;
import tech.wenisch.kairos.dto.TimelineBlockDTO;
import tech.wenisch.kairos.entity.Announcement;
import tech.wenisch.kairos.entity.AnnouncementKind;
import tech.wenisch.kairos.entity.ApiKey;
import tech.wenisch.kairos.entity.AppUser;
import tech.wenisch.kairos.entity.AuthProvider;
import tech.wenisch.kairos.entity.AuthType;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.CorsAllowedOrigin;
import tech.wenisch.kairos.entity.CustomHeaderSettings;
import tech.wenisch.kairos.entity.DiscoveryServiceAuth;
import tech.wenisch.kairos.entity.DiscoveryServiceConfig;
import tech.wenisch.kairos.entity.DiscoveryServiceType;
import tech.wenisch.kairos.entity.EmbedAllowedOrigin;
import tech.wenisch.kairos.entity.EmbedPolicy;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationPolicy;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.NotificationProviderType;
import tech.wenisch.kairos.entity.NotificationScopeType;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.OutageNotificationRef;
import tech.wenisch.kairos.entity.ProxyMode;
import tech.wenisch.kairos.entity.ProxySettings;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceGroupVisibility;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.entity.UserRole;
import tech.wenisch.kairos.service.CheckAuditEntry;
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
            registerTemplateModels(hints,
                AdminResourceGroupViewModel.class,
                AnnouncementDTO.class,
                DashboardGroupShell.class,
                GroupSummaryDTO.class,
                InstantCheckExecutionResult.class,
                LatencySampleDTO.class,
                OutageDTO.class,
                ResourceDTO.class,
                ResourceDetailsDTO.class,
                ResourceGroupViewModel.class,
                ResourceStatusUpdateDTO.class,
                ResourceViewModel.class,
                TimelineBlockDTO.class,
                Announcement.class,
                AnnouncementKind.class,
                ApiKey.class,
                AppUser.class,
                AuthProvider.class,
                AuthType.class,
                CheckResult.class,
                CheckStatus.class,
                CorsAllowedOrigin.class,
                CustomHeaderSettings.class,
                DiscoveryServiceAuth.class,
                DiscoveryServiceConfig.class,
                DiscoveryServiceType.class,
                EmbedAllowedOrigin.class,
                EmbedPolicy.class,
                MonitoredResource.class,
                NotificationEvent.class,
                NotificationPolicy.class,
                NotificationProvider.class,
                NotificationProviderType.class,
                NotificationScopeType.class,
                Outage.class,
                OutageNotificationRef.class,
                PageImpl.class,
                ProxyMode.class,
                ProxySettings.class,
                ResourceDiscovery.class,
                ResourceGroup.class,
                ResourceGroupVisibility.class,
                ResourceType.class,
                ResourceTypeAuth.class,
                ResourceTypeConfig.class,
                UserRole.class,
                CheckAuditEntry.class
            );
            registerTemplateModel(hints, "tech.wenisch.kairos.controller.HomeController$OutageRowViewModel");
            registerTemplateModel(hints, "tech.wenisch.kairos.controller.HomeController$OutageGanttTick");
            registerTemplateModel(hints, "tech.wenisch.kairos.controller.HomeController$GroupStatusSummary");
        }

        private void registerExpressionHelper(RuntimeHints hints, Class<?> type) {
            hints.reflection().registerType(
                type,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS
            );
        }

        private void registerTemplateModel(RuntimeHints hints, Class<?> type) {
            hints.reflection().registerType(
                type,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS
            );
        }

        private void registerTemplateModel(RuntimeHints hints, String typeName) {
            hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS
            );
        }

        private void registerTemplateModels(RuntimeHints hints, Class<?>... types) {
            for (Class<?> type : types) {
                registerTemplateModel(hints, type);
            }
        }
    }
}
