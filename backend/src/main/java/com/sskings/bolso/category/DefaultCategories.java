package com.sskings.bolso.category;

import java.util.List;

/** Categorias com que todo espaço novo começa (editáveis e arquiváveis). */
final class DefaultCategories {

    record Seed(String name, CategoryKind kind, String color) {}

    static final List<Seed> ALL = List.of(
            new Seed("Alimentação", CategoryKind.EXPENSE, "#f97316"),
            new Seed("Mercado", CategoryKind.EXPENSE, "#22c55e"),
            new Seed("Moradia", CategoryKind.EXPENSE, "#3b82f6"),
            new Seed("Contas da casa", CategoryKind.EXPENSE, "#0ea5e9"),
            new Seed("Transporte", CategoryKind.EXPENSE, "#eab308"),
            new Seed("Saúde", CategoryKind.EXPENSE, "#ef4444"),
            new Seed("Educação", CategoryKind.EXPENSE, "#8b5cf6"),
            new Seed("Lazer", CategoryKind.EXPENSE, "#ec4899"),
            new Seed("Assinaturas", CategoryKind.EXPENSE, "#06b6d4"),
            new Seed("Compras", CategoryKind.EXPENSE, "#a855f7"),
            new Seed("Outros", CategoryKind.EXPENSE, "#6b7280"),
            new Seed("Salário", CategoryKind.INCOME, "#16a34a"),
            new Seed("Renda extra", CategoryKind.INCOME, "#84cc16"),
            new Seed("Outros", CategoryKind.INCOME, "#64748b"));

    private DefaultCategories() {}
}
