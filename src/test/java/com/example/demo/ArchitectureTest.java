package com.example.demo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Guards the data-oriented structure: domain packages hold plain, immutable data
 * with no framework or outer-layer dependencies, operations live outside the data,
 * and inner-layer signatures preserve type evidence instead of widening it away.
 */
@AnalyzeClasses(packages = "com.example.demo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.servlet..", "java.sql..",
                    "..application..", "..web..", "..persistence..")
            .because("domain data must stay plain: records and sealed types with no framework coupling");

    @ArchTest
    static final ArchRule domain_data_is_immutable = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().beFinal()
            .because("domain state must be modeled immutably");

    @ArchTest
    static final ArchRule no_field_injection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /**
     * The native image ships with zero hand-written reflection hints; reflective
     * access in our own code would break that silently at runtime.
     */
    @ArchTest
    static final ArchRule no_reflection = noClasses()
            .should().dependOnClassesThat().resideInAPackage("java.lang.reflect..")
            .because("reflective access bypasses the type system and breaks the hint-free native image");

    private static final Set<String> WIDENED_TYPES = Set.of(
            "java.lang.Object",
            "java.util.Map",
            "java.util.HashMap",
            "java.util.concurrent.ConcurrentMap",
            "java.util.concurrent.ConcurrentHashMap",
            "tools.jackson.databind.JsonNode",
            "com.fasterxml.jackson.databind.JsonNode");

    /**
     * "Never widen a known value": inner-layer method and constructor signatures
     * must carry domain types, not Object or generic containers. Raw input types
     * are only legitimate in the web layer, where the trust boundary actually is.
     */
    @ArchTest
    static final ArchRule no_widened_signatures_in_inner_layers = codeUnits()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..application..", "..persistence..")
            // Spring AOT (native profile) writes CGLIB proxy classes into target/classes — not our code
            .and().areDeclaredInClassesThat().haveNameNotMatching(".*\\$\\$.*")
            .and().doNotHaveName("equals")
            .should(notUseWidenedTypesInSignature())
            .because("passing Object/Map/JsonNode where a domain type exists discards type evidence");

    /**
     * Same rule for stored state: a widened field discards evidence at rest.
     */
    @ArchTest
    static final ArchRule no_widened_fields_in_inner_layers = fields()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..application..", "..persistence..")
            .and().areDeclaredInClassesThat().haveNameNotMatching(".*\\$\\$.*")
            .should(notBeOfWidenedType())
            .because("holding known data as Object/Map discards type evidence");

    private static ArchCondition<JavaCodeUnit> notUseWidenedTypesInSignature() {
        return new ArchCondition<>("not use widened types (Object, Map, JsonNode) in their signature") {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                var offending = Stream
                        .concat(Stream.of(codeUnit.getRawReturnType()), codeUnit.getRawParameterTypes().stream())
                        .map(JavaClass::getName)
                        .filter(WIDENED_TYPES::contains)
                        .toList();
                if (!offending.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(codeUnit,
                            codeUnit.getFullName() + " widens to " + offending));
                }
            }
        };
    }

    private static ArchCondition<JavaField> notBeOfWidenedType() {
        return new ArchCondition<>("not be of a widened type (Object, Map, JsonNode)") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                if (WIDENED_TYPES.contains(field.getRawType().getName())) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " widens to " + field.getRawType().getName()));
                }
            }
        };
    }
}
