/**
 * Shared infrastructure available to every module: id generation, database clock, configuration properties, idempotency, outbox, rate limiting and cache.
 * It must not depend on any business module.
 */
package dev.obrenoxs.ticketing.platform.api;