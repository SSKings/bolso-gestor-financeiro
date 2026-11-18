package com.sskings.bolso.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sskings.bolso.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Contrato da API com o front: a especificação gerada do código precisa ser igual à versionada em docs/openapi.json,
 * de onde o front gera os tipos (npm run contract). Mudou a API? Atualize os dois:
 * <pre>
 *   CONTRACT_UPDATE=1 ./mvnw test -Dtest=ApiContractTest     (backend: regrava docs/openapi.json)
 *   cd ../frontend && npm run contract                         (front: regera os tipos; o tsc aponta o que quebrou)
 * </pre>
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApiContractTest {

    static final Path SPEC = Path.of("..", "docs", "openapi.json");

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("A API gerada pelo código é a mesma de docs/openapi.json (contrato com o front)")
    void specMatchesCommittedContract() throws Exception {
        String raw = mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).enable(SerializationFeature.INDENT_OUTPUT);
        JsonNode spec = json.readTree(raw);
        ((ObjectNode) spec).remove("servers");                               // porta aleatória do teste
        String canonical = json.writeValueAsString(json.treeToValue(spec, Object.class)) + "\n";
        if (System.getenv("CONTRACT_UPDATE") != null) {
            Files.writeString(SPEC, canonical);
        }
        assertThat(Files.exists(SPEC)).as("docs/openapi.json não existe: rode com CONTRACT_UPDATE=1").isTrue();
        assertThat(Files.readString(SPEC))
                .as("A API mudou e docs/openapi.json não. Rode CONTRACT_UPDATE=1 ./mvnw test -Dtest=ApiContractTest e depois `npm run contract` no front")
                .isEqualTo(canonical);
    }
}
