package com.mailkube.model;

/**
 * Links to the pages adjacent to the one in hand.
 *
 * <p>The server <b>omits</b> a step at either end of the range rather than sending null, so an
 * absent link and a null value mean the same thing: there is no such page. Following {@code next}
 * rather than incrementing a counter is what lets the server change its pagination scheme without
 * breaking a released client.
 *
 * @param next absolute URL of the following page, or null on the last page
 * @param previous absolute URL of the preceding page, or null on the first page
 */
public record PageSteps(String next, String previous) {}
