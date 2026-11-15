package org.limelightcreatives.auth.session;

import org.junit.jupiter.api.Test;
import org.limelightcreatives.auth.DatabaseTest;
import org.limelightcreatives.auth.web.Visitor;
import org.springframework.aot.hint.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class SessionSerializationTest extends DatabaseTest {

    private static final String AUTHORIZE = "/oauth2/authorize?response_type=code&client_id=test-app&scope=openid"
            + "&redirect_uri=https://app.example/callback&state=s&nonce=n"
            + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256";

    @Autowired
    MockMvc mvc;

    private final Set<String> stored = new TreeSet<>();

    private void collect() {
        for (byte[] bytes : this.db.sql("SELECT attribute_bytes FROM spring_session_attributes").query(byte[].class).list()) {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes)) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    Class<?> type = super.resolveClass(desc);
                    if (!type.isArray() && !type.isPrimitive()) SessionSerializationTest.this.stored.add(type.getName());
                    return type;
                }
            }) {
                in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }
    }

    @SuppressWarnings("removal") // spring security and spring session still register thru deprecated SerializationHints
    private static Set<String> registeredForSerialization(RuntimeHints hints) {
        return Stream.concat(
                hints.reflection().typeHints().filter(TypeHint::hasJavaSerialization).map(TypeHint::getType),
                hints.serialization().javaSerializationHints().map(JavaSerializationHint::getType)
        ).map(TypeReference::getName).collect(Collectors.toSet());
    }

    @Test
    void everythingInASessionCanBeSerializedInTheNativeImage() throws Exception {
        Visitor visitor = new Visitor(this.mvc);
        visitor.send(MockMvcRequestBuilders.get(URI.create(AUTHORIZE)).accept(MediaType.TEXT_HTML));
        visitor.get("/login?resume");
        collect();
        visitor.post("/login", "email", "sam@example.com");
        visitor.post("/login/code", "code", "000000");
        collect();
        visitor.signIn("sam@example.com", this.mailer);
        collect();
        visitor.post("/login/name", "firstName", "Sam", "lastName", "Lee");
        collect();
        visitor.get("/account");
        collect();

        RuntimeHints hints = new RuntimeHints();
        SpringFactoriesLoader.forResourceLocation("META-INF/spring/aot.factories")
                .load(RuntimeHintsRegistrar.class)
                .forEach(registrar -> registrar.registerHints(hints, getClass().getClassLoader()));
        new SessionHints().registerHints(hints, getClass().getClassLoader());

        assertThat(this.stored).contains("org.springframework.security.core.context.SecurityContextImpl");
        assertThat(registeredForSerialization(hints)).containsAll(this.stored);
    }
}
