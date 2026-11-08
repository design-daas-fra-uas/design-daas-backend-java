package de.frauas.design.backend.config;

import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import de.frauas.design.backend.config.properties.OAuth2Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthorizationServerConfig")
class AuthorizationServerConfigTest {

    @TempDir
    Path keysDir;

    private AuthorizationServerConfig config() {
        OAuth2Properties properties = new OAuth2Properties();
        properties.setKeysDir(keysDir.resolve("keys").toString());
        return new AuthorizationServerConfig(properties);
    }

    private static List<JWK> keys(JWKSource<SecurityContext> source) throws Exception {
        return source.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
    }

    @Test
    @DisplayName("generates an owner-only RSA key on first start and reuses it afterwards")
    void generatesThenReloadsKey() throws Exception {
        List<JWK> first = keys(config().jwkSource());
        Path keyFile = keysDir.resolve("keys/rsa-jwk.json");

        assertThat(keyFile).exists();
        assertThat(Files.getPosixFilePermissions(keyFile))
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

        List<JWK> second = keys(config().jwkSource());

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getKeyID()).isEqualTo(first.get(0).getKeyID());
        assertThat(second.get(0).isPrivate()).isTrue();
    }

    @Test
    @DisplayName("builds an encoder from the key source")
    void encoder() throws Exception {
        AuthorizationServerConfig config = config();

        assertThat(config.jwtEncoder(config.jwkSource())).isNotNull();
    }
}
