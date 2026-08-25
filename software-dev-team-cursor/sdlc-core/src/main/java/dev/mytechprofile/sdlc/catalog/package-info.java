/**
 * Team and project catalogs. YAML files under {@code config/} become {@link
 * dev.mytechprofile.sdlc.catalog.TeamBlueprint} and {@link
 * dev.mytechprofile.sdlc.catalog.ProjectProfile} records.
 *
 * <p><strong>Key scenarios:</strong> load a scrum team, reject unknown role kinds, reject a
 * {@code repoPath} outside the workspace directory.
 */
package dev.mytechprofile.sdlc.catalog;
