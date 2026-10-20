package com.mailkube.model;

/**
 * The organization owning an API key's domain.
 *
 * @param name the organization's display name
 * @param alias the organization's short alias
 */
public record ApiKeyOrganization(String name, String alias) {}
