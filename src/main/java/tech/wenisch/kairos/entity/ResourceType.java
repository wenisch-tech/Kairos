package tech.wenisch.kairos.entity;

public enum ResourceType {
    HTTP, DOCKER, TCP,
    /** A single model reported by a LiteLLM proxy health endpoint. Created and updated by discovery only. */
    LITELLM_MODEL;

    private static final ResourceType[] MANUALLY_CREATABLE = {HTTP, DOCKER, TCP};

    /** Types an admin/user may pick when creating a resource by hand (LITELLM_MODEL resources are discovery-managed). */
    public static ResourceType[] manuallyCreatable() {
        return MANUALLY_CREATABLE.clone();
    }
}
