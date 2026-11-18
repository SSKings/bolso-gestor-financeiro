package com.sskings.bolso.importing;

import com.sskings.bolso.common.Texts;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê extratos OFX e CSV.
 * <ul>
 *   <li><b>Sem parser XML</b>: o OFX é lido por padrões de texto, então DOCTYPE e entidades (XXE) viram texto comum.</li>
 *   <li>No máximo {@value #MAX_LINES} lançamentos; o tamanho do arquivo é limitado antes (1 MB).</li>
 *   <li>Codificação: UTF-8 quando válido; senão Windows-1252 (o padrão dos bancos brasileiros).</li>
 *   <li>Descrição sem caracteres de controle, até 140 caracteres.</li>
 *   <li>Identidade da linha: FITID (OFX), Identificador (CSV do Nubank) ou um hash de data, valor, descrição e
 *       ordem da repetição (dois cafés iguais no mesmo dia são dois lançamentos; reimportar dá os mesmos hashes).</li>
 * </ul>
 */
public final class StatementParser {

    static final int MAX_LINES = 2000;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");
    private static final Charset CP1252 = Charset.forName("windows-1252");
    private static final Pattern TAG = Pattern.compile("<(DTPOSTED|TRNAMT|FITID|MEMO|NAME)>([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE);
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uu").withResolverStyle(ResolverStyle.STRICT));

    private StatementParser() {}

    public static List<ParsedEntry> parse(byte[] content) {
        String text = decode(content);
        if (text.startsWith("﻿")) text = text.substring(1);
        String upper = text.toUpperCase(Locale.ROOT);
        List<ParsedEntry> entries;
        if (upper.contains("<OFX>") || upper.stripLeading().startsWith("OFXHEADER")) {
            entries = ofx(text);
        } else if (looksLikeCsv(text)) {
            entries = csv(text);
        } else {
            throw new ImportException("Unsupported file format");
        }
        if (entries.isEmpty()) throw new ImportException("No transactions found in the file");
        return entries;
    }

    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return new String(content, CP1252);
        }
    }

    // ---------- OFX ----------

    private static List<ParsedEntry> ofx(String text) {
        List<ParsedEntry> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        String[] chunks = text.split("(?i)<STMTTRN>");
        for (int i = 1; i < chunks.length; i++) {
            if (out.size() >= MAX_LINES) throw new ImportException("Too many lines (maximum " + MAX_LINES + ")");
            String chunk = chunks[i].split("(?i)</STMTTRN>|</BANKTRANLIST>")[0];
            Map<String, String> f = new HashMap<>();
            Matcher m = TAG.matcher(chunk);
            while (m.find()) f.putIfAbsent(m.group(1).toUpperCase(Locale.ROOT), m.group(2).strip());
            String dt = f.getOrDefault("DTPOSTED", "");
            LocalDate date;
            try {
                date = LocalDate.parse(dt.length() >= 8 ? dt.substring(0, 8) : dt, DateTimeFormatter.BASIC_ISO_DATE.withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeParseException e) {
                throw new ImportException("Invalid date in transaction " + i);
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(f.getOrDefault("TRNAMT", "").replace(',', '.'));
            } catch (NumberFormatException e) {
                throw new ImportException("Invalid amount in transaction " + i);
            }
            if (amount.signum() == 0 || amount.scale() > 2 || amount.abs().compareTo(MAX_AMOUNT) > 0) {
                throw new ImportException("Invalid amount in transaction " + i);
            }
            String memo = f.getOrDefault("MEMO", "");
            String description = description(xmlText(memo.isBlank() ? f.getOrDefault("NAME", "") : memo));
            String fitid = safeId(f.getOrDefault("FITID", ""));
            String id = fitid.isEmpty() ? "ofx:" + hash(date, amount, description, seen) : "ofx:" + fitid;
            out.add(new ParsedEntry(date, amount, description, id));
        }
        return out;
    }

    /** Só as entidades padrão; qualquer outra (inclusive &xxe;) fica como texto. */
    private static String xmlText(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    // ---------- CSV ----------

    private static boolean looksLikeCsv(String text) {
        String first = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        char sep = separator(first);
        List<String> header = split(first, sep);
        return header.size() >= 2 && column(header, "date") >= 0 && column(header, "amount") >= 0;
    }

    private static char separator(String header) {
        return header.chars().filter(c -> c == ';').count() > header.chars().filter(c -> c == ',').count() ? ';' : ',';
    }

    private static int column(List<String> header, String kind) {
        for (int i = 0; i < header.size(); i++) {
            String h = com.sskings.bolso.common.Folding.fold(header.get(i));
            boolean hit = switch (kind) {
                case "date" -> h.equals("date") || h.startsWith("data") || h.equals("dt");
                case "amount" -> h.equals("valor") || h.equals("amount") || h.equals("value") || h.startsWith("valor ");
                case "description" -> h.startsWith("descri") || h.startsWith("historic") || h.equals("title") || h.equals("memo")
                        || h.startsWith("lancamento") || h.startsWith("estabelecimento");
                case "id" -> h.equals("identificador") || h.equals("id");
                default -> false;
            };
            if (hit) return i;
        }
        return -1;
    }

    private static List<ParsedEntry> csv(String text) {
        List<String> lines = text.lines().toList();
        int headerIdx = 0;
        while (lines.get(headerIdx).isBlank()) headerIdx++;
        char sep = separator(lines.get(headerIdx));
        List<String> header = split(lines.get(headerIdx), sep);
        int cDate = column(header, "date"), cAmount = column(header, "amount"), cDesc = column(header, "description"), cId = column(header, "id");
        List<ParsedEntry> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = headerIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            if (out.size() >= MAX_LINES) throw new ImportException("Too many lines (maximum " + MAX_LINES + ")");
            int lineNo = i + 1;
            List<String> cols = split(line, sep);
            LocalDate date = date(get(cols, cDate));
            if (date == null) throw new ImportException("Invalid date on line " + lineNo);
            BigDecimal amount = amount(get(cols, cAmount));
            if (amount == null || amount.signum() == 0) throw new ImportException("Invalid amount on line " + lineNo);
            String description = description(get(cols, cDesc));           // coluna ausente (-1) → ""
            String rawId = safeId(get(cols, cId));
            String id = rawId.isEmpty() ? "csv:" + hash(date, amount, description, seen) : "csv:" + rawId;
            out.add(new ParsedEntry(date, amount, description, id));
        }
        return out;
    }

    private static String get(List<String> cols, int i) {
        return i >= 0 && i < cols.size() ? cols.get(i) : "";
    }

    /** Campos com aspas (podem conter o separador e aspas dobradas). */
    static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == sep) {
                out.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().strip());
        return out;
    }

    private static LocalDate date(String s) {
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(s.strip(), f);
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    /**
     * Valor em texto de banco: "R$ 1.234,56", "-12.50", "12,50-", "(12,50)", "1,234.56". O último separador com 1 ou 2
     * casas depois é o decimal; com 3 casas, é de milhar. Mais de 2 casas decimais, formato ambíguo ou acima do teto → null.
     */
    public static BigDecimal amount(String raw) {
        if (raw == null) return null;
        String s = raw.replace("R$", "").replace(" ", "").replace(" ", "").strip();
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length() - 1); }
        if (s.endsWith("-")) { negative = true; s = s.substring(0, s.length() - 1); }
        if (s.startsWith("-")) {
            if (negative) return null;                                    // "-12,50-" ou "-(5)": sinal duplicado é ambíguo
            negative = true;
            s = s.substring(1);
        }
        else if (s.startsWith("+")) s = s.substring(1);
        if (!s.matches("[0-9.,]+")) return null;
        int lastDot = s.lastIndexOf('.'), lastComma = s.lastIndexOf(',');
        String digits;
        if (lastDot >= 0 && lastComma >= 0) {
            char dec = lastDot > lastComma ? '.' : ',';
            char thousands = dec == '.' ? ',' : '.';
            int decPos = s.lastIndexOf(dec);
            if (s.indexOf(dec) != decPos || !s.substring(0, decPos).matches("[0-9]{1,3}(\\" + thousands + "[0-9]{3})*")) return null;
            digits = s.substring(0, decPos).replace(String.valueOf(thousands), "") + "." + s.substring(decPos + 1);
        } else if (lastDot >= 0 || lastComma >= 0) {
            char sepChar = lastDot >= 0 ? '.' : ',';
            String[] parts = s.split(java.util.regex.Pattern.quote(String.valueOf(sepChar)), -1);
            String tail = parts[parts.length - 1];
            if (parts.length == 2 && tail.length() <= 2 && !tail.isEmpty()) {
                digits = parts[0] + "." + tail;
            } else if (s.matches("[0-9]{1,3}(\\" + sepChar + "[0-9]{3})+")) {
                digits = s.replace(String.valueOf(sepChar), "");
            } else {
                return null;
            }
        } else {
            digits = s;
        }
        if (digits.startsWith(".") || digits.endsWith(".")) return null;
        BigDecimal v = new BigDecimal(digits);
        if (v.scale() > 2 || v.compareTo(MAX_AMOUNT) > 0) return null;
        return negative ? v.negate() : v;
    }

    private static String description(String raw) {
        String noControl = raw.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", "");
        String clean = Texts.clean(noControl);
        if (clean.isEmpty()) return "Sem descrição";
        return clean.length() > 140 ? clean.substring(0, 140).strip() : clean;
    }

    private static String safeId(String raw) {
        String id = raw.replaceAll("[^A-Za-z0-9._:-]", "");
        return id.length() > 76 ? id.substring(0, 76) : id;
    }

    private static String hash(LocalDate date, BigDecimal amount, String description, Map<String, Integer> seen) {
        String key = date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + description;
        int n = seen.merge(key, 1, Integer::sum);
        try {
            // UTF-8 (e não ASCII): "São" e "Sao" precisam dar hashes diferentes
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest((key + "|" + n).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d).substring(0, 40);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
