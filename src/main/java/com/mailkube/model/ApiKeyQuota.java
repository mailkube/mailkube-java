package com.mailkube.model;

/**
 * One send quota of the plan: its limit and what the current period has used.
 *
 * @param name which quota, such as {@code emails_per_day} or {@code emails_per_month}; a plain
 *     string rather than an enum, because a closed set turns a new server-side value into a parse
 *     error on an already-released client
 * @param limit the plan's limit for the period, in recipients
 * @param used the recipients sent to so far in the period
 * @param remaining the recipients left in the period
 * @param period the UTC period the figures count: a day ({@code 2026-10-19}) or a month
 *     ({@code 2026-10})
 */
public record ApiKeyQuota(String name, int limit, int used, int remaining, String period) {}
