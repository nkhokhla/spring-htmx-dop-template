package com.example.demo;

import io.github.wimdeblauwe.vite.spring.boot.ViteManifestReader;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.jspecify.annotations.Nullable;

/**
 * Application-wide reflection hints for GraalVM native images: third-party types
 * (the Vite manifest record) and JDK types templates touch through SpEL. Each
 * module registers hints for its own template-visible records in its own
 * RuntimeHintsRegistrar (see notes.web.NotesRuntimeHints) — a module's domain
 * types must not be referenced from here.
 */
class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar()
                .registerReflectionHints(hints.reflection(), ViteManifestReader.ManifestEntry.class);
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
