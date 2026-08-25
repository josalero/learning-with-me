package dev.mytechprofile.sdlc;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the sdlc-core module boundary claimed in AGENTS.md.
 */
@AnalyzeClasses(packages = "dev.mytechprofile.sdlc", importOptions = ImportOption.DoNotIncludeTests.class)
class SdlcCoreArchitectureTest {

    @ArchTest
    static final ArchRule core_must_not_depend_on_spring_or_langchain4j = noClasses()
            .that()
            .resideInAPackage("dev.mytechprofile.sdlc..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "dev.langchain4j..");
}
