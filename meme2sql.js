function meme2sql(memelang) {
    // Remove all whitespace from the input
    memelang = memelang.replace(/\s+/g, '');
    let filters = [];  // Collect outer filter conditions for rid/bid
    let sqlQuery = memeClause(memelang, filters);

    // Process filters to group by rid and bid terms
    let groupedFilter = groupFilters(filters);

    // Include outer filters if any are present
    let filterCondition = groupedFilter.length > 0 ? ` WHERE ${groupedFilter.join(' OR ')}` : "";
    return sqlQuery + filterCondition + ";";
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
    // Handle OR (|) conditions by generating complete SELECT statements and combining them with UNION
    if (query.includes('|')) {
        const clauses = query.split('|');
        const sqlClauses = clauses.map(clause => {
            const subFilters = [];  // Independent filter set for each OR clause
            const sqlPart = `SELECT m.* FROM mem m ${memeClause(clause.trim(), subFilters)}`;
            const filterCondition = subFilters.length > 0 ? ` WHERE ${groupFilters(subFilters).join(' OR ')}` : "";
            return sqlPart + filterCondition;
        });
        return sqlClauses.join(" UNION ");
    }

    // Handle AND (&) conditions by generating JOIN with GROUP BY and HAVING
    if (query.includes('&')) {
        const clauses = query.split('&');
        const havingConditions = clauses.map(clause => {
            const result = memeParse(clause.trim());
            if (result.filter) filters.push(`(${result.filter})`);
            return `SUM(CASE WHEN ${result.clause} THEN 1 ELSE 0 END) > 0`;
        });
        return `JOIN (SELECT aid FROM mem GROUP BY aid HAVING ${havingConditions.join(' AND ')}) AS aids ON m.aid = aids.aid`;
    }

    // Direct parsing when no nested expressions or logical operators are present
    const result = memeParse(query);
    if (result.filter) {
        filters.push(`(${result.filter})`);
    }
    return `WHERE ${result.clause}`;
}

function groupFilters(filters) {
    let ridValues = [];
    let bidValues = [];
    let complexFilters = [];

    for (const filterCondition of filters) {
        let ridMatch = filterCondition.match(/^\(rid='([A-Za-z0-9]+)'\)$/);
        let bidMatch = filterCondition.match(/^\(bid='([A-Za-z0-9]+)'\)$/);

        if (ridMatch) {
            ridValues.push(ridMatch[1]);
        } else if (bidMatch) {
            bidValues.push(bidMatch[1]);
        } else {
            complexFilters.push(filterCondition);  // Complex terms like (rid='letter' AND bid='ord')
        }
    }

    const grouped = [];
    if (ridValues.length > 0) {
        grouped.push(`m.rid IN ('${ridValues.join("','")}')`);
    }
    if (bidValues.length > 0) {
        grouped.push(`m.bid IN ('${bidValues.join("','")}')`);
    }

    return grouped.concat(complexFilters);
}

// Test function
function meme2sqlTest() {
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
        ".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok",
        ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok"
    ];

    for (const query of queries) {
        try {
            const generatedSql = meme2sql(query);
            console.log(`Query: ${query}`);
            console.log(`Generated SQL: ${generatedSql}\n`);
        } catch (error) {
            console.error(`Error: ${error.message}\n`);
        }
    }
}

// Run tests
meme2sqlTest();
