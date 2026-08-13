/**
 * Adapters, YAML catalogs, prompts, and model wiring.
 *
 * <p><strong>Key scenarios:</strong> {@code CatalogLoader} reads teams at startup;
 * {@code CatalogWriter} persists dashboard creates; {@code ChatModelFactory} builds OpenRouter
 * clients per role.
 */
package dev.mytechprofile.sdlc.config;
