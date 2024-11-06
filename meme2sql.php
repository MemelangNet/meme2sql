<?php

// Run the test with example queries
meme2sql_test();

function meme2sql($memelang) {
    // Remove all whitespace from the input
    $memelang = preg_replace('/\s+/', '', $memelang);
    $filters = [];  // Collect outer filter conditions for rid/bid
    $sqlQuery = memeClause($memelang, $filters);

    // Include outer filters if any are present
    $filterCondition = !empty($filters) ? " AND (" . implode(" OR ", $filters) . ")" : "";
    return "SELECT * FROM mem WHERE ($sqlQuery)$filterCondition;";
}

function memeParse($query) {
    // Regular expression to parse A.R:B=Q format
    $pattern = '/^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$/';

    if (preg_match($pattern, $query, $matches)) {
        $aid = $matches[1] ?: null;
        $rid = $matches[2] ?: null;
        $bid = $matches[3] ?: null;
        $operator = str_replace('#=', '=', $matches[4] ?: '=');
        $qnt = $matches[5] !== '' ? $matches[5] : '1';

        $conditions = [];
        if ($aid) $conditions[] = "aid='$aid'";
        if ($rid) $conditions[] = "rid='$rid'";
        if ($bid) $conditions[] = "bid='$bid'";
        $conditions[] = "qnt$operator$qnt";

        // Prepare filter for outer conditions based on rid/bid
        $filter = [];
        if ($rid) $filter[] = "rid='$rid'";
        if ($bid) $filter[] = "bid='$bid'";
        return ["clause" => "(" . implode(' AND ', $conditions) . ")", "filter" => implode(' AND ', $filter)];
    } else {
        throw new Exception("Invalid memelang format: $query");
    }
}

function memeClause($query, &$filters) {
    if (strpos($query, '|') !== false) {
        // Split by OR operator
        $clauses = explode('|', $query);
        $sqlClauses = array_map(function($clause) use (&$filters) {
            return memeClause($clause, $filters);
        }, $clauses);
        return implode(" OR ", $sqlClauses);
    }

    if (strpos($query, '&') !== false) {
        // Split by AND operator
        $clauses = explode('&', $query);
        $primaryConditions = [];
        foreach ($clauses as $clause) {
            $result = memeParse(trim($clause));
            $primaryConditions[] = "aid IN (SELECT aid FROM mem WHERE " . $result['clause'] . ")";
            if ($result['filter']) {
                $filters[] = "(" . $result['filter'] . ")";
            }
        }
        return implode(" AND ", $primaryConditions);
    }

    // Base case for single clause
    $result = memeParse($query);
    if ($result['filter']) {
        $filters[] = "(" . $result['filter'] . ")";
    }
    return $result['clause'];
}

// Test function
function meme2sql_test() {
    // Define example memelang queries
    $queries = [
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
    ];

    // Run each query and display the generated SQL
    foreach ($queries as $query) {
        try {
            $generated_sql = meme2sql($query);
            echo "Query: $query\n";
            echo "Generated SQL: $generated_sql\n\n";
        } catch (Exception $e) {
            echo "Error: " . $e->getMessage() . "\n\n";
        }
    }
}
