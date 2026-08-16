package com.plagod.testkit;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public final class SharedArchitectureRules {

    private SharedArchitectureRules() {
    }

    public static ArchRule controllersMustNotDependOnMappers() {
        return noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .because("Controller must call an application Service instead of persistence");
    }

    public static ArchRule administrationMustNotDependOnPersistence(String... adminPackages) {
        return noClasses()
                .that().resideInAnyPackage(adminPackages)
                .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "..entity..")
                .because("administration/BFF code must not own persistence");
    }

    public static ArchRule externalCodeMustNotDependOnImplementation(
            String implementationPackage) {
        return noClasses()
                .that().resideOutsideOfPackage(implementationPackage)
                .should().dependOnClassesThat().resideInAPackage(implementationPackage)
                .because("implementation packages are not cross-module contracts");
    }
}
