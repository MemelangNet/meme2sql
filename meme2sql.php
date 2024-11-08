<?php

/**
 * Memelang SQL Query Processor
 * 
 * This script allows the processing of Memelang queries and translation to SQL.
 * It supports SQLite3, MySQL, and PostgreSQL databases, translating complex Memelang
 * expressions into SQL that can be executed on a specified table.
 * 
 * Usage:
 * 1. Set the database connection constants (DB_TYPE, DB_PATH, DB_HOST, DB_USER, DB_PASSWORD, DB_NAME, DB_TABLE).
 * 2. Use `memeQuery($memelangQuery)` to process a Memelang query.
 * 3. The function returns results in an array format, or an error message if processing fails.
 * 4. Use `memeOut($results)` to format the output as Memelang-style strings.
**/

// Database configuration constants
define('DB_TYPE', 'sqlite3');      // Default to 'sqlite3'. Options: 'sqlite3', 'mysql', 'postgres'
define('DB_PATH', 'data.sqlite');  // Default path for SQLite3
define('DB_HOST', 'localhost');    // Host for MySQL/Postgres
define('DB_USER', 'username');     // Username for MySQL/Postgres
define('DB_PASSWORD', 'password'); // Password for MySQL/Postgres
define('DB_NAME', 'database_name'); // Database name for MySQL/Postgres
define('DB_TABLE', 'meme');        // Default table name for queries

// Example Usage
$memelangQuery = ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok";  // Sample memelang query
$results = memeQuery($memelangQuery);
echo memeOut($results);

// Main function to process memelang query and return results
function memeQuery($memelangQuery) {
	// Remove all whitespace from the input
	$memelangQuery = preg_replace('/\s+/', '', $memelangQuery);
	
	try {
		// Translate memelang to SQL
		$sqlQuery = memeSQL($memelangQuery);
		// Call the appropriate database function based on DB_TYPE constant
		switch (DB_TYPE) {
			case 'sqlite3':
				return memeSQLite3($sqlQuery);
			case 'mysql':
				return memeMySQL($sqlQuery);
			case 'postgres':
				return memePostgres($sqlQuery);
			default:
				throw new Exception("Unsupported database type: " . DB_TYPE);
		}
	} catch (Exception $e) {
		return "Error: " . $e->getMessage();
	}
}

// Function to handle AND, OR conditions and translate to SQL
function memeSQL($query) {
	// If there are multiple OR clauses, separate each one with a UNION and wrap each in a SELECT statement
	if (strpos($query, '|') !== false) {
		$clauses = explode('|', $query);
		$sqlClauses = array_map(function($clause) {
			return "SELECT m.* FROM " . DB_TABLE . " m " . memeJunction($clause);
		}, $clauses);
		return implode(" UNION ", $sqlClauses);
	}

	// If no OR, treat it as a single SELECT query
	return "SELECT m.* FROM " . DB_TABLE . " m " . memeJunction($query);
}

// Handle single clause logic for both AND (&) conditions and basic WHERE filtering
function memeJunction($query) {
	$filters = [];
	
	// Handle AND conditions
	if (strpos($query, '&') !== false) {
		$clauses = explode('&', $query);
		$havingConditions = [];
		foreach ($clauses as $clause) {
			$result = memeParse(trim($clause));
			$havingConditions[] = "SUM(CASE WHEN " . $result['clause'] . " THEN 1 ELSE 0 END) > 0";
			if ($result['filter']) {
				$filters[] = "(" . $result['filter'] . ")";
			}
		}
		return "JOIN (SELECT aid FROM " . DB_TABLE . " GROUP BY aid HAVING " . implode(" AND ", $havingConditions) . ") AS aids ON m.aid = aids.aid" .
			(!empty($filters) ? " WHERE " . implode(" OR ", memeFilterGroup($filters)) : "");
	}

	// No AND, so it's a single WHERE condition
	$result = memeParse($query);
	if ($result['filter']) {
		$filters[] = "(" . $result['filter'] . ")";
	}
	return "WHERE " . $result['clause'] .
		(!empty($filters) ? " AND " . implode(" OR ", memeFilterGroup($filters)) : "");
}

// Function to parse individual components of the memelang query
function memeParse($query) {
	$pattern = '/^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$/';
	$matches = [];
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

		$filterConditions = [];
		if ($rid) $filterConditions[] = "rid='$rid'";
		if ($bid) $filterConditions[] = "bid='$bid'";
		return ["clause" => "(" . implode(' AND ', $conditions) . ")", "filter" => implode(' AND ', $filterConditions)];
	} else {
		throw new Exception("Invalid memelang format: $query");
	}
}

// Group filters to reduce SQL complexity
function memeFilterGroup($filters) {
	$ridValues = [];
	$bidValues = [];
	$complexFilters = [];

	foreach ($filters as $filter) {
		if (preg_match("/^\\(rid='([A-Za-z0-9]+)'\\)$/", $filter, $matches)) {
			$ridValues[] = $matches[1];
		} elseif (preg_match("/^\\(bid='([A-Za-z0-9]+)'\\)$/", $filter, $matches)) {
			$bidValues[] = $matches[1];
		} else {
			$complexFilters[] = $filter;
		}
	}

	$grouped = [];
	if (!empty($ridValues)) {
		$grouped[] = "m.rid IN ('" . implode("','", $ridValues) . "')";
	}
	if (!empty($bidValues)) {
		$grouped[] = "m.bid IN ('" . implode("','", $bidValues) . "')";
	}

	return array_merge($grouped, $complexFilters);
}

// SQLite3 database query function
function memeSQLite3($sqlQuery) {
	$db = new SQLite3(DB_PATH);
	$results = [];
	$queryResult = $db->query($sqlQuery);

	while ($row = $queryResult->fetchArray(SQLITE3_ASSOC)) {
		$results[] = $row;
	}

	$db->close();
	return $results;
}

// MySQL database query function
function memeMySQL($sqlQuery) {
	$connection = new mysqli(DB_HOST, DB_USER, DB_PASSWORD, DB_NAME);
	if ($connection->connect_error) {
		throw new Exception("Connection failed: " . $connection->connect_error);
	}

	$results = [];
	$queryResult = $connection->query($sqlQuery);
	if ($queryResult) {
		while ($row = $queryResult->fetch_assoc()) {
			$results[] = $row;
		}
	} else {
		throw new Exception("Query failed: " . $connection->error);
	}

	$connection->close();
	return $results;
}

// PostgreSQL database query function
function memePostgres($sqlQuery) {
	$connectionString = "host=" . DB_HOST . " dbname=" . DB_NAME . " user=" . DB_USER . " password=" . DB_PASSWORD;
	$connection = pg_connect($connectionString);
	if (!$connection) {
		throw new Exception("Connection failed: " . pg_last_error());
	}

	$results = [];
	$queryResult = pg_query($connection, $sqlQuery);
	if ($queryResult) {
		while ($row = pg_fetch_assoc($queryResult)) {
			$results[] = $row;
		}
	} else {
		throw new Exception("Query failed: " . pg_last_error($connection));
	}

	pg_close($connection);
	return $results;
}

// Format query results as memelang
function memeOut($results) {
	$memelangOutput = [];
	foreach ($results as $row) {
		$memelangOutput[] = "{$row['aid']}.{$row['rid']}:{$row['bid']}={$row['qnt']}";
	}
	return implode(";\n", $memelangOutput);
}

?>
