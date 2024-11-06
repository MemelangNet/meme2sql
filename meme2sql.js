// Main function to convert memelang to SQL
function meme2sql(memelang) {
    // Remove all whitespace from the input
    memelang = memelang.replace(/\s+/g, '');
    let filters = [];  // Collect outer filter conditions for rid/bid
    let sqlQuery = memeClause(memelang, filters);

    // Include outer filters if any are present
    let filterCondition = filters.length > 0 ? ` AND (${filters.join(" OR ")})` : "";
    return `SELECT * FROM mem WHERE (${sqlQuery})${filterCondition};`;
}

// Function to parse A.R:B=Q format
function memeParse(query) {
    // Regular expression to parse A.R:B=Q format
    const pattern = /^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$/;
    const match = query.match(pattern);

    if (match) {
        let [_, aid, rid, bid, operator, qnt] = match;
        operator = operator ? operator.replace('#=', '=') : '=';
        qnt = qnt !== '' ? qnt : '1';

        // Build conditions
        let conditions = [];
        if (aid) conditions.push(`aid='${aid}'`);
        if (rid) conditions.push(`rid='${rid}'`);
        if (bid) conditions.push(`bid='${bid}'`);
        conditions.push(`qnt${operator}${qnt}`);

        // Prepare filter for outer conditions
        let filterCond = [];
        if (rid) filterCond.push(`rid='${rid}'`);
        if (bid) filterCond.push(`bid='${bid}'`);
        return { clause: `(${conditions.join(' AND ')})`, filter: filterCond.join(' AND ') };
    } else {
        throw new Error(`Invalid memelang format: ${query}`);
    }
}

// Function to handle & and | operators in clauses
function memeClause(query, filters) {
    if (query.includes('|')) {
        // Split by OR operator
        let clauses = query.split('|');
        let sqlClauses = clauses.map(clause => memeClause(clause, filters));
        return sqlClauses.join(" OR ");
    }

    if (query.includes('&')) {
        // Split by AND operator
        let clauses = query.split('&');
        let primaryConditions = [];
        clauses.forEach(clause => {
            let result = memeParse(clause.trim());
            primaryConditions.push(`aid IN (SELECT aid FROM mem WHERE ${result.clause})`);
            if (result.filter) {
                filters.push(`(${result.filter})`);
            }
        });
        return primaryConditions.join(" AND ");
    }

    // Base case for single clause
    let result = memeParse(query);
    if (result.filter) {
        filters.push(`(${result.filter})`);
    }
    return result.clause;
}

// Test function
function meme2sqlTest() {
    // Define example memelang queries
    const queries = [
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
    queries.forEach(query => {
        try {
            const generatedSql = meme2sql(query);
            console.log(`Query: ${query}`);
            console.log(`Generated SQL: ${generatedSql}\n`);
        } catch (error) {
            console.log(`Error: ${error.message}\n`);
        }
    });
}

// Run the test
meme2sqlTest();
