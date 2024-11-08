import os
import re  # Regular expression library for parsing

# Database configuration constants
DB_TYPE = os.getenv('DB_TYPE', 'sqlite3')     # Options: 'sqlite3', 'mysql', 'postgres'
DB_PATH = os.getenv('DB_PATH', 'data.sqlite') # Path for SQLite3
DB_HOST = os.getenv('DB_HOST', 'localhost')   # Host for MySQL/Postgres
DB_USER = os.getenv('DB_USER', 'username')    # Username for MySQL/Postgres
DB_PASSWORD = os.getenv('DB_PASSWORD', 'password') # Password for MySQL/Postgres
DB_NAME = os.getenv('DB_NAME', 'database_name') # Database name for MySQL/Postgres
DB_TABLE = os.getenv('DB_TABLE', 'meme')       # Default table name for queries

# Conditional imports for database libraries
if DB_TYPE == 'sqlite3':
    import sqlite3
elif DB_TYPE == 'postgres':
    import psycopg2
    from psycopg2.extras import RealDictCursor
elif DB_TYPE == 'mysql':
    import mysql.connector

def meme_query(memelang_query):
    try:
        # Translate memelang to SQL
        sql_query = meme_sql(memelang_query)
        # Call the appropriate database function based on DB_TYPE
        if DB_TYPE == 'sqlite3':
            return meme_sqlite3(sql_query)
        elif DB_TYPE == 'mysql':
            return meme_mysql(sql_query)
        elif DB_TYPE == 'postgres':
            return meme_postgres(sql_query)
        else:
            raise Exception(f"Unsupported database type: {DB_TYPE}")
    except Exception as e:
        return f"Error: {e}"

def meme_sql(query):
    # Remove all whitespace from the input
    query = ''.join(query.split())

    # Split the query by | for OR conditions
    or_clauses = query.split('|')
    sql_clauses = [meme_clause(clause.strip()) for clause in or_clauses]

    # Join the OR-separated clauses using UNION for the final SQL
    return " UNION ".join(sql_clauses)

def meme_clause(clause):
    # Check for & in the clause for AND conditions
    if '&' in clause:
        return f"SELECT m.* FROM {DB_TABLE} m " + meme_junction(clause)
    else:
        # Handle simple clause with no &
        result = meme_parse(clause)
        return f"SELECT * FROM {DB_TABLE} WHERE {result['clause']}"

def meme_junction(query):
    filters = []
    having_conditions = []

    # Split the query by '&' and process each clause
    clauses = query.split('&')
    for clause in clauses:
        clause = clause.strip()
        is_and_not_condition = clause.startswith('!')
        clause = clause[1:] if is_and_not_condition else clause

        result = meme_parse(clause)
        having_conditions.append(f"SUM(CASE WHEN {result['clause']} THEN 1 ELSE 0 END) {'= 0' if is_and_not_condition else '> 0'}")

        if result['filter']:
            filters.append(f"({result['filter']})")

    return f"JOIN (SELECT aid FROM {DB_TABLE} GROUP BY aid HAVING " + " AND ".join(having_conditions) + f") AS aids ON m.aid = aids.aid" + (f" WHERE {' OR '.join(meme_filter_group(filters))}" if filters else "")

def meme_parse(query):
    # Default qnt to 1 if not specified
    pattern = r'^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$'
    matches = re.match(pattern, query)
    if matches:
        aid, rid, bid, operator, qnt = matches.groups()
        operator = operator.replace('#=', '=')
        # Set qnt to 1 by default if it is not specified
        qnt = qnt if qnt else '1'
        operator = operator if operator else '='  # Default to '=' if no operator specified

        conditions = [f"aid='{aid}'"] if aid else []
        if rid: conditions.append(f"rid='{rid}'")
        if bid: conditions.append(f"bid='{bid}'")
        conditions.append(f"qnt{operator}{qnt}")

        filter_conditions = []
        if rid: filter_conditions.append(f"rid='{rid}'")
        if bid: filter_conditions.append(f"bid='{bid}'")
        return {"clause": " AND ".join(conditions), "filter": " AND ".join(filter_conditions)}
    else:
        raise Exception(f"Invalid memelang format: {query}")

def meme_filter_group(filters):
    rid_values, bid_values, complex_filters = [], [], []

    for filter in filters:
        if re.match(r"^\(rid='([A-Za-z0-9]+)'\)$", filter):
            rid_values.append(filter.split('=')[1].strip("'()"))
        elif re.match(r"^\(bid='([A-Za-z0-9]+)'\)$", filter):
            bid_values.append(filter.split('=')[1].strip("'()"))
        else:
            complex_filters.append(filter)

    grouped = []
    if rid_values:
        grouped.append(f"m.rid IN ('" + "','".join(rid_values) + "')")
    if bid_values:
        grouped.append(f"m.bid IN ('" + "','".join(bid_values) + "')")

    return grouped + complex_filters

def meme_sqlite3(sql_query):
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row  # Return rows as dictionaries
    cursor = connection.cursor()
    cursor.execute(sql_query)
    results = [dict(row) for row in cursor.fetchall()]
    connection.close()
    return results

def meme_mysql(sql_query):
    connection = mysql.connector.connect(
        host=DB_HOST,
        user=DB_USER,
        password=DB_PASSWORD,
        database=DB_NAME
    )
    cursor = connection.cursor(dictionary=True)  # Return rows as dictionaries
    cursor.execute(sql_query)
    results = cursor.fetchall()
    connection.close()
    return results

def meme_postgres(sql_query):
    connection = psycopg2.connect(
        host=DB_HOST,
        user=DB_USER,
        password=DB_PASSWORD,
        dbname=DB_NAME
    )
    cursor = connection.cursor(cursor_factory=RealDictCursor)  # Return rows as dictionaries
    cursor.execute(sql_query)
    results = cursor.fetchall()
    connection.close()
    return results

def meme_out(results):
    # Confirm all rows are dictionaries before attempting access
    for row in results:
        if not isinstance(row, dict):
            raise TypeError("Each row must be a dictionary")
    return ";\n".join([f"{row['aid']}.{row['rid']}:{row['bid']}={row['qnt']}" for row in results])

# Test function with various example queries
def meme_test():
    queries = [
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
    ]

    for query in queries:
        print(f"Memelang Query: {query}")
        sql_query = meme_sql(query)
        print(f"Generated SQL: {sql_query}")
        results = meme_query(query)
        print("Results in Memelang Format:\n" + meme_out(results) + "\n")

# Run the test function
meme_test()
