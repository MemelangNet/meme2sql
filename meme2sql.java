import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MemeLangToSQL {

    public static String meme2sql(String memelang) {
        // Remove all whitespace from the input
        memelang = memelang.replaceAll("\\s+", "");
        List<String> filters = new ArrayList<>();  // Collect outer filter conditions for rid/bid
        String sqlQuery = memeClause(memelang, filters);

        // Include outer filters if any are present
        String filterCondition = !filters.isEmpty() ? " AND (" + String.join(" OR ", filters) + ")" : "";
        return "SELECT * FROM mem WHERE (" + sqlQuery + ")" + filterCondition + ";";
    }

    public static ParseResult memeParse(String query) {
        // Regular expression to parse A.R:B=Q format
        Pattern pattern = Pattern.compile("^([A-Za-z0-9]*)\\.([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\\d*\\.?\\d*)$");
        Matcher matcher = pattern.matcher(query);

        if (matcher.matches()) {
            String aid = matcher.group(1).isEmpty() ? null : matcher.group(1);
            String rid = matcher.group(2).isEmpty() ? null : matcher.group(2);
            String bid = matcher.group(3).isEmpty() ? null : matcher.group(3);
            String operator = matcher.group(4).isEmpty() ? "=" : matcher.group(4).replace("#=", "=");
            String qnt = matcher.group(5).isEmpty() ? "1" : matcher.group(5);

            // Build conditions
            List<String> conditions = new ArrayList<>();
            if (aid != null) conditions.add("aid='" + aid + "'");
            if (rid != null) conditions.add("rid='" + rid + "'");
            if (bid != null) conditions.add("bid='" + bid + "'");
            conditions.add("qnt" + operator + qnt);

            // Prepare filter for outer conditions
            List<String> filterCond = new ArrayList<>();
            if (rid != null) filterCond.add("rid='" + rid + "'");
            if (bid != null) filterCond.add("bid='" + bid + "'");
            return new ParseResult("(" + String.join(" AND ", conditions) + ")", String.join(" AND ", filterCond));
        } else {
            throw new IllegalArgumentException("Invalid memelang format: " + query);
        }
    }

    public static String memeClause(String query, List<String> filters) {
        if (query.contains("|")) {
            // Split by OR operator
            String[] clauses = query.split("\\|");
            List<String> sqlClauses = new ArrayList<>();
            for (String clause : clauses) {
                sqlClauses.add(memeClause(clause, filters));
            }
            return String.join(" OR ", sqlClauses);
        }

        if (query.contains("&")) {
            // Split by AND operator
            String[] clauses = query.split("&");
            List<String> primaryConditions = new ArrayList<>();
            for (String clause : clauses) {
                ParseResult result = memeParse(clause.trim());
                primaryConditions.add("aid IN (SELECT aid FROM mem WHERE " + result.clause + ")");
                if (!result.filter.isEmpty()) {
                    filters.add("(" + result.filter + ")");
                }
            }
            return String.join(" AND ", primaryConditions);
        }

        // Base case for single clause
        ParseResult result = memeParse(query);
        if (!result.filter.isEmpty()) {
            filters.add("(" + result.filter + ")");
        }
        return result.clause;
    }

    public static void meme2sqlTest() {
        // Define example memelang queries
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
                ".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok"
        };

        // Run each query and display the generated SQL
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

    // Inner class to hold parsing results
    static class ParseResult {
        String clause;
        String filter;

        ParseResult(String clause, String filter) {
            this.clause = clause;
            this.filter = filter;
        }
    }

    public static void main(String[] args) {
        // Run the test
        meme2sqlTest();
    }
}
