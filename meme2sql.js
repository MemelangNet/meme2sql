function meme2sql(memelang) {
    // Remove all whitespace from the input
    memelang = memelang.replace(/\s+/g, '');
    let filters = [];  // Collect outer filter conditions for rid/bid
    let sqlQuery = memeClause(memelang, filters);

    // Process filters to group by rid and bid terms
    let groupedFilter = groupFilters(filters);

    // Include outer filters if any are present
    let filterCondition = groupedFilter.length > 0 ? ` AND (${groupedFilter.join(' OR ')})` : "";
    return `SELECT * FROM mem WHERE (${sqlQuery})${filterCondition};`;
}

function memeParse(query) {
    const pattern = /^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$/;
    let matches = query.match(pattern);

    if (matches) {
        let [_, aid, rid, bid, operator, qnt] = matches;
        aid = aid || null;
        rid = rid || null;
        bid = bid || null;
        operator = operator ? operator.replace('#=', '=') : '=';
        qnt = qnt || '1';

        let conditions = [];
        if (aid) conditions.push(`aid='${aid}'`);
        if (rid) conditions.push(`rid='${rid}'`);
        if (bid) conditions.push(`bid='${bid}'`);
        conditions.push(`qnt${operator}${qnt}`);

        let filterConditions = [];
        if (rid) filterConditions.push(`rid='${rid}'`);
        if (bid) filterConditions.push(`bid='${bid}'`);
        return { clause: `(${conditions.join(' AND ')})`, filter: filterConditions.join(' AND ') };
    } else {
        throw new Error(`Invalid memelang format: ${query}`);
    }
}

function memeClause(query, filters) {
    // Handle OR (|) conditions at the top level
    if (query.includes('|')) {
        let clauses = query.split('|');
        let sqlClauses = clauses.map(clause => memeClause(clause.trim(), filters));
        return sqlClauses.join(" OR ");
    }

    // Handle AND (&) conditions at the top level with INTERSECT logic
    if (query.includes('&')) {
        let clauses = query.split('&');
        let primaryConditions = clauses.map(clause => {
            let parsedClause = memeClause(clause.trim(), filters);
            return `SELECT aid FROM mem WHERE ${parsedClause}`;
        });
        return `aid IN (${primaryConditions.join(" INTERSECT ")})`;
    }

    // Direct parsing when no nested expressions or logical operators are present
    let result = memeParse(query);
    if (result.filter) {
        filters.push(`(${result.filter})`);
    }
    return result.clause;
}

function groupFilters(filters) {
    let ridValues = [];
    let bidValues = [];
    let complexFilters = [];

    for (let filter of filters) {
        let ridMatch = filter.match(/^\(rid='([A-Za-z0-9]+)'\)$/);
        let bidMatch = filter.match(/^\(bid='([A-Za-z0-9]+)'\)$/);

        if (ridMatch) {
            ridValues.push(ridMatch[1]);
        } else if (bidMatch) {
            bidValues.push(bidMatch[1]);
        } else {
            complexFilters.push(filter);  // Complex terms like (rid='letter' AND bid='ord')
        }
    }

    let grouped = [];
    if (ridValues.length > 0) {
        grouped.push(`rid IN ('${ridValues.join("','")}')`);
    }
    if (bidValues.length > 0) {
        grouped.push(`bid IN ('${bidValues.join("','")}')`);
    }

    return grouped.concat(complexFilters);
}

// Test function
function meme2sqlTest() {
    let queries = [
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

    for (let query of queries) {
        try {
            let generatedSql = meme2sql(query);
            console.log(`Query: ${query}`);
            console.log(`Generated SQL: ${generatedSql}\n`);
        } catch (error) {
            console.error(`Error: ${error.message}\n`);
        }
    }
}

// Run tests
meme2sqlTest();
