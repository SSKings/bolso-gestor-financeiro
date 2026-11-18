package com.sskings.bolso.importing;

import com.sskings.bolso.account.Account;
import com.sskings.bolso.category.Category;
import com.sskings.bolso.category.CategoryKind;
import com.sskings.bolso.category.CategoryRepository;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.PlainText;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.rule.RuleService;
import com.sskings.bolso.security.CurrentUser;
import com.sskings.bolso.transaction.Transaction;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import com.sskings.bolso.transaction.TransactionRepository;
import com.sskings.bolso.transaction.TransactionService;
import com.sskings.bolso.transaction.TransactionSource;
import com.sskings.bolso.transaction.TransactionType;
import com.sskings.bolso.workspace.WorkspaceAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Importação de extrato em duas etapas: a prévia lê o arquivo e sugere categorias (sem gravar nada); a importação
 * recebe as linhas revisadas, valida todas antes de gravar e pula as que já existem na conta (mesma identidade).
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    static final long MAX_FILE_BYTES = 1_048_576;

    private final TransactionService transactionService;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final RuleService rules;
    private final WorkspaceAccess access;
    private final Clock clock;

    public record PreviewEntry(String externalId, LocalDate date, BigDecimal amount, TransactionType type, String description,
                               CategoryRef suggestedCategory, boolean duplicate, boolean similar) {}

    public record Preview(UUID accountId, List<PreviewEntry> entries, int total, int duplicates) {}

    public record ImportEntry(
            @NotBlank @Size(max = 80) @Pattern(regexp = "^[A-Za-z0-9._:-]+$") String externalId,
            @NotNull LocalDate date,
            @NotNull @Digits(integer = 12, fraction = 2) @DecimalMin("0.01") @DecimalMax("999999999.99") BigDecimal amount,
            @NotNull TransactionType type,
            @NotBlank @Size(max = 140) @PlainText String description,
            @NotNull UUID categoryId) {}

    public record CommitRequest(@NotNull UUID accountId, @NotEmpty @Size(max = 2000) List<@Valid @NotNull ImportEntry> entries) {}

    public record CommitResult(int imported, int skipped) {}

    @Transactional(readOnly = true)
    public Preview preview(UUID ws, UUID accountId, MultipartFile file) {
        access.member(ws);
        Account account = transactionService.usableAccount(ws, accountId);
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose a file to import");
        if (file.getSize() > MAX_FILE_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large (maximum 1 MB)");
        List<ParsedEntry> parsed;
        try {
            parsed = StatementParser.parse(file.getBytes());
        } catch (ImportException e) {
            throw ApiException.badRequest(e.getMessage());
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file");
        }
        Set<String> existing = transactions.existingExternalIds(ws, account.getId(), parsed.stream().map(ParsedEntry::externalId).toList());
        LocalDate from = parsed.stream().map(ParsedEntry::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = parsed.stream().map(ParsedEntry::date).max(Comparator.naturalOrder()).orElseThrow();
        List<Transaction> manual = transactions.manualInRange(ws, account.getId(), from, to);
        RuleService.Matcher matcher = rules.matcher(ws);
        Map<CategoryKind, Category> others = categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream()
                .filter(c -> c.getName().equals("Outros") && !c.isArchived())
                .collect(Collectors.toMap(Category::getKind, Function.identity(), (a, b) -> a));
        List<PreviewEntry> entries = parsed.stream().map(p -> {
            TransactionType type = p.amount().signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
            CategoryKind kind = type == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
            Category suggested = matcher.match(p.description()).filter(c -> c.getKind() == kind).orElse(others.get(kind));
            BigDecimal abs = p.amount().abs();
            boolean similar = manual.stream().anyMatch(t -> t.getOccurredOn().equals(p.date()) && t.getAmount().compareTo(abs) == 0 && t.getType() == type);
            return new PreviewEntry(p.externalId(), p.date(), abs, type, p.description(),
                    suggested == null ? null : new CategoryRef(suggested.getId(), suggested.getName(), suggested.getColor()),
                    existing.contains(p.externalId()), similar);
        }).toList();
        return new Preview(account.getId(), entries, entries.size(), (int) entries.stream().filter(PreviewEntry::duplicate).count());
    }

    @Transactional
    public CommitResult commit(UUID ws, CommitRequest req) {
        access.member(ws);
        Account account = transactionService.usableAccount(ws, req.accountId());
        Map<UUID, Category> cats = categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        for (ImportEntry e : req.entries()) {                         // valida o lote inteiro antes de gravar
            if (e.type() != TransactionType.INCOME && e.type() != TransactionType.EXPENSE) throw ApiException.badRequest("Imported lines are incomes or expenses");
            Category c = Optional.ofNullable(cats.get(e.categoryId())).orElseThrow(() -> ApiException.badRequest("Category not found"));
            CategoryKind kind = e.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
            if (c.getKind() != kind) throw ApiException.badRequest("Category does not match the transaction type");
            if (c.isArchived()) throw ApiException.badRequest("Category is archived");
            transactionService.checkDate(e.date());
        }
        Set<String> existing = new HashSet<>(transactions.existingExternalIds(ws, account.getId(), req.entries().stream().map(ImportEntry::externalId).toList()));
        UUID user = CurrentUser.get().getId();
        int imported = 0;
        for (ImportEntry e : req.entries()) {
            if (!existing.add(e.externalId())) continue;              // já na conta, ou repetida no próprio pedido
            Transaction t = new Transaction(ws, user, clock.instant());
            t.setType(e.type());
            t.setAmount(e.amount());
            t.setOccurredOn(e.date());
            t.setDescription(Texts.clean(e.description()));
            t.setAccountId(account.getId());
            t.setCategoryId(e.categoryId());
            t.setSource(TransactionSource.IMPORT);
            t.setExternalId(e.externalId());
            transactions.save(t);
            imported++;
        }
        transactions.flush();                                         // o índice único segura importações simultâneas
        return new CommitResult(imported, req.entries().size() - imported);
    }
}
