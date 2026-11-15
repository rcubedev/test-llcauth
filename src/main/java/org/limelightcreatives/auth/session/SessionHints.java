package org.limelightcreatives.auth.session;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMapAdapter;
import org.springframework.web.servlet.FlashMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// todo(fut): remove once Spring Session and Spring Security register these themselves
class SessionHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.reflection()
                .registerJavaSerialization(FactorGrantedAuthority.class)
                .registerJavaSerialization(Instant.class)
                .registerType(TypeReference.of("java.time.Ser"), type -> type.withJavaSerialization(true))
                .registerJavaSerialization(FlashMap.class)
                .registerJavaSerialization(HashMap.class)
                .registerJavaSerialization(LinkedHashMap.class)
                .registerJavaSerialization(CopyOnWriteArrayList.class)
                .registerJavaSerialization(LinkedMultiValueMap.class)
                .registerJavaSerialization(MultiValueMapAdapter.class)
                .registerJavaSerialization(Boolean.class);
    }
}
