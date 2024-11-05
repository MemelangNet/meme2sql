function meme2sql(memelang) {
    memelang = memelang.trim();

    // Base case: Handle the atomic "a.r:b=q" pattern
    const match = memelang.match(/^(\w*)\.?(\w*):?(\w*)?([<>=#]*)?(\d*)$/);
    if (match) {
        const aid = match[1] || null;
        const rid = match[2] || null;
        const bid = match[3] || null;
        const operator = match[4] ? match[4].replace('#=', '=') : '=';
        const qnt = match[5] || '1';

        let conditions = [];
        if (aid) conditions.push(`aid='${aid}'`);
        if (rid) conditions.push(`rid='${rid}'`);
        if (bid) conditions.push(`bid='${bid}'`);
        conditions.push(`qnt ${operator} ${qnt}`);

        return `SELECT * FROM mem WHERE (${conditions.join(' AND ')})`;
    }

    // Recursive case: Handle OR (|) and AND (&) junctions
    if (memelang.includes('|')) {
        // Split by '|' for OR conditions
        const parts = memelang.split('|');
        const subqueries = parts.map(meme2sql);
        const combined = subqueries
            .map(query => `(${query.replace(/^SELECT \* FROM mem WHERE /, '')})`)
            .join(' OR ');
        return `SELECT * FROM mem WHERE (${combined})`;
    } else if (memelang.includes('&')) {
        // Split by '&' for AND conditions
        const parts = memelang.split('&');
        const subqueries = parts.map(meme2sql);

        // Extract aid conditions using INTERSECT for AND
        const intersectConditions = subqueries
            .map(query => `SELECT aid FROM mem WHERE ${query.replace(/^SELECT \* FROM mem WHERE /, '')}`)
            .join(' INTERSECT ');
        const combined = subqueries
            .map(query => query.replace(/^SELECT \* FROM mem WHERE /, ''))
            .join(' OR ');

        return `SELECT * FROM mem WHERE (
            aid IN (${intersectConditions})
            AND (${combined})
        )`;
    }

    return '';
}

// Sample usage
const queries = [
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

queries.forEach(query => {
    console.log(`/* ${query} */`);
    console.log(meme2sql(query));
    console.log();
});
