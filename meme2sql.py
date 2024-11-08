import re
from typing import List, Dict, Any

# Database configuration constants
DB_TYPE = 'sqlite3'       # Options: 'sqlite3', 'mysql', 'postgres'
DB_PATH = 'data.sqlite'   # Path for SQLite3
DB_HOST = 'localhost'     # Host for MySQL/Postgres
DB_USER = 'username'      # Username for MySQL/Postgres
DB_PASSWORD = 'password'  # Password for MySQL/Postgres
DB_NAME = 'database_name' # Database name for MySQL/Postgres
DB_TABLE = 'meme'         # Table name for queries

# Conditionally import the required database library based on DB_TYPE
if DB_TYPE == 'sqlite3':
    import sqlite3
    execute_query = lambda sql_query: meme_sqlite3(sql_query)
elif DB_TYPE == 'mysql':
    import pymysql
    execute_query = lambda sql_query: meme_mysql(sql_query)
elif DB_TYPE == 'postgres':
    import psycopg2
    execute_query = lambda sql_query: meme_postgres(sql_query)
else:
    raise ValueError(f"Unsupported database type: {DB_TYPE}")

# Example usage
if __name__ == '__main__':
    memelang_query = ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok"
    results = meme_query(memelang_query)
    print(meme_out(results))

# Main function to process memelang query and return results
def meme_query(memelang_query: str) -> List[Dict[str, Any]]:
    # Remove all whitespace from the input
    memelang_query = re.sub(r'\s+', '', memelang_query)

    try:
        # Translate memelang to SQL
        sql_query = meme_sql(memelang_query)
        return execute_query(sql_query)
    except Exception as e:
        return [{"error": str(e)}]

# Function to handle AND, OR conditions and translate to SQL
def meme_sql(query: str) -> str:
    if '|' in query:
        clauses = query.split('|')
        sql_clauses = [f"SELECT m.* FROM {DB_TABLE} m {meme_junction(clause.strip())}" for clause in clauses]
        return " UNION ".join(sql_clauses)

    return f"SELECT m.* FROM {DB_TABLE} m {meme_junction(query.strip())}"

# Handle single clause logic for both AND (&) conditions and basic WHERE filtering
def meme_junction(query: str) -> str:
    filters = []

    if '&' in query:
        clauses = query.split('&')
        having_conditions = []
        for clause in clauses:
            result = meme_parse(clause.strip())
            having_conditions.append(f"SUM(CASE WHEN {result['clause']} THEN 1 ELSE 0 END) > 0")
            if result['filter']:
                filters.append(f"({result['filter']})")
        
        return f"JOIN (SELECT aid FROM {DB_TABLE} GROUP BY aid HAVING " + \
               f"{' AND '.join(having_conditions)}) AS aids ON m.aid = aids.aid" + \
               (f" WHERE {' OR '.join(meme_filter_group(filters))}" if filters else "")

    result = meme_parse(query)
    if result['filter']:
        filters.append(f"({result['filter']})")
    return f"WHERE {result['clause']}" + \
           (f" AND {' OR '.join(meme_filter_group(filters))}" if filters else "")

# Function to parse individual components of the memelang query
def meme_parse(query: str) -> Dict[str, str]:
    pattern = r'^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$'
    matches = re.match(pattern, query)
    if matches:
        aid, rid, bid, operator, qnt = matches.groups()
        operator = '=' if operator == '#=' else operator
        qnt = qnt if qnt != '' else '1'

        conditions = []
        if aid: conditions.append(f"aid='{aid}'")
        if rid: conditions.append(f"rid='{rid}'")
        if bid: conditions.append(f"bid='{bid}'")
        conditions.append(f"qnt{operator}{qnt}")

        filter_conditions = []
        if rid: filter_conditions.append(f"rid='{rid}'")
        if bid: filter_conditions.append(f"bid='{bid}'")

        return {
            "clause": f"({' AND '.join(conditions)})",
            "filter": ' AND '.join(filter_conditions)
        }
    else:
        raise ValueError(f"Invalid memelang format: {query}")

# Group filters to reduce SQL complexity
def meme_filter_group(filters: List[str]) -> List[str]:
    rid_values = []
    bid_values = []
    complex_filters = []

    for filter_expr in filters:
        if match := re.match(r"^\(rid='([A-Za-z0-9]+)'\)$", filter_expr):
            rid_values.append(match.group(1))
        elif match := re.match(r"^\(bid='([A-Za-z0-9]+)'\)$", filter_expr):
            bid_values.append(match.group(1))
        else:
            complex_filters.append(filter_expr)

    grouped = []
    if rid_values:
        grouped.append(f"m.rid IN ('{'',''.join(rid_values)}')")
    if bid_values:
        grouped.append(f"m.bid IN ('{'',''.join(bid_values)}')")

    return grouped + complex_filters

# SQLite3 database query function
def meme_sqlite3(sql_query: str) -> List[Dict[str, Any]]:
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute(sql_query)
    results = [dict(row) for row in cursor.fetchall()]
    conn.close()
    return results

# MySQL database query function
def meme_mysql(sql_query: str) -> List[Dict[str, Any]]:
    connection = pymysql.connect(host=DB_HOST, user=DB_USER, password=DB_PASSWORD, db=DB_NAME)
    cursor = connection.cursor(pymysql.cursors.DictCursor)
    cursor.execute(sql_query)
    results = cursor.fetchall()
    connection.close()
    return results

# PostgreSQL database query function
def meme_postgres(sql_query: str) -> List[Dict[str, Any]]:
    connection = psycopg2.connect(host=DB_HOST, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD)
    cursor = connection.cursor()
    cursor.execute(sql_query)
    results = [dict(row) for row in cursor.fetchall()]
    connection.close()
    return results

# Format query results as memelang
def meme_out(results: List[Dict[str, Any]]) -> str:
    memelang_output = [
        f"{row['aid']}.{row['rid']}:{row['bid']}={row['qnt']}" for row in results
    ]
    return ";\n".join(memelang_output)
