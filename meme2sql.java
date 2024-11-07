import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemeLangToSQL {

    public static void main(String[] args) {
        meme2sqlTest();
    }

    public static String meme2sql(String memelang) {
        // Remove all whitespace from the input
        memelang = memelang.replaceAll("\\s+", "");
        List<String> filters = new ArrayList<>();  // Collect outer filter conditions for rid/bid
        String sqlQuery = memeClause(memelang, filters);

        // Process filters to group by rid and bid terms
        List<String> groupedFilter = groupFilters(filters);

        // Include outer filters if any are present
        String filterCondition = !groupedFilter.isEmpty() ? " WHERE " + String.join(" OR ", groupedFilter) : "";
        return sqlQuery + filterCondition + ";";
    }

    public static ParseResult memeParse(String query) {
        Pattern pattern = Pattern.compile("^([A-Za-z0-9]*)\\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\\d*\\.?\\d*)$");
        Matcher matches = pattern.matcher(query);

        if (matches.find()) {
            String aid = matches.group(1).isEmpty() ? null : matches.group(1);
            String rid = matches.group(2).isEmpty() ? null : matches.group(2);
            String bid = matches.group(3).isEmpty() ? null : matches.group(3);
            String operator = matches.group(4).isEmpty() ? "=" : matches.group(4).replace("#=", "=");
            String qnt = matches.group(5).isEmpty() ? "1" : matches.group(5);

            List<String> conditions = new ArrayList<>();
            if (aid != null) conditions.add("aid='" + aid + "'");
            if (rid != null) conditions.add("rid='" + rid + "'");
            if (bid != null) conditions.add("bid='" + bid + "'");
            conditions.add("qnt" + operator + qnt);

            List<String> filterConditions = new ArrayList<>();
            if (rid != null) filterConditions.add("rid='" + rid + "'");
            if (bid != null) filterConditions.add("bid='" + bid + "'");
            return new ParseResult("(" + String.join(" AND ", conditions) + ")", String.join(" AND ", filterConditions));
        } else {
            throw new IllegalArgumentException("Invalid memelang format: " + query);
        }
    }

    public static String memeClause(String query, List<String> filters) {
        // Handle OR (|) conditions by generating complete SELECT statements and combining them with UNION
        if (query.contains("|")) {
            String[] clauses = query.split("\\|");
            List<String> sqlClauses = new ArrayList<>();
            for (String clause : clauses) {
                List<String> subFilters = new ArrayList<>();  // Independent filter set for each OR clause
                String sqlPart = "SELECT m.* FROM mem m " + memeClause(clause.trim(), subFilters);
                String filterCondition = !subFilters.isEmpty() ? " WHERE " + String.join(" OR ", groupFilters(subFilters)) : "";
                sqlClauses.add(sqlPart + filterCondition);
            }
            return String.join(" UNION ", sqlClauses);
        }

        // Handle AND (&) conditions by generating JOIN with GROUP BY and HAVING
        if (query.contains("&")) {
            String[] clauses = query.split("&");
            List<String> havingConditions = new ArrayList<>();
            for (String clause : clauses) {
                ParseResult result = memeParse(clause.trim());
                havingConditions.add("SUM(CASE WHEN " + result.clause + " THEN 1 ELSE 0 END) > 0");
                if (!result.filter.isEmpty()) {
                    filters.add("(" + result.filter + ")");
                }
            }
            return "JOIN (SELECT aid FROM mem GROUP BY aid HAVING " + String.join(" AND ", havingConditions) + ") AS aids ON m.aid = aids.aid";
        }

        // Direct parsing when no nested expressions or logical operators are present
        ParseResult result = memeParse(query);
        if (!result.filter.isEmpty()) {
            filters.add("(" + result.filter + ")");
        }
        return "WHERE " + result.clause;
    }

    public static List<String> groupFilters(List<String> filters) {
        List<String> ridValues = new ArrayList<>();
        List<String> bidValues = new ArrayList<>();
        List<String> complexFilters = new ArrayList<>();

        for (String filter : filters) {
            Matcher ridMatch = Pattern.compile("^\\(rid='([A-Za-z0-9]+)'\\)$").matcher(filter);
            Matcher bidMatch = Pattern.compile("^\\(bid='([A-Za-z0-9]+)'\\)$").matcher(filter);

            if (ridMatch.find()) {
                ridValues.add(ridMatch.group(1));
            } else if (bidMatch.find()) {
                bidValues.add(bidMatch.group(1));
            } else {
                complexFilters.add(filter);  // Complex terms like (rid='letter' AND bid='ord')
            }
        }

        List<String> grouped = new ArrayList<>();
        if (!ridValues.isEmpty()) {
            grouped.add("m.rid IN ('" + String.join("','", ridValues) + "')");
        }
        if (!bidValues.isEmpty()) {
            grouped.add("m.bid IN ('" + String.join("','", bidValues) + "')");
        }

        grouped.addAll(complexFilters);
        return grouped;
    }

    // Test function
    public static void meme2sqlTest() {
        List<String> queries = Arrays.asList(
            "ant.admire:amsterdam #= 0",
            "ant.believe:cairo",
            "ant.believe",
            "ant",
            ".admire",
            ":amsterdam",
            ".letter #= 2",
            ".letter > 1.9",
            ".letter >= 2.1",
            ".letter < 2.2",
            ".letter <= 2.3",
            "ant | :cairo",
            ".admire | .believe",
            ".admire | .believe | .letter > 2",
            ".discover & .explore",
            ".admire & .believe & .letter:ord < 5",
            ".discover & .explore:amsterdam | :cairo",
            ".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok",
            ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok"
        );

        for (String query : queries) {
            try {
                String generatedSql = meme2sql(query);
                System.out.println("Query: " + query);
                System.out.println("Generated SQL: " + generatedSql + "\n");
            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage() + "\n");
            }
        }
    }
}

// Helper class to store parse results
class ParseResult {
    String clause;
    String filter;

    ParseResult(String clause, String filter) {
        this.clause = clause;
        this.filter = filter;
    }
}
