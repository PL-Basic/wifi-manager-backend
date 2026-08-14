package com.plagod.migration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import com.plagod.vo.user.UserAccountSnapshotVO;
import com.plagod.vo.user.UserAuthenticationSnapshotVO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceOwnershipArchitectureTest {

    private static final List<String> RUNTIME_MODULES = Arrays.asList(
            "auth-service",
            "user-service",
            "device-service",
            "monitor-service",
            "tenant-service",
            "admin-service");

    @Test
    void onlyUserOwnsSysUserPersistence() {
        assertOnlyOwnedByUser("sys_user");
        assertOnlyOwnedByUser("BaseMapper<User>");
        assertTrue(sourceContains(
                "user-service",
                "@TableName(\"sys_user\")"));
    }

    @Test
    void onlyUserOwnsDefaultMembershipOutboxMapper() {
        assertOnlyOwnedByUser("DefaultTenantMembershipOutboxMapper");
    }

    @Test
    void onlyAuthenticationSnapshotExposesPasswordHash() throws Exception {
        assertFalse(Arrays.stream(
                        UserAccountSnapshotVO.class.getDeclaredFields())
                .anyMatch(field -> "passwordHash".equals(field.getName())));
        assertTrue(Arrays.stream(
                        UserAuthenticationSnapshotVO.class
                                .getDeclaredFields())
                .anyMatch(field -> "passwordHash".equals(field.getName())));
    }

    @Test
    void controllersAndServicesKeepDependencyDirection() {
        JavaClasses classes = importRuntimeModules();
        ArchRule controllersDoNotUsePersistence = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..mapper..", "..entity..");
        ArchRule servicesDoNotUseControllers = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..controller..");
        controllersDoNotUsePersistence.check(classes);
        servicesDoNotUseControllers.check(classes);
    }

    @Test
    void adminHasNoPersistenceCapability() {
        assertFalse(sourceContains("admin-service", "mybatis"));
        assertFalse(sourceContains("admin-service", "DataSource"));
        assertFalse(Files.exists(modulePath(
                "admin-service",
                "src/main/java/com/plagod/mapper")));
        assertFalse(Files.exists(modulePath(
                "admin-service",
                "src/main/java/com/plagod/entity")));
    }

    @Test
    void persistenceOnlyModulesHaveNoApplicationClass() {
        for (String module : Arrays.asList(
                "marketplace-service",
                "support-service",
                "ai-service")) {
            assertFalse(sourceContains(module, "@SpringBootApplication"));
        }
    }

    @Test
    void runtimeModulesHaveNoDuplicatePersistenceFqcn() throws IOException {
        Map<String, String> owners = new LinkedHashMap<>();
        for (String module : RUNTIME_MODULES) {
            Path classes = modulePath(module, "target/classes");
            if (!Files.isDirectory(classes)) {
                continue;
            }
            try (java.util.stream.Stream<Path> files = Files.walk(classes)) {
                files.filter(path -> path.toString().endsWith(".class"))
                        .filter(path -> path.toString().contains(
                                java.io.File.separator + "mapper"
                                        + java.io.File.separator)
                                || path.toString().contains(
                                java.io.File.separator + "entity"
                                        + java.io.File.separator))
                        .forEach(path -> {
                            String fqcn = classes.relativize(path)
                                    .toString()
                                    .replace(java.io.File.separatorChar, '.')
                                    .replaceAll("\\.class$", "");
                            String previous = owners.putIfAbsent(fqcn, module);
                            if (previous != null) {
                                throw new AssertionError(
                                        fqcn + " appears in "
                                                + previous + " and " + module);
                            }
                        });
            }
        }
    }

    private JavaClasses importRuntimeModules() {
        ClassFileImporter importer = new ClassFileImporter();
        java.util.Set<java.net.URL> locations =
                new java.util.LinkedHashSet<>();
        for (String module : RUNTIME_MODULES) {
            Path classes = modulePath(module, "target/classes");
            if (Files.isDirectory(classes)) {
                try {
                    locations.add(classes.toUri().toURL());
                } catch (java.net.MalformedURLException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        }
        return importer.importUrls(locations);
    }

    private boolean sourceContains(String module, String token) {
        Path root = modulePath(module, "src/main");
        if (!Files.isDirectory(root)) {
            return false;
        }
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".xml")
                            || path.toString().endsWith(".yml"))
                    .anyMatch(path -> contains(path, token));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertOnlyOwnedByUser(String token) {
        assertTrue(sourceContains("user-service", token));
        for (String module : RUNTIME_MODULES) {
            if (!"user-service".equals(module)) {
                assertFalse(
                        sourceContains(module, token),
                        token + " must not appear in " + module);
            }
        }
    }

    private boolean contains(Path path, String token) {
        try {
            return new String(
                    Files.readAllBytes(path),
                    java.nio.charset.StandardCharsets.UTF_8)
                    .contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Path modulePath(String module, String relative) {
        return Paths.get("..", module, relative).normalize();
    }
}
