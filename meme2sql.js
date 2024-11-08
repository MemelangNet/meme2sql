// Database configuration constants
const DB_TYPE = 'sqlite3';         // Options: 'sqlite3', 'mysql', 'postgres'
const DB_PATH = 'data.sqlite';     // Path for SQLite3
const DB_HOST = 'localhost';       // Host for MySQL/Postgres
const DB_USER = 'username';        // Username for MySQL/Postgres
const DB_PASSWORD = 'password';    // Password for MySQL/Postgres
const DB_NAME = 'database_name';   // Database name for MySQL/Postgres
const DB_TABLE = 'meme';           // Table name for queries

let dbLibrary;

// Conditionally load the required database library based on DB_TYPE
if (DB_TYPE === 'sqlite3') {
    const sqlite3 = require('sqlite3').verbose();
    dbLibrary = { executeQuery: memeSQLite3, client: sqlite3 };
} else if (DB_TYPE === 'mysql') {
    const mysql = require('mysql2/promise');
    dbLibrary = { executeQuery: memeMySQL, client: mysql };
} else if (DB_TYPE === 'postgres') {
    const { Client } = require('pg');
    dbLibrary = { executeQuery: memePostgres, client: Client };
} else {
    throw new Error(`Unsupported database type: ${DB_TYPE}`);
}

// Main function to process memelang query and return results
async function memeQuery(memelangQuery) {
    memelangQuery = memelangQuery.replace(/\s+/g, ''); // Remove all whitespace

    try {
        const sqlQuery = memeSQL(memelangQuery);
        return await dbLibrary.executeQuery(sqlQuery);
    } catch (error) {
        return [{ error: error.message }];
    }
}

// Function to handle AND, OR conditions and translate to SQL
function memeSQL(query) {
    if (query.includes('|')) {
        const clauses = query.split('|');
        const sqlClauses = clauses.map(clause => `SELECT m.* FROM ${DB_TABLE} m ${memeJunction(clause.trim())}`);
        return sqlClauses.join(' UNION ');
    }
    return `SELECT m.* FROM ${DB_TABLE} m ${memeJunction(query)}`;
}

// Handle single clause logic for both AND (&) conditions and basic WHERE filtering
function memeJunction(query) {
    const filters = [];

    if (query.includes('&')) {
        const clauses = query.split('&');
        const havingConditions = [];

        clauses.forEach(clause => {
            const result = memeParse(clause.trim());
            havingConditions.push(`SUM(CASE WHEN ${result.clause} THEN 1 ELSE 0 END) > 0`);
            if (result.filter) {
                filters.push(`(${result.filter})`);
            }
        });

        return `JOIN (SELECT aid FROM ${DB_TABLE} GROUP BY aid HAVING ${havingConditions.join(' AND ')}) AS aids ON m.aid = aids.aid` +
               (filters.length ? ` WHERE ${memeFilterGroup(filters).join(' OR ')}` : '');
    }

    const result = memeParse(query);
    if (result.filter) {
        filters.push(`(${result.filter})`);
    }
    return `WHERE ${result.clause}` + (filters.length ? ` AND ${memeFilterGroup(filters).join(' OR ')}` : '');
}

// Function to parse individual components of the memelang query
function memeParse(query) {
    const pattern = /^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$/;
    const matches = query.match(pattern);

    if (matches) {
        const [ , aid, rid, bid, operatorRaw, qntRaw ] = matches;
        const operator = operatorRaw === '#=' ? '=' : operatorRaw || '=';
        const qnt = qntRaw || '1';

        const conditions = [];
        if (aid) conditions.push(`aid='${aid}'`);
        if (rid) conditions.push(`rid='${rid}'`);
        if (bid) conditions.push(`bid='${bid}'`);
        conditions.push(`qnt${operator}${qnt}`);

        const filterConditions = [];
        if (rid) filterConditions.push(`rid='${rid}'`);
        if (bid) filterConditions.push(`bid='${bid}'`);

        return {
            clause: `(${conditions.join(' AND ')})`,
            filter: filterConditions.join(' AND ')
        };
    } else {
        throw new Error(`Invalid memelang format: ${query}`);
    }
}

// Group filters to reduce SQL complexity
function memeFilterGroup(filters) {
    const ridValues = [];
    const bidValues = [];
    const complexFilters = [];

    filters.forEach(filter => {
        const ridMatch = filter.match(/^\(rid='([A-Za-z0-9]+)'\)$/);
        const bidMatch = filter.match(/^\(bid='([A-Za-z0-9]+)'\)$/);

        if (ridMatch) {
            ridValues.push(ridMatch[1]);
        } else if (bidMatch) {
            bidValues.push(bidMatch[1]);
        } else {
            complexFilters.push(filter);
        }
    });

    const grouped = [];
    if (ridValues.length) {
        grouped.push(`m.rid IN ('${ridValues.join("','")}')`);
    }
    if (bidValues.length) {
        grouped.push(`m.bid IN ('${bidValues.join("','")}')`);
    }

    return grouped.concat(complexFilters);
}

// SQLite3 database query function
async function memeSQLite3(sqlQuery) {
    return new Promise((resolve, reject) => {
        const db = new dbLibrary.client.Database(DB_PATH, sqlite3.OPEN_READONLY, (err) => {
            if (err) return reject(err);
        });

        db.all(sqlQuery, [], (err, rows) => {
            if (err) {
                reject(err);
            } else {
                resolve(rows);
            }
        });

        db.close();
    });
}

// MySQL database query function
async function memeMySQL(sqlQuery) {
    const connection = await dbLibrary.client.createConnection({
        host: DB_HOST,
        user: DB_USER,
        password: DB_PASSWORD,
        database: DB_NAME
    });

    const [rows] = await connection.execute(sqlQuery);
    await connection.end();
    return rows;
}

// PostgreSQL database query function
async function memePostgres(sqlQuery) {
    const client = new dbLibrary.client({
        host: DB_HOST,
        user: DB_USER,
        password: DB_PASSWORD,
        database: DB_NAME
    });
    await client.connect();
    const res = await client.query(sqlQuery);
    await client.end();
    return res.rows;
}

// Format query results as memelang
function memeOut(results) {
    return results.map(row => `${row.aid}.${row.rid}:${row.bid}=${row.qnt}`).join(';\n');
}

// Example usage
(async () => {
    const memelangQuery = ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok";
    const results = await memeQuery(memelangQuery);
    console.log(memeOut(results));
})();
