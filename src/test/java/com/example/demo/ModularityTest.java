package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Each top-level feature package (e.g. {@code notes}) is a Spring Modulith module:
 * its nested packages are module-private, and other modules may only use types from
 * the module's base package. verify() fails the build on cross-module leaks that the
 * compiler cannot catch; the Documenter writes always-current C4/PlantUML diagrams
 * to target/spring-modulith-docs.
 */
class ModularityTest {

    static final ApplicationModules modules = ApplicationModules.of(DemoApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void writeArchitectureDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
