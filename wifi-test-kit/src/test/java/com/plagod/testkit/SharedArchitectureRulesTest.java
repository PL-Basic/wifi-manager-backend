package com.plagod.testkit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedArchitectureRulesTest {

    private final JavaClasses fixtures =
            new ClassFileImporter().importPackages("com.plagod.testkit.fixture");

    @Test
    void controlledFixturesProveAllSharedRulesDetectViolations() {
        assertTrue(
                SharedArchitectureRules.controllersMustNotDependOnMappers()
                        .evaluate(fixtures)
                        .hasViolation());
        assertTrue(
                SharedArchitectureRules.administrationMustNotDependOnPersistence(
                                "..testkit.fixture.admin..")
                        .evaluate(fixtures)
                        .hasViolation());
        assertTrue(
                SharedArchitectureRules.externalCodeMustNotDependOnImplementation(
                                "..testkit.fixture.foreign.impl..")
                        .evaluate(fixtures)
                        .hasViolation());
    }

    @Test
    void repositoryPomsKeepTestKitOutOfProductionScope() {
        String multiModuleRoot = System.getProperty("maven.multiModuleProjectDirectory");
        Path repositoryRoot = multiModuleRoot == null
                ? Paths.get("..").toAbsolutePath().normalize()
                : Paths.get(multiModuleRoot).toAbsolutePath().normalize();

        MavenTestKitScopeVerifier.assertOnlyTestScoped(repositoryRoot);
    }
}
