package com.mailkube.model;

/**
 * The prior state in a domain status change.
 *
 * @param status the status before the change
 * @param onboardingState the onboarding state before the change
 */
public record DomainStatusPrevious(String status, String onboardingState) {}
