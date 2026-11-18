package com.sskings.bolso.rule;

import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Folding;
import com.sskings.bolso.common.PlainText;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.workspace.WorkspaceAccess;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras de categorização. A comparação é "contém", sem acento e sem maiúsculas; quando mais de uma casa, a mais longa
 * (mais específica) vence — "uber eats" ganha de "uber". Usadas na importação de extrato e no Telegram.
 */
@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleRepository rules;
    private final CategoryRepository categories;
    private final WorkspaceAccess access;
    private final Clock clock;

    public record RuleRequest(@NotBlank @Size(min = 2, max = 60) @PlainText String pattern, @NotNull UUID categoryId) {}

    public record RuleView(UUID id, String pattern, CategoryRef category) {}

    /** Combinador pronto para muitas descrições (carrega as regras uma vez). */
    public interface Matcher {
        Optional<Category> match(String description);
    }

    @Transactional(readOnly = true)
    public List<RuleView> list(UUID ws) {
        access.member(ws);
        Map<UUID, Category> cats = categoriesOf(ws);
        return rules.findByWorkspaceIdOrderByCreatedAt(ws).stream().map(r -> view(r, cats)).toList();
    }

    @Transactional
    public RuleView create(UUID ws, RuleRequest req) {
        access.member(ws);
        String pattern = Texts.clean(req.pattern());
        String normalized = Folding.fold(pattern);
        if (normalized.length() < 2) throw ApiException.badRequest("Pattern must have at least 2 characters");
        Category c = categories.findByIdAndWorkspaceId(req.categoryId(), ws).orElseThrow(() -> ApiException.badRequest("Category not found"));
        if (rules.existsByWorkspaceIdAndNormalized(ws, normalized)) throw ApiException.conflict("Rule already exists");
        Rule r = rules.save(new Rule(ws, pattern, normalized, c.getId(), clock.instant()));
        return view(r, Map.of(c.getId(), c));
    }

    @Transactional
    public void delete(UUID ws, UUID id) {
        access.member(ws);
        rules.delete(rules.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.notFound("Rule not found")));
    }

    @Transactional(readOnly = true)
    public Optional<CategoryRef> suggest(UUID ws, String description) {
        access.member(ws);
        return matcher(ws).match(description).map(c -> new CategoryRef(c.getId(), c.getName(), c.getColor()));
    }

    /** Sem checar acesso: quem chama já conferiu (importação, Telegram). Categoria arquivada não é sugerida. */
    @Transactional(readOnly = true)
    public Matcher matcher(UUID ws) {
        Map<UUID, Category> cats = categoriesOf(ws);
        List<Rule> sorted = rules.findByWorkspaceIdOrderByCreatedAt(ws).stream()
                .sorted(Comparator.comparingInt((Rule r) -> r.getNormalized().length()).reversed()).toList();
        return description -> {
            String text = Folding.fold(description);
            return sorted.stream().filter(r -> text.contains(r.getNormalized()))
                    .map(r -> cats.get(r.getCategoryId())).filter(c -> c != null && !c.isArchived()).findFirst();
        };
    }

    private Map<UUID, Category> categoriesOf(UUID ws) {
        return categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream().collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private static RuleView view(Rule r, Map<UUID, Category> cats) {
        Category c = cats.get(r.getCategoryId());
        return new RuleView(r.getId(), r.getPattern(), new CategoryRef(c.getId(), c.getName(), c.getColor()));
    }
}
