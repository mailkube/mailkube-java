package com.mailkube.model;

/**
 * Page metadata for a listing.
 *
 * @param steps links to the adjacent pages
 * @param totalCount total matching records across every page
 * @param currentPage the 1-based number of the page in hand
 */
public record Pagination(PageSteps steps, int totalCount, int currentPage) {}
