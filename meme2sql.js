const os = require('os');

// Database configuration constants
const DB_TYPE = process.env.DB_TYPE || 'sqlite3'; // Options: 'sqlite3', 'mysql', 'postgres'
const DB_PATH = process.env.DB_PATH || 'data.sqlite'; // Path for SQLite3
const DB_HOST = process.env.DB_HOST || 'localhost'; // Host for MySQL/Postgres
const DB_USER = process.env.DB_USER || 'username'; // Username for MySQL/Postgres
const DB_PASSWORD = process.env.DB_PASSWORD || 'password'; // Password for MySQL/Postgres
const DB_NAME = process.env.DB_NAME || 'database_name'; // Database name for MySQL/Postgres
const DB_TABLE = process.env.DB_TABLE || 'meme'; // Default table name for queries

// Database libraries will be loaded conditionally
let sqlite3, mysql, PgClient;

// Main function to process memelang query and return results
async function memeQuery(memelangQuery) {
    try {
        // Translate memelang to SQL
        const sqlQuery = memeSQL(memelangQuery);
        // Call the appropriate database function based on DB_TYPE
        switch (DB_TYPE) {
            case 'sqlite3':
                return await memeSQLite3(sqlQuery);
            case 'mysql':
                return await memeMySQL(sqlQuery);
            case 'postgres':
                return await memePostgres(sqlQuery);
            default:
                throw new Error(`Unsupported database type: ${DB_TYPE}`);
        }
    } catch (error) {
        console.error('Error:', error.message);
        return `Error: ${error.message}`;
    }
}

// Generate SQL from the memelang query
function memeSQL(query) {
    query = query.replace(/\s+/g, ''); // Remove all whitespace

    // Split the query by | for OR conditions
    const orClauses = query.split('|');
    const sqlClauses = orClauses.map((clause) => memeClause(clause.trim()));

    // Join OR-separated clauses using UNION
    return sqlClauses.join(' UNION ');
}

function memeClause(clause) {
    // Check for & in the clause for AND conditions
    if (clause.includes('&')) {
        return `SELECT m.* FROM ${DB_TABLE} m ` + memeJunction(clause);
    } else {
        // Handle simple clause with no &
        const result = memeParse(clause);
        return `SELECT * FROM ${DB_TABLE} WHERE ${result.clause}`;
    }
}

function memeJunction(query) {
    const filters = [];
    const havingConditions = [];

    // Split the query by '&' and process each clause
    const clauses = query.split('&');
    for (let clause of clauses) {
        clause = clause.trim();
        const isAndNotCondition = clause.startsWith('!');
        if (isAndNotCondition) clause = clause.slice(1);

        const result = memeParse(clause);
        havingConditions.push(
            `SUM(CASE WHEN ${result.clause} THEN 1 ELSE 0 END) ${isAndNotCondition ? '= 0' : '> 0'}`
        );

        if (result.filter) {
            filters.push(`(${result.filter})`);
        }
    }

    return `JOIN (SELECT aid FROM ${DB_TABLE} GROUP BY aid HAVING ${havingConditions.join(
        ' AND '
    )}) AS aids ON m.aid = aids.aid` + (filters.length ? ` WHERE ${memeFilterGroup(filters).join(' OR ')}` : '');
}

function memeParse(query) {
    const pattern = /^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$/;
    const matches = query.match(pattern);
    if (matches) {
        let [_, aid, rid, bid, operator, qnt] = matches;
        operator = operator ? operator.replace('#=', '=') : '=';
        qnt = qnt || '1'; // Default qnt to 1 if not specified

        const conditions = [];
        if (aid) conditions.push(`aid='${aid}'`);
        if (rid) conditions.push(`rid='${rid}'`);
        if (bid) conditions.push(`bid='${bid}'`);
        conditions.push(`qnt${operator}${qnt}`);

        const filterConditions = [];
        if (rid) filterConditions.push(`rid='${rid}'`);
        if (bid) filterConditions.push(`bid='${bid}'`);
        return { clause: conditions.join(' AND '), filter: filterConditions.join(' AND ') };
    } else {
        throw new Error(`Invalid memelang format: ${query}`);
    }
}

function memeFilterGroup(filters) {
    const ridValues = [];
    const bidValues = [];
    const complexFilters = [];

    for (const filter of filters) {
        if (/^\(rid='([A-Za-z0-9]+)'\)$/.test(filter)) {
            ridValues.push(filter.match(/rid='([A-Za-z0-9]+)'/)[1]);
        } else if (/^\(bid='([A-Za-z0-9]+)'\)$/.test(filter)) {
            bidValues.push(filter.match(/bid='([A-Za-z0-9]+)'/)[1]);
        } else {
            complexFilters.push(filter);
        }
    }

    const grouped = [];
    if (ridValues.length) grouped.push(`m.rid IN ('${ridValues.join("','")}')`);
    if (bidValues.length) grouped.push(`m.bid IN ('${bidValues.join("','")}')`);

    return grouped.concat(complexFilters);
}

// SQLite3 database query function
async function memeSQLite3(sqlQuery) {
    if (!sqlite3) sqlite3 = require('sqlite3').verbose();
    const db = new sqlite3.Database(DB_PATH);
    return new Promise((resolve, reject) => {
        db.all(sqlQuery, (err, rows) => {
            if (err) reject(err);
            else resolve(rows.map((row) => ({ ...row })));
            db.close();
        });
    });
}

// MySQL database query function
async function memeMySQL(sqlQuery) {
    if (!mysql) mysql = require('mysql2/promise');
    const connection = await mysql.createConnection({
        host: DB_HOST,
        user: DB_USER,
        password: DB_PASSWORD,
        database: DB_NAME,
    });
    const [rows] = await connection.execute(sqlQuery);
    await connection.end();
    return rows;
}

// PostgreSQL database query function
async function memePostgres(sqlQuery) {
    if (!PgClient) PgClient = require('pg').Client;
    const client = new PgClient({
        host: DB_HOST,
        user: DB_USER,
        password: DB_PASSWORD,
        database: DB_NAME,
    });
    await client.connect();
    const res = await client.query(sqlQuery);
    await client.end();
    return res.rows;
}

// Format query results as memelang
function memeOut(results) {
    return results
        .map((row) => `${row.aid}.${row.rid}:${row.bid}=${row.qnt}`)
        .join(';\n');
}

// Test function with various example queries
async function memeTest() {
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

    for (const query of queries) {
        console.log(`Memelang Query: ${query}`);
        const sqlQuery = memeSQL(query);
        console.log(`Generated SQL: ${sqlQuery}`);
        const results = await memeQuery(query);
        console.log("Results in Memelang Format:\n" + memeOut(results) + "\n");
    }
}

// Run the test function
memeTest();
