<?php

// Execute the test function
meme2sql_test();

function meme2sql($memelang) {
    // Remove all whitespace from the input
    $memelang = preg_replace('/\s+/', '', $memelang);
    $filters = [];  // Collect outer filter conditions for rid/bid
    $sqlQuery = memeClause($memelang, $filters);

    // Process filters to group by rid and bid terms
    $groupedFilter = groupFilters($filters);

    // Include outer filters if any are present
    $filterCondition = !empty($groupedFilter) ? " AND (" . implode(" OR ", $groupedFilter) . ")" : "";
    return "SELECT * FROM mem WHERE ($sqlQuery)$filterCondition;";
}

function memeParse($query) {
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

        $filter = [];
        if ($rid) $filter[] = "rid='$rid'";
        if ($bid) $filter[] = "bid='$bid'";
        return ["clause" => "(" . implode(' AND ', $conditions) . ")", "filter" => implode(' AND ', $filter)];
    } else {
        throw new Exception("Invalid memelang format: $query");
    }
}

function memeClause($query, &$filters) {
    // Handle OR (|) conditions at the top level
    if (strpos($query, '|') !== false) {
        $clauses = explode('|', $query);
        $sqlClauses = array_map(function($clause) use (&$filters) {
            return memeClause(trim($clause), $filters);
        }, $clauses);
        return implode(" OR ", $sqlClauses);
    }

    // Handle AND (&) conditions at the top level with INTERSECT logic
    if (strpos($query, '&') !== false) {
        $clauses = explode('&', $query);
        $primaryConditions = [];
        foreach ($clauses as $clause) {
            $parsedClause = memeClause(trim($clause), $filters);
            $primaryConditions[] = "SELECT aid FROM mem WHERE $parsedClause";
        }
        return "aid IN (" . implode(" INTERSECT ", $primaryConditions) . ")";
    }

    // Direct parsing when no nested expressions or logical operators are present
    $result = memeParse($query);
    if ($result['filter']) {
        $filters[] = "(" . $result['filter'] . ")";
    }
    return $result['clause'];
}

// Function to group single-variable rid and bid terms in filters
function groupFilters($filters) {
    $ridValues = [];
    $bidValues = [];
    $complexFilters = [];

    foreach ($filters as $filter) {
        if (preg_match("/^\\(rid='([A-Za-z0-9]+)'\\)$/", $filter, $matches)) {
            $ridValues[] = $matches[1];
        } elseif (preg_match("/^\\(bid='([A-Za-z0-9]+)'\\)$/", $filter, $matches)) {
            $bidValues[] = $matches[1];
        } else {
            $complexFilters[] = $filter;  // Complex terms like (rid='letter' AND bid='ord')
        }
    }

    $grouped = [];
    if (!empty($ridValues)) {
        $grouped[] = "rid IN ('" . implode("','", $ridValues) . "')";
    }
    if (!empty($bidValues)) {
        $grouped[] = "bid IN ('" . implode("','", $bidValues) . "')";
    }

    return array_merge($grouped, $complexFilters);
}

// Test function
function meme2sql_test() {
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
        ".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok",
        ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok"
    ];

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

?>
