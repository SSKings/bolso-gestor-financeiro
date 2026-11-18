package com.sskings.bolso.config;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Especificação OpenAPI usada como contrato com o front (docs/openapi.json → tipos TypeScript gerados).
 * <ul>
 *   <li>Todo campo é obrigatório, a não ser que esteja marcado {@code @Schema(nullable = true)}: assim o front sabe
 *       exatamente o que pode faltar ou vir nulo, e campo novo ou renomeado vira erro de compilação lá.</li>
 *   <li>{@link YearMonth} é texto ("2026-11"), como o Jackson serializa.</li>
 * </ul>
 * A especificação só é servida em dev (springdoc desligado nos outros perfis); o teste de contrato a gera.
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().replaceWithSchema(YearMonth.class, new StringSchema().pattern("^\\d{4}-\\d{2}$").example("2026-11"));
    }

    /**
     * Respostas: todo campo obrigatório, salvo os anuláveis. Requisições ficam como a validação diz (@NotNull etc.):
     * um campo opcional na entrada continua opcional no tipo do front.
     */
    @Bean
    OpenApiCustomizer requiredUnlessNullable() {
        return api -> {
            if (api.getComponents() == null || api.getComponents().getSchemas() == null) return;
            Map<String, Schema> all = api.getComponents().getSchemas();
            Set<String> requests = new HashSet<>();
            if (api.getPaths() != null) {
                api.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                    if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                        op.getRequestBody().getContent().values().forEach(media -> collect(media.getSchema(), all, requests));
                    }
                }));
            }
            for (Map.Entry<String, Schema> e : all.entrySet()) {
                Schema<?> schema = e.getValue();
                if (schema.getProperties() == null || requests.contains(e.getKey())) continue;
                List<String> required = new ArrayList<>(schema.getRequired() == null ? List.of() : schema.getRequired());
                for (Map.Entry<String, Schema> prop : new ArrayList<>(schema.getProperties().entrySet())) {
                    String name = prop.getKey();
                    Schema<?> p = prop.getValue();
                    if (nullable(p)) {
                        required.remove(name);
                        if (p.get$ref() != null) schema.getProperties().put(name, refOrNull(p.get$ref()));
                    } else if (!required.contains(name)) {
                        required.add(name);
                    }
                }
                schema.setRequired(required.isEmpty() ? null : required);
            }
        };
    }

    /** OpenAPI 3.1: anulável é {@code type: [X, "null"]} (ou {@code $ref} com {@code type: "null"} ao lado). */
    static boolean nullable(Schema<?> p) {
        return Boolean.TRUE.equals(p.getNullable()) || "null".equals(p.getType())
                || (p.getTypes() != null && p.getTypes().contains("null"));
    }

    /** "$ref ou null" escrito sem ambiguidade: anyOf [$ref, null] (o front gera {@code Tipo | null}). */
    private static Schema<?> refOrNull(String ref) {
        Schema<?> target = new Schema<>().$ref(ref);
        Schema<?> nul = new Schema<>();
        nul.addType("null");
        Schema<?> any = new Schema<>();
        any.setAnyOf(List.of(target, nul));
        for (Schema<?> x : List.of(target, nul, any)) x.setSpecVersion(io.swagger.v3.oas.models.SpecVersion.V31);
        return any;
    }

    /** Nomes dos schemas alcançáveis a partir de {@code s} (referências, propriedades, itens de lista). */
    private static void collect(Schema<?> s, Map<String, Schema> all, Set<String> out) {
        if (s == null) return;
        if (s.get$ref() != null) {
            String name = s.get$ref().substring(s.get$ref().lastIndexOf('/') + 1);
            if (out.add(name)) collect(all.get(name), all, out);
            return;
        }
        if (s.getProperties() != null) s.getProperties().values().forEach(p -> collect(p, all, out));
        if (s.getItems() != null) collect(s.getItems(), all, out);
    }
}
