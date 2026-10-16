/**
 * Native Forge 1.20.1 addon contracts for the fixed Slimefun RC-37 baseline.
 * Register definitions in the mod constructor; common setup freezes them.
 * World, inventory and command operations require the owning server thread,
 * except the explicitly immutable asynchronous profile view.
 * Mutable event payloads are defensive copies unless their contract says otherwise.
 * Forge registries, events, tick lifecycle and native persistence remain authoritative.
 * Bukkit binary compatibility, plugin integrations and online GitHub services are not provided.
 * See ADDON_API.md for supported root event/timing types, ownership and cancellation details.
 */
package io.github.slimefun4.forge.api;
