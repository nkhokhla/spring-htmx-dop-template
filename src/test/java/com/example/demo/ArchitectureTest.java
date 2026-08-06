package com.example.demo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Guards the data-oriented structure: domain packages hold plain, immutable data
 * with no framework or outer-layer dependencies. Operations live outside the data.
 */
@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.servlet..", "..application..", "..web..")
            .because("domain data must stay plain: records and sealed types with no framework coupling");

    @ArchTest
    static final ArchRule domain_data_is_immutable = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().beFinal()
            .because("domain state must be modeled immutably");

    @ArchTest
    static final ArchRule no_field_injection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
}
