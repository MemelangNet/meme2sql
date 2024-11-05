<?php

function meme2sql($memelang) {
    // Base case: Handle the atomic "a.r:b=q" pattern
    if (preg_match('/^(\w*)\.?(\w*):?(\w*)?([<>=#]*)?(\d*)$/', trim($memelang), $matches)) {
        $aid = $matches[1] ?: null;
        $rid = $matches[2] ?: null;
        $bid = $matches[3] ?: null;
        $operator = str_replace('#=', '=', $matches[4] ?: '=');
        $qnt = $matches[5] ?: '1';

        $conditions = [];
        if ($aid) $conditions[] = "aid='$aid'";
        if ($rid) $conditions[] = "rid='$rid'";
        if ($bid) $conditions[] = "bid='$bid'";
        $conditions[] = "qnt $operator $qnt";

        return "SELECT * FROM mem WHERE (" . implode(' AND ', $conditions) . ")";
    }

    // Recursive case: Handle OR (|) and AND (&) junctions
    if (strpos($memelang, '|') !== false) {
        // Split by '|' for OR conditions
        $parts = explode('|', $memelang);
        $subqueries = array_map('meme2sql', $parts);
        $combined = implode(' OR ', array_map(function($query) {
            return '(' . preg_replace('/^SELECT \* FROM mem WHERE /', '', $query) . ')';
        }, $subqueries));
        return "SELECT * FROM mem WHERE ($combined)";
    } elseif (strpos($memelang, '&') !== false) {
        // Split by '&' for AND conditions
        $parts = explode('&', $memelang);
        $subqueries = array_map('meme2sql', $parts);

        // Extract aid conditions using INTERSECT for AND
        $intersectConditions = implode(' INTERSECT ', array_map(function($query) {
            return "SELECT aid FROM mem WHERE " . preg_replace('/^SELECT \* FROM mem WHERE /', '', $query);
        }, $subqueries));
        $combined = implode(' OR ', array_map(function($query) {
            return preg_replace('/^SELECT \* FROM mem WHERE /', '', $query);
        }, $subqueries));
        
        return "SELECT * FROM mem WHERE (
            aid IN ($intersectConditions)
            AND ($combined)
        )";
    }

    return "";
}

// Sample usage
$queries = [
    "ant.admire:amsterdam=0",
    "ant.believe:cairo",
    "ant.believe",
    "ant",
    ".admire",
    ":amsterdam",
    ".letter#=2",
    ".letter>2",
    "ant | :cairo",
    ".admire | .believe",
    ".discover & .explore",
    ".admire & .believe & .letter<5",
    ".admire & .explore & :amsterdam | .letter:ord<2 | :cairo"
];

foreach ($queries as $query) {
    echo "/* $query */\n";
    echo meme2sql($query) . "\n\n";
}

?>