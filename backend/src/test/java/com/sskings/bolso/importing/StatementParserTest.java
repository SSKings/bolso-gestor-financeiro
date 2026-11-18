package com.sskings.bolso.importing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementParserTest {

    private static byte[] file(String name) throws IOException {
        try (InputStream in = StatementParserTest.class.getResourceAsStream("/statements/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("OFX (SGML, Windows-1252): data, valor com sinal, descrição com acento e o FITID como identidade")
    void ofx() throws Exception {
        List<ParsedEntry> e = StatementParser.parse(file("extrato.ofx"));
        assertThat(e).hasSize(3);
        assertThat(e.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(e.get(0).amount()).isEqualByComparingTo("-35.90");
        assertThat(e.get(0).description()).isEqualTo("PADARIA SÃO JOÃO");
        assertThat(e.get(0).externalId()).isEqualTo("ofx:202611030001");
        assertThat(e.get(1).amount()).isEqualByComparingTo("3500.00");
        assertThat(e.get(1).description()).as("sem MEMO, usa NAME").isEqualTo("SALARIO EMPRESA");
    }

    @Test
    @DisplayName("OFX com DOCTYPE/entidade externa (XXE): nada é resolvido — não há parser XML")
    void ofxXxe() throws Exception {
        List<ParsedEntry> e = StatementParser.parse(file("xxe.ofx"));
        assertThat(e).hasSize(1);
        assertThat(e.get(0).description()).doesNotContain("root:").isEqualTo("&xxe;");
    }

    @Test
    @DisplayName("CSV do Nubank (conta): vírgula dentro de aspas, Identificador como identidade")
    void nubank() throws Exception {
        List<ParsedEntry> e = StatementParser.parse(file("nubank-conta.csv"));
        assertThat(e).hasSize(3);
        assertThat(e.get(1).description()).isEqualTo("Transferência recebida - Empresa, Ltda");
        assertThat(e.get(1).amount()).isEqualByComparingTo("3500.00");
        assertThat(e.get(0).externalId()).isEqualTo("csv:6730a1b2-0001");
    }

    @Test
    @DisplayName("CSV de banco em Latin-1 com ';' e '1.234,56'; linhas idênticas viram identidades diferentes")
    void latin1Semicolon() throws Exception {
        List<ParsedEntry> e = StatementParser.parse(file("banco-latin1.csv"));
        assertThat(e).hasSize(3);
        assertThat(e.get(0).amount()).isEqualByComparingTo("-1234.56");
        assertThat(e.get(1).amount()).isEqualByComparingTo("-5.00");
        assertThat(e.get(1).externalId()).startsWith("csv:").isNotEqualTo(e.get(2).externalId());
        assertThat(StatementParser.parse(file("banco-latin1.csv")).get(2).externalId())
                .as("a mesma linha do mesmo arquivo dá a mesma identidade (reimportar não duplica)").isEqualTo(e.get(2).externalId());
    }

    @Test
    @DisplayName("Descrição: sem caracteres de controle, espaços juntos, até 140 caracteres; vazia vira 'Sem descrição'")
    void descriptionSanitized() {
        String csv = "data,descricao,valor\n2026-11-03,\"A\u0007B\u001b[31m  C\",-1.00\n2026-11-04,\"" + "x".repeat(300) + "\",-2.00\n2026-11-05,,-3.00\n";
        List<ParsedEntry> e = StatementParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(e.get(0).description()).isEqualTo("AB[31m C");
        assertThat(e.get(1).description()).hasSize(140);
        assertThat(e.get(2).description()).isEqualTo("Sem descrição");
    }

    @Test
    @DisplayName("Arquivo inválido: formato desconhecido, sem lançamentos, data ou valor ruins (com o número da linha), linhas demais")
    void invalid() {
        assertThatThrownBy(() -> StatementParser.parse("isto não é um extrato".getBytes())).isInstanceOf(ImportException.class)
                .hasMessage("Unsupported file format");
        assertThatThrownBy(() -> StatementParser.parse("data,descricao,valor\n".getBytes())).hasMessage("No transactions found in the file");
        assertThatThrownBy(() -> StatementParser.parse("data,descricao,valor\n31/02/2026,X,-1\n".getBytes())).hasMessage("Invalid date on line 2");
        assertThatThrownBy(() -> StatementParser.parse("data,descricao,valor\n01/02/2026,X,abc\n".getBytes())).hasMessage("Invalid amount on line 2");
        assertThatThrownBy(() -> StatementParser.parse("data,descricao,valor\n01/02/2026,X,0\n".getBytes())).hasMessage("Invalid amount on line 2");
        StringBuilder big = new StringBuilder("data,descricao,valor\n");
        for (int i = 0; i < 2001; i++) big.append("01/02/2026,X,-1\n");
        assertThatThrownBy(() -> StatementParser.parse(big.toString().getBytes())).hasMessage("Too many lines (maximum 2000)");
        assertThatThrownBy(() -> StatementParser.parse(new byte[0])).hasMessage("Unsupported file format");
    }

    @Test
    @DisplayName("Valores: R$, espaços, sinal no fim e parênteses (formatos de alguns bancos); teto de R$ 999.999.999,99")
    void amounts() {
        assertThat(StatementParser.amount("R$ 1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(StatementParser.amount("-12.50")).isEqualByComparingTo("-12.50");
        assertThat(StatementParser.amount("12,50-")).isEqualByComparingTo("-12.50");
        assertThat(StatementParser.amount("(12,50)")).isEqualByComparingTo("-12.50");
        assertThat(StatementParser.amount("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(StatementParser.amount("1000000000,00")).isNull();
        assertThat(StatementParser.amount("1,234")).as("ambíguo com 3 casas: milhar").isEqualByComparingTo(new BigDecimal("1234"));
        assertThat(StatementParser.amount("12,345,67")).isNull();
    }

    @Test
    @DisplayName("Identidade da linha diferencia acentos: 'Café' e 'Cafè' (iguais em ASCII: 'Caf?') são lançamentos diferentes")
    void hashKeepsAccents() {
        String csv = "data,descricao,valor\n2026-11-03,Café,-5.00\n2026-11-03,Cafè,-5.00\n";
        List<ParsedEntry> e = StatementParser.parse(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(e.get(0).externalId()).isNotEqualTo(e.get(1).externalId());
    }

    private static List<ParsedEntry> parse(String text) {
        return StatementParser.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String ofx(String... transactions) {
        StringBuilder b = new StringBuilder("<OFX><BANKTRANLIST>\n");
        for (String t : transactions) b.append("<STMTTRN>").append(t).append("</STMTTRN>\n");
        return b.append("</BANKTRANLIST></OFX>").toString();
    }

    @Test
    @DisplayName("OFX 2 (XML), sem FITID: identidade por hash, estável; transação com data/valor ruins é recusada com o número dela")
    void ofxEdgeCases() {
        String xml = ofx("<DTPOSTED>20261103</DTPOSTED><TRNAMT>-5.00</TRNAMT><NAME>Caf&amp;e &lt;b&gt;</NAME>",
                "<DTPOSTED>20261103</DTPOSTED><TRNAMT>-5.00</TRNAMT><NAME>Caf&amp;e &lt;b&gt;</NAME>");
        List<ParsedEntry> e = parse(xml);
        assertThat(e.get(0).description()).isEqualTo("Caf&e <b>");
        assertThat(e.get(0).externalId()).startsWith("ofx:").hasSize(44).isNotEqualTo(e.get(1).externalId());
        assertThat(parse(xml).get(1).externalId()).isEqualTo(e.get(1).externalId());
    }

    @Test
    @DisplayName("OFX: data curta/inválida, valor ausente, zero, com 3 casas ou acima do teto → erro com o número da transação")
    void ofxInvalid() {
        String ok = "<DTPOSTED>20261103<TRNAMT>-1.00<FITID>1";
        assertThatThrownBy(() -> parse(ofx(ok, "<DTPOSTED>2026<TRNAMT>-1.00"))).hasMessage("Invalid date in transaction 2");
        assertThatThrownBy(() -> parse(ofx("<DTPOSTED>20261332<TRNAMT>-1.00"))).hasMessage("Invalid date in transaction 1");
        assertThatThrownBy(() -> parse(ofx("<DTPOSTED>20261103<FITID>1"))).hasMessage("Invalid amount in transaction 1");
        for (String bad : new String[]{"0.00", "-1.005", "1000000000.00"}) {
            assertThatThrownBy(() -> parse(ofx("<DTPOSTED>20261103<TRNAMT>" + bad))).as(bad).hasMessage("Invalid amount in transaction 1");
        }
        assertThat(parse(ofx("<DTPOSTED>20261103<TRNAMT>-1,50")).get(0).amount()).isEqualByComparingTo("-1.50");
        String many = ofx(java.util.Collections.nCopies(2001, ok).toArray(String[]::new));
        assertThatThrownBy(() -> parse(many)).hasMessage("Too many lines (maximum 2000)");
        assertThat(parse("OFXHEADER:100\n" + ofx("<DTPOSTED>20261103<TRNAMT>-1.00")).get(0).description()).isEqualTo("Sem descrição");
    }

    @Test
    @DisplayName("FITID: só caracteres seguros (o resto é removido) e no máximo 76")
    void fitidSanitized() {
        assertThat(parse(ofx("<DTPOSTED>20261103<TRNAMT>-1<FITID>ab/../c d'\"" )).get(0).externalId()).isEqualTo("ofx:ab..cd");
        assertThat(parse(ofx("<DTPOSTED>20261103<TRNAMT>-1<FITID>" + "9".repeat(100))).get(0).externalId()).isEqualTo("ofx:" + "9".repeat(76));
    }

    @Test
    @DisplayName("CSV: BOM, linhas em branco, cabeçalhos de vários bancos, linha curta, aspas escapadas, data em 4 formatos")
    void csvVariants() {
        List<ParsedEntry> card = parse("\uFEFF\n\ndate,title,amount\n2026-11-03,\"Loja \"\"X\"\"\",12.90\n\n");
        assertThat(card).hasSize(1);
        assertThat(card.get(0).description()).isEqualTo("Loja \"X\"");
        assertThat(card.get(0).amount()).as("cartão do Nubank: positivo = gasto, como vem no arquivo").isEqualByComparingTo("12.90");
        List<ParsedEntry> e = parse("Data Lançamento;Histórico;Valor (R$)\n03-11-2026;A;-1,00\n03/11/26;B;-2,00\n");
        assertThat(e).extracting(ParsedEntry::date).containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3));
        assertThat(e).extracting(ParsedEntry::description).containsExactly("A", "B");
    }

    @Test
    @DisplayName("Reconhece colunas: dt/valor/memo, Estabelecimento, Lançamento, Descrição; sem descrição → 'Sem descrição'")
    void columns() {
        assertThat(parse("dt,memo,valor,id\n2026-11-03,M,-1,abc\n").get(0)).extracting(ParsedEntry::description, ParsedEntry::externalId)
                .containsExactly("M", "csv:abc");
        assertThat(parse("data,estabelecimento,value\n03/11/2026,E,-1\n").get(0).description()).isEqualTo("E");
        assertThat(parse("data;lancamento;valor\n03-11-2026;L;-1\n").get(0).date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(parse("data;historico;valor\n03/11/26;H;-1\n").get(0).date()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(parse("data,valor\n03/11/2026,-1\n").get(0).description()).isEqualTo("Sem descrição");
        assertThat(parse("data,descricao,amount\n03/11/2026,X,-1\n").get(0).amount()).isEqualByComparingTo("-1");
        assertThatThrownBy(() -> parse("data,descricao\n03/11/2026,X\n")).hasMessage("Unsupported file format");
        assertThatThrownBy(() -> parse("valor\n1\n")).hasMessage("Unsupported file format");
        assertThatThrownBy(() -> parse("data,descricao,valor\n03/11/2026,X\n")).hasMessage("Invalid amount on line 2");
    }

    @Test
    @DisplayName("Valores recusados: vazio, letras, separador sobrando, 3 casas decimais, milhar malformado")
    void amountsRejected() {
        for (String bad : new String[]{"", "abc", "1.2.3", ",5", "5,", "1,2345", "1.234,5,6", "12.34.567,00", "1,000.000",
                "1.000,123", "--5", "(5", "+-5", "-(5,00)"}) {
            assertThat(StatementParser.amount(bad)).as(bad).isNull();
        }
        assertThat(StatementParser.amount(null)).isNull();
        assertThat(StatementParser.amount("+5")).isEqualByComparingTo("5");
        assertThat(StatementParser.amount("1.234.567")).isEqualByComparingTo("1234567");
        assertThat(StatementParser.amount("12,3")).isEqualByComparingTo("12.30");
    }
}
