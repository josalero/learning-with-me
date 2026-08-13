/**
 * Path-jailed workspace, argv command runner, local git, and artifact/run stores.
 *
 * <p><strong>Key scenarios:</strong> Developer tools write files through {@code FileWorkspace};
 * the build gate runs allowlisted argv via {@code ProcessCommandRunner}.
 */
package dev.mytechprofile.sdlc.adapter;
