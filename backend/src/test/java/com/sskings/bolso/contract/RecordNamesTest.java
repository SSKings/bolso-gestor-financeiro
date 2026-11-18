package com.sskings.bolso.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O OpenAPI nomeia os tipos pelo nome simples da classe: dois records com o mesmo nome (ex.: dois "Status") virariam
 * um só no contrato, sem aviso, e o front receberia o tipo errado.
 */
class RecordNamesTest {

    @Test
    @DisplayName("Não há dois records com o mesmo nome simples no código (o contrato os juntaria)")
    void uniqueRecordNames() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition d) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Map<String, List<String>> byName = new HashMap<>();
        for (BeanDefinition d : scanner.findCandidateComponents("com.sskings.bolso")) {
            String fqn = d.getBeanClassName();
            if (fqn.contains("Test") || fqn.contains(".support.")) continue;
            String simple = fqn.substring(fqn.lastIndexOf('.') + 1).replaceAll(".*\\$", "");
            byName.computeIfAbsent(simple, k -> new java.util.ArrayList<>()).add(fqn);
        }
        assertThat(byName).as("records encontrados").hasSizeGreaterThan(40);
        Map<String, List<String>> dup = byName.entrySet().stream().filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertThat(dup).as("renomeie um deles").isEmpty();
    }
}
