package tech.wenisch.kairos.config;

import db.migration.V10__convert_jpa_jdbc_not_available_to_unknown;
import db.migration.V15__deduplicate_docker_resources_by_target;
import db.migration.V16__resource_multi_group;
import db.migration.V17__add_custom_header_settings;
import db.migration.V18__add_outage_retention_config;
import db.migration.V19__add_resource_discovery_tables;
import db.migration.V20__add_instant_check_settings;
import db.migration.V21__add_openshift_route_discovery_config;
import db.migration.V22__increase_discovery_service_auth_password_size;
import db.migration.V23__add_dashboard_auto_group_threshold;
import db.migration.V24__adjust_dashboard_auto_group_threshold_default_to_10;
import db.migration.V28__add_proxy_settings;
import db.migration.V2__expand_resource_type_for_dockerrepository;
import db.migration.V4__backfill_outages_from_history;
import db.migration.V6__add_check_history_retention_config;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FlywayMigrationConfig {

    @Bean
    FlywayConfigurationCustomizer kairosFlywayJavaMigrations() {
        return configuration -> configuration.javaMigrations(
            new V2__expand_resource_type_for_dockerrepository(),
            new V4__backfill_outages_from_history(),
            new V6__add_check_history_retention_config(),
            new V10__convert_jpa_jdbc_not_available_to_unknown(),
            new V15__deduplicate_docker_resources_by_target(),
            new V16__resource_multi_group(),
            new V17__add_custom_header_settings(),
            new V18__add_outage_retention_config(),
            new V19__add_resource_discovery_tables(),
            new V20__add_instant_check_settings(),
            new V21__add_openshift_route_discovery_config(),
            new V22__increase_discovery_service_auth_password_size(),
            new V23__add_dashboard_auto_group_threshold(),
            new V24__adjust_dashboard_auto_group_threshold_default_to_10(),
            new V28__add_proxy_settings()
        );
    }
}
