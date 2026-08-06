package com.example.demo;

import com.example.demo.notes.domain.Note;
import io.github.wimdeblauwe.vite.spring.boot.ViteManifestReader;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.jspecify.annotations.Nullable;

/**
 * Reflection hints needed only for GraalVM native images. Spring AOT cannot see
 * reflection that happens via Jackson (the Vite manifest) or via Thymeleaf template
 * expressions, so those types are registered here. Register every type whose methods
 * a template calls through SpEL — your records, but also JDK types like Locale or the
 * immutable List classes — or native pages will fail at render time.
 */
class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar()
                .registerReflectionHints(hints.reflection(), ViteManifestReader.ManifestEntry.class);
        hints.reflection().registerType(Note.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        // ${#locale.toLanguageTag()} in layout/main.html
        hints.reflection().registerType(java.util.Locale.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        // ${notes.isEmpty()} in notes/list.html — SpEL needs the runtime classes
        // (Stream.toList() internals) for method lookup AND the List interface for invocation
        hints.reflection().registerType(java.util.List.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(
                TypeReference.of("java.util.ImmutableCollections$ListN"), MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(
                TypeReference.of("java.util.ImmutableCollections$List12"), MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
