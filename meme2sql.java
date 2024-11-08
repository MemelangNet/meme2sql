import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemeLangQueryProcessor {

    // Database configuration constants
    private static final String DB_TYPE = System.getenv().getOrDefault("DB_TYPE", "sqlite3"); // Options: 'sqlite3', 'mysql', 'postgres'
    private static final String DB_PATH = System.getenv().getOrDefault("DB_PATH", "data.sqlite"); // Path for SQLite3
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost"); // Host for MySQL/Postgres
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "username"); // Username for MySQL/Postgres
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "password"); // Password for MySQL/Postgres
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "database_name"); // Database name for MySQL/Postgres
    private static final String DB_TABLE = System.getenv().getOrDefault("DB_TABLE", "meme"); // Default table name for queries

    private static Connection connection = null;

    // Main function to process memelang query and return results
    public static List<Map<String, Object>> memeQuery(String memelangQuery) throws SQLException {
        String sqlQuery = memeSQL(memelangQuery);
        return executeQuery(sqlQuery);
    }

    // Generate SQL from the memelang query
    public static String memeSQL(String query) {
        query = query.replaceAll("\\s+", ""); // Remove all whitespace

        // Split the query by | for OR conditions
        String[] orClauses = query.split("\\|");
        StringBuilder sqlClauses = new StringBuilder();
        for (String clause : orClauses) {
            if (sqlClauses.length() > 0) sqlClauses.append(" UNION ");
            sqlClauses.append(memeClause(clause.trim()));
        }
        return sqlClauses.toString();
    }

    private static String memeClause(String clause) {
        // Check for & in the clause for AND conditions
        if (clause.contains("&")) {
            return "SELECT m.* FROM " + DB_TABLE + " m " + memeJunction(clause);
        } else {
            // Handle simple clause with no &
            ParsedClause result = memeParse(clause);
            return "SELECT * FROM " + DB_TABLE + " WHERE " + result.clause;
        }
    }

    private static String memeJunction(String query) {
        List<String> filters = new ArrayList<>();
        List<String> havingConditions = new ArrayList<>();

        // Split the query by '&' and process each clause
        String[] clauses = query.split("&");
        for (String clause : clauses) {
            clause = clause.trim();
            boolean isAndNotCondition = clause.startsWith("!");
            if (isAndNotCondition) clause = clause.substring(1);

            ParsedClause result = memeParse(clause);
            havingConditions.add("SUM(CASE WHEN " + result.clause + " THEN 1 ELSE 0 END) " +
                    (isAndNotCondition ? "= 0" : "> 0"));

            if (result.filter != null && !result.filter.isEmpty()) {
                filters.add("(" + result.filter + ")");
            }
        }

        return "JOIN (SELECT aid FROM " + DB_TABLE + " GROUP BY aid HAVING " +
                String.join(" AND ", havingConditions) + ") AS aids ON m.aid = aids.aid" +
                (filters.isEmpty() ? "" : " WHERE " + String.join(" OR ", memeFilterGroup(filters)));
    }

    private static ParsedClause memeParse(String query) {
        String pattern = "^([A-Za-z0-9]*)\\.([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\\d*\\.?\\d*)$";
        if (query.matches(pattern)) {
            String[] parts = query.split("[:.#]");
            String aid = parts.length > 0 ? parts[0] : null;
            String rid = parts.length > 1 ? parts[1] : null;
            String bid = parts.length > 2 ? parts[2] : null;
            String operator = parts.length > 3 ? parts[3].replace("#=", "=") : "=";
            String qnt = parts.length > 4 && !parts[4].isEmpty() ? parts[4] : "1";

            List<String> conditions = new ArrayList<>();
            if (aid != null) conditions.add("aid='" + aid + "'");
            if (rid != null) conditions.add("rid='" + rid + "'");
            if (bid != null) conditions.add("bid='" + bid + "'");
            conditions.add("qnt" + operator + qnt);

            List<String> filterConditions = new ArrayList<>();
            if (rid != null) filterConditions.add("rid='" + rid + "'");
            if (bid != null) filterConditions.add("bid='" + bid + "'");

            return new ParsedClause(String.join(" AND ", conditions), String.join(" AND ", filterConditions));
        } else {
            throw new IllegalArgumentException("Invalid memelang format: " + query);
        }
    }

    private static List<String> memeFilterGroup(List<String> filters) {
        List<String> grouped = new ArrayList<>();
        List<String> ridValues = new ArrayList<>();
        List<String> bidValues = new ArrayList<>();
        List<String> complexFilters = new ArrayList<>();

        for (String filter : filters) {
            if (filter.matches("\\(rid='([A-Za-z0-9]+)'\\)")) {
                ridValues.add(filter.split("=")[1].replace("'", "").replace(")", ""));
            } else if (filter.matches("\\(bid='([A-Za-z0-9]+)'\\)")) {
                bidValues.add(filter.split("=")[1].replace("'", "").replace(")", ""));
            } else {
                complexFilters.add(filter);
            }
        }

        if (!ridValues.isEmpty()) grouped.add("m.rid IN ('" + String.join("','", ridValues) + "')");
        if (!bidValues.isEmpty()) grouped.add("m.bid IN ('" + String.join("','", bidValues) + "')");

        grouped.addAll(complexFilters);
        return grouped;
    }

    private static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            switch (DB_TYPE) {
                case "sqlite3":
                    connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
                    break;
                case "mysql":
                    connection = DriverManager.getConnection("jdbc:mysql://" + DB_HOST + "/" + DB_NAME, DB_USER, DB_PASSWORD);
                    break;
                case "postgres":
                    connection = DriverManager.getConnection("jdbc:postgresql://" + DB_HOST + "/" + DB_NAME, DB_USER, DB_PASSWORD);
                    break;
                default:
                    throw new SQLException("Unsupported database type: " + DB_TYPE);
            }
        }
        return connection;
    }

    private static List<Map<String, Object>> executeQuery(String sqlQuery) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sqlQuery); ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
        }
        return results;
    }

    // Class to hold parsed clauses
    private static class ParsedClause {
        String clause;
        String filter;

        ParsedClause(String clause, String filter) {
            this.clause = clause;
            this.filter = filter;
        }
    }

    // Test function
    public static void main(String[] args) {
        List<String> queries = List.of(
            "ant.admire:amsterdam #= 0",
            "ant.believe:cairo",
            ".admire | .believe"
            // Add other test cases as needed
        );

        for (String query : queries) {
            try {
                System.out.println("Memelang Query: " + query);
                String sqlQuery = memeSQL(query);
                System.out.println("Generated SQL: " + sqlQuery);
                List<Map<String, Object>> results = memeQuery(query);
                System.out.println("Results in Memelang Format:\n" + memeOut(results) + "\n");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    // Format results to Memelang format
    public static String memeOut(List<Map<String, Object>> results) {
        StringBuilder output = new StringBuilder();
        for (Map<String, Object> row : results) {
            output.append(row.get("aid")).append(".").append(row.get("rid")).append(":").append(row.get("bid"))
                    .append("=").append(row.get("qnt")).append(";\n");
        }
        return output.toString();
    }
}
