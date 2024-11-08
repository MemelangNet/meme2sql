import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MemeLangQueryProcessor {

    // Database configuration constants
    private static final String DB_TYPE = "sqlite3";     // Default to 'sqlite3'. Options: 'sqlite3', 'mysql', 'postgres'
    private static final String DB_PATH = "data.sqlite"; // Default path for SQLite3
    private static final String DB_HOST = "localhost";   // Host for MySQL/Postgres
    private static final String DB_USER = "username";    // Username for MySQL/Postgres
    private static final String DB_PASSWORD = "password"; // Password for MySQL/Postgres
    private static final String DB_NAME = "database_name"; // Database name for MySQL/Postgres
    private static final String DB_TABLE = "meme";       // Default table name for queries

    public static void main(String[] args) {
        String memelangQuery = ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok";
        List<Map<String, Object>> results = memeQuery(memelangQuery);
        System.out.println(memeOut(results));
    }

    // Main function to process memelang query and return results
    public static List<Map<String, Object>> memeQuery(String memelangQuery) {
        // Remove all whitespace from the input
        memelangQuery = memelangQuery.replaceAll("\\s+", "");

        try {
            // Translate memelang to SQL
            String sqlQuery = memeSQL(memelangQuery);
            
            // Call the appropriate database function based on DB_TYPE constant
            switch (DB_TYPE) {
                case "sqlite3":
                    return memeSQLite3(sqlQuery);
                case "mysql":
                    return memeMySQL(sqlQuery);
                case "postgres":
                    return memePostgres(sqlQuery);
                default:
                    throw new IllegalArgumentException("Unsupported database type: " + DB_TYPE);
            }
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return Collections.singletonList(error);
        }
    }

    // Function to handle AND, OR conditions and translate to SQL
    public static String memeSQL(String query) {
        // If there are multiple OR clauses, separate each one with a UNION and wrap each in a SELECT statement
        if (query.contains("|")) {
            String[] clauses = query.split("\\|");
            List<String> sqlClauses = Arrays.stream(clauses)
                .map(clause -> "SELECT m.* FROM " + DB_TABLE + " m " + memeJunction(clause.trim()))
                .collect(Collectors.toList());
            return String.join(" UNION ", sqlClauses);
        }

        // If no OR, treat it as a single SELECT query
        return "SELECT m.* FROM " + DB_TABLE + " m " + memeJunction(query);
    }

    // Handle single clause logic for both AND (&) conditions and basic WHERE filtering
    public static String memeJunction(String query) {
        List<String> filters = new ArrayList<>();

        // Handle AND conditions
        if (query.contains("&")) {
            String[] clauses = query.split("&");
            List<String> havingConditions = new ArrayList<>();

            for (String clause : clauses) {
                Map<String, String> result = memeParse(clause.trim());
                havingConditions.add("SUM(CASE WHEN " + result.get("clause") + " THEN 1 ELSE 0 END) > 0");
                if (!result.get("filter").isEmpty()) {
                    filters.add("(" + result.get("filter") + ")");
                }
            }

            return "JOIN (SELECT aid FROM " + DB_TABLE + " GROUP BY aid HAVING " + 
                   String.join(" AND ", havingConditions) + ") AS aids ON m.aid = aids.aid" +
                   (!filters.isEmpty() ? " WHERE " + String.join(" OR ", memeFilterGroup(filters)) : "");
        }

        // No AND, so it's a single WHERE condition
        Map<String, String> result = memeParse(query);
        if (!result.get("filter").isEmpty()) {
            filters.add("(" + result.get("filter") + ")");
        }
        return "WHERE " + result.get("clause") +
               (!filters.isEmpty() ? " AND " + String.join(" OR ", memeFilterGroup(filters)) : "");
    }

    // Function to parse individual components of the memelang query
    public static Map<String, String> memeParse(String query) {
        String pattern = "^([A-Za-z0-9]*)\\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\\d*\\.?\\d*)$";
        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(query);

        if (matcher.find()) {
            String aid = matcher.group(1) != null ? matcher.group(1) : "";
            String rid = matcher.group(2) != null ? matcher.group(2) : "";
            String bid = matcher.group(3) != null ? matcher.group(3) : "";
            String operator = matcher.group(4) != null ? matcher.group(4).replace("#=", "=") : "=";
            String qnt = !matcher.group(5).isEmpty() ? matcher.group(5) : "1";

            List<String> conditions = new ArrayList<>();
            if (!aid.isEmpty()) conditions.add("aid='" + aid + "'");
            if (!rid.isEmpty()) conditions.add("rid='" + rid + "'");
            if (!bid.isEmpty()) conditions.add("bid='" + bid + "'");
            conditions.add("qnt" + operator + qnt);

            List<String> filterConditions = new ArrayList<>();
            if (!rid.isEmpty()) filterConditions.add("rid='" + rid + "'");
            if (!bid.isEmpty()) filterConditions.add("bid='" + bid + "'");

            Map<String, String> result = new HashMap<>();
            result.put("clause", "(" + String.join(" AND ", conditions) + ")");
            result.put("filter", String.join(" AND ", filterConditions));
            return result;
        } else {
            throw new IllegalArgumentException("Invalid memelang format: " + query);
        }
    }

    // Group filters to reduce SQL complexity
    public static List<String> memeFilterGroup(List<String> filters) {
        List<String> ridValues = new ArrayList<>();
        List<String> bidValues = new ArrayList<>();
        List<String> complexFilters = new ArrayList<>();

        for (String filter : filters) {
            Matcher ridMatcher = Pattern.compile("^\\(rid='([A-Za-z0-9]+)'\\)$").matcher(filter);
            Matcher bidMatcher = Pattern.compile("^\\(bid='([A-Za-z0-9]+)'\\)$").matcher(filter);

            if (ridMatcher.find()) {
                ridValues.add(ridMatcher.group(1));
            } else if (bidMatcher.find()) {
                bidValues.add(bidMatcher.group(1));
            } else {
                complexFilters.add(filter);
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

    // SQLite3 database query function
    public static List<Map<String, Object>> memeSQLite3(String sqlQuery) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlQuery)) {

            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();
                Map<String, Object> row = new HashMap<>(columns);
                for (int i = 1; i <= columns; i++) {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
            return results;
        }
    }

    // MySQL database query function
    public static List<Map<String, Object>> memeMySQL(String sqlQuery) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://" + DB_HOST + "/" + DB_NAME, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlQuery)) {

            List<Map<String, Object>> results = new ArrayList<>();
            while (rs.next()) {
                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();
                Map<String, Object> row = new HashMap<>(columns);
                for (int i = 1; i <= columns; i++) {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
            return results;
        }
    }

    // PostgreSQL database query function
    public static List<Map<String, Object>> memePostgres(String sqlQuery) throws SQLException {
        String url = "jdbc:postgresql://" + DB_HOST + "/" + DB_NAME;
        try (Connection conn = DriverManager.getConnection(url, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs =
