package com.mailkube.model;

/**
 * The sending domain an API key belongs to.
 *
 * @param name the domain name, such as {@code example.com}
 */
public record ApiKeyDomain(String name) {}
