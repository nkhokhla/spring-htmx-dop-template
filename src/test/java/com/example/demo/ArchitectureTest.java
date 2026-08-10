package com.example.demo;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
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
            .resideInAnyPackage("org.springframework..", "jakarta.servlet..", "..application..", "..web..")
            .because("domain data must stay plain: records and sealed types with no framework coupling");

    @ArchTest
    static final ArchRule domain_data_is_immutable = fields()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .should().beFinal()
            .because("domain state must be modeled immutably");

    @ArchTest
    static final ArchRule no_field_injection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /**
     * "Never widen a known value": inner-layer method signatures must carry domain
     * types, not Object or generic containers. Raw input types are only legitimate
     * in the web layer, where the trust boundary actually is.
     */
    @ArchTest
    static final ArchRule no_widened_signatures_in_inner_layers = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage("..domain..", "..application..")
            .and().doNotHaveName("equals")
            .should(notUseWidenedTypesInSignature())
            .because("passing Object/Map/JsonNode where a domain type exists discards type evidence");

    private static ArchCondition<JavaMethod> notUseWidenedTypesInSignature() {
        Set<String> widened = Set.of(
                "java.lang.Object",
                "java.util.Map",
                "java.util.HashMap",
                "java.util.concurrent.ConcurrentMap",
                "java.util.concurrent.ConcurrentHashMap",
                "tools.jackson.databind.JsonNode",
                "com.fasterxml.jackson.databind.JsonNode");
        return new ArchCondition<>("not use widened types (Object, Map, JsonNode) in their signature") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                var offending = Stream
                        .concat(Stream.of(method.getRawReturnType()), method.getRawParameterTypes().stream())
                        .map(JavaClass::getName)
                        .filter(widened::contains)
                        .toList();
                if (!offending.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " widens to " + offending));
                }
            }
        };
    }
}
