import java.util.ArrayList;
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
        String filterCondition = groupedFilter.isEmpty() ? "" : " AND (" + String.join(" OR ", groupedFilter) + ")";
        return "SELECT * FROM mem WHERE (" + sqlQuery + ")" + filterCondition + ";";
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

            return new ParseResult(String.join(" AND ", conditions), String.join(" AND ", filterConditions));
        } else {
            throw new IllegalArgumentException("Invalid memelang format: " + query);
        }
    }

    public static String memeClause(String query, List<String> filters) {
        // Handle OR (|) conditions at the top level
        if (query.contains("|")) {
            String[] clauses = query.split("\\|");
            List<String> sqlClauses = new ArrayList<>();
            for (String clause : clauses) {
                sqlClauses.add(memeClause(clause.trim(), filters));
            }
            return String.join(" OR ", sqlClauses);
        }

        // Handle AND (&) conditions at the top level with INTERSECT logic
        if (query.contains("&")) {
            String[] clauses = query.split("&");
            List<String> primaryConditions = new ArrayList<>();
            for (String clause : clauses) {
                String parsedClause = memeClause(clause.trim(), filters);
                primaryConditions.add("SELECT aid FROM mem WHERE " + parsedClause);
            }
            return "aid IN (" + String.join(" INTERSECT ", primaryConditions) + ")";
        }

        // Direct parsing when no nested expressions or logical operators are present
        ParseResult result = memeParse(query);
        if (!result.filter.isEmpty()) {
            filters.add("(" + result.filter + ")");
        }
        return result.clause;
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
            grouped.add("rid IN ('" + String.join("','", ridValues) + "')");
        }
        if (!bidValues.isEmpty()) {
            grouped.add("bid IN ('" + String.join("','", bidValues) + "')");
        }

        grouped.addAll(complexFilters);
        return grouped;
    }

    public static void meme2sqlTest() {
        String[] queries = {
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
        };

        for (String query : queries) {
            try {
                String generatedSql = meme2sql(query);
                System.out.println("Query: " + query);
                System.out.println("Generated SQL: " + generatedSql + "\n");
            } catch (Exception e) {
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
