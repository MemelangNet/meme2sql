import re

def meme2sql(memelang):
    # Remove all whitespace from the input
    memelang = re.sub(r'\s+', '', memelang)
    filters = []  # Collect outer filter conditions for rid/bid
    sql_query = meme_clause(memelang, filters)

    # Include outer filters if any are present
    filter_condition = f" AND ({' OR '.join(filters)})" if filters else ""
    return f"SELECT * FROM mem WHERE ({sql_query}){filter_condition};"

def meme_parse(query):
    # Regular expression to parse A.R:B=Q format
    pattern = r'^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$'
    match = re.match(pattern, query)

    if match:
        aid, rid, bid, operator, qnt = match.groups()
        aid = aid or None
        rid = rid or None
        bid = bid or None
        operator = operator.replace('#=', '=') if operator else '='
        qnt = qnt if qnt != '' else '1'

        # Build conditions
        conditions = []
        if aid: conditions.append(f"aid='{aid}'")
        if rid: conditions.append(f"rid='{rid}'")
        if bid: conditions.append(f"bid='{bid}'")
        conditions.append(f"qnt{operator}{qnt}")

        # Prepare filter for outer conditions
        filter_cond = []
        if rid: filter_cond.append(f"rid='{rid}'")
        if bid: filter_cond.append(f"bid='{bid}'")
        return {"clause": f"({' AND '.join(conditions)})", "filter": ' AND '.join(filter_cond)}
    else:
        raise ValueError(f"Invalid memelang format: {query}")

def meme_clause(query, filters):
    if '|' in query:
        # Split by OR operator
        clauses = query.split('|')
        sql_clauses = [meme_clause(clause, filters) for clause in clauses]
        return " OR ".join(sql_clauses)

    if '&' in query:
        # Split by AND operator
        clauses = query.split('&')
        primary_conditions = []
        for clause in clauses:
            result = meme_parse(clause.strip())
            primary_conditions.append(f"aid IN (SELECT aid FROM mem WHERE {result['clause']})")
            if result['filter']:
                filters.append(f"({result['filter']})")
        return " AND ".join(primary_conditions)

    # Base case for single clause
    result = meme_parse(query)
    if result['filter']:
        filters.append(f"({result['filter']})")
    return result['clause']

# Test function
def meme2sql_test():
    # Define example memelang queries
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
        "ant | :cairo",
        ".admire | .believe",
        ".admire | .believe | .letter > 2",
        ".discover & .explore",
        ".admire & .believe & .letter:ord < 5",
        ".discover & .explore:amsterdam | :cairo",
        ".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok"
    ]

    # Run each query and display the generated SQL
    for query in queries:
        try:
            generated_sql = meme2sql(query)
            print(f"Query: {query}")
            print(f"Generated SQL: {generated_sql}\n")
        except ValueError as e:
            print(f"Error: {e}\n")

# Run the test
meme2sql_test()
