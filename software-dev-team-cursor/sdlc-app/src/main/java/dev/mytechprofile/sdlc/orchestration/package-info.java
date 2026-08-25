/**
 * LangChain4j pipeline, run lifecycle, and human approval gate.
 *
 * <p><strong>Key scenarios:</strong> {@code SdlcOrchestrator.run} delegates pipeline assembly to
 * {@code SdlcPipelineFactory}; {@code RunService} exposes that as async HTTP.
 */
package dev.mytechprofile.sdlc.orchestration;
