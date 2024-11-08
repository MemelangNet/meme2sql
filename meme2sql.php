<?php

// Run the test function
memeTest();

// Database configuration constants
define('DB_TYPE', 'sqlite3');      // Default to 'sqlite3'. Options: 'sqlite3', 'mysql', 'postgres'
define('DB_PATH', 'data.sqlite');   // Default path for SQLite3
define('DB_HOST', 'localhost');    // Host for MySQL/Postgres
define('DB_USER', 'username');     // Username for MySQL/Postgres
define('DB_PASSWORD', 'password'); // Password for MySQL/Postgres
define('DB_NAME', 'database_name'); // Database name for MySQL/Postgres
define('DB_TABLE', 'meme');        // Default table name for queries

// Main function to process memelang query and return results
function memeQuery($memelangQuery) {
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

// Updated function to handle AND, OR, AND-NOT conditions and translate to SQL
function memeSQL($query) {
	// Remove all whitespace from the input
	$query = preg_replace('/\s+/', '', $query);

	// Split the query by | for OR conditions
	$orClauses = explode('|', $query);
	$sqlClauses = array_map(function($clause) {
		// Check for & in the clause for AND conditions
		if (strpos($clause, '&') !== false) {
			return "SELECT m.* FROM " . DB_TABLE . " m " . memeJunction(trim($clause));
		} else {
			// Handle simple clause with no &
			$result = memeParse(trim($clause));
			return "SELECT * FROM " . DB_TABLE . " WHERE " . $result['clause'];
		}
	}, $orClauses);

	// Join the OR-separated clauses using UNION for the final SQL
	return implode(" UNION ", $sqlClauses);
}

// Handle single clause logic for AND (&), AND-NOT (&!), and basic WHERE filtering
function memeJunction($query) {
	$filters = [];
	$havingConditions = [];

	// Split the query by '&' and process each clause
	$clauses = explode('&', $query);
	foreach ($clauses as $clause) {
		$clause = trim($clause);

		// Determine if the clause is an AND-NOT by checking if it starts with '!'
		$isAndNotCondition = (strpos($clause, '!') === 0);
		$clause = $isAndNotCondition ? substr($clause, 1) : $clause;

		$result = memeParse($clause);
		$havingConditions[] = "SUM(CASE WHEN " . $result['clause'] . " THEN 1 ELSE 0 END) " . ($isAndNotCondition ? "= 0" : "> 0");

		if ($result['filter']) {
			$filters[] = "(" . $result['filter'] . ")";
		}
	}

	return "JOIN (SELECT aid FROM " . DB_TABLE . " GROUP BY aid HAVING " . implode(" AND ", $havingConditions) . ") AS aids ON m.aid = aids.aid" .
		(!empty($filters) ? " WHERE " . implode(" OR ", memeFilterGroup($filters)) : "");
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

// Test function with various example queries
function memeTest() {
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
		"ant | :bangkok",
		".admire | .believe",
		".admire | .believe | .letter > 2",
		".discover &! .explore",
		".create &! :dubai",
		".admire &! .believe & .letter:ord < 5",
		".admire &! .believe &! .letter:ord < 5",
		".discover & .explore:amsterdam | :cairo",
		".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok",
		".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok",
		".admire & .explore &! :bangkok | .letter:ord < 3 & :amsterdam"
	];

	foreach ($queries as $query) {
		echo "Memelang Query: $query\n";
		$sqlQuery = memeSQL($query);
		echo "Generated SQL: $sqlQuery\n";
		$results = memeQuery($query);
		echo "Results in Memelang Format:\n" . memeOut($results) . "\n\n";
	}
}

?>
