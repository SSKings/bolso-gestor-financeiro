package com.sskings.bolso.category;

import com.sskings.bolso.category.CategoryDtos.CategoryView;
import com.sskings.bolso.category.CategoryDtos.CreateCategory;
import com.sskings.bolso.category.CategoryDtos.UpdateCategory;
import com.sskings.bolso.common.ApiException;
import com.sskings.bolso.common.Texts;
import com.sskings.bolso.transaction.TransactionRepository;
import com.sskings.bolso.workspace.WorkspaceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    static final String NOT_FOUND = "Category not found";

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final com.sskings.bolso.card.CardPurchaseRepository purchases;
    private final WorkspaceAccess access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CategoryView> list(UUID ws) {
        access.member(ws);
        return categories.findByWorkspaceIdOrderByKindAscNameAsc(ws).stream().map(CategoryView::of).toList();
    }

    @Transactional
    public CategoryView create(UUID ws, CreateCategory req) {
        access.member(ws);
        String name = Texts.clean(req.name());
        if (categories.existsByWorkspaceIdAndKindAndNameIgnoreCase(ws, req.kind(), name)) {
            throw ApiException.conflict("Category name already in use");
        }
        return CategoryView.of(categories.save(new Category(ws, name, req.kind(), req.color().toLowerCase(Locale.ROOT), clock.instant())));
    }

    @Transactional
    public CategoryView update(UUID ws, UUID id, UpdateCategory req) {
        access.member(ws);
        Category c = find(ws, id);
        String name = Texts.clean(req.name());
        if (!c.getName().equalsIgnoreCase(name) && categories.existsByWorkspaceIdAndKindAndNameIgnoreCase(ws, c.getKind(), name)) {
            throw ApiException.conflict("Category name already in use");
        }
        c.setName(name);
        c.setColor(req.color().toLowerCase(Locale.ROOT));
        if (req.archived() != null) c.setArchived(req.archived());
        return CategoryView.of(c);
    }

    @Transactional
    public void delete(UUID ws, UUID id) {
        access.member(ws);
        Category c = find(ws, id);
        if (transactions.existsByWorkspaceIdAndCategoryId(ws, id) || purchases.existsByWorkspaceIdAndCategoryId(ws, id)) {
            throw ApiException.conflict("Category has transactions; archive it instead");
        }
        categories.delete(c);
    }

    private Category find(UUID ws, UUID id) {
        return categories.findByIdAndWorkspaceId(id, ws).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
