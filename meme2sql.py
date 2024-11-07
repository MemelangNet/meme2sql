import re

def meme2sql(memelang):
    # Remove all whitespace from the input
    memelang = re.sub(r'\s+', '', memelang)
    filters = []  # Collect outer filter conditions for rid/bid
    sql_query = meme_clause(memelang, filters)

    # Process filters to group by rid and bid terms
    grouped_filter = group_filters(filters)

    # Include outer filters if any are present
    filter_condition = f" WHERE {' OR '.join(grouped_filter)}" if grouped_filter else ""
    return sql_query + filter_condition + ";"

def meme_parse(query):
    pattern = r'^([A-Za-z0-9]*)\.?([A-Za-z0-9]*):?([A-Za-z0-9]*)?([<>=#]*)?(-?\d*\.?\d*)$'
    matches = re.match(pattern, query)

    if matches:
        aid, rid, bid, operator, qnt = matches.groups()
        aid = aid or None
        rid = rid or None
        bid = bid or None
        operator = operator.replace('#=', '=') if operator else '='
        qnt = qnt if qnt else '1'

        conditions = []
        if aid:
            conditions.append(f"aid='{aid}'")
        if rid:
            conditions.append(f"rid='{rid}'")
        if bid:
            conditions.append(f"bid='{bid}'")
        conditions.append(f"qnt{operator}{qnt}")

        filter_conditions = []
        if rid:
            filter_conditions.append(f"rid='{rid}'")
        if bid:
            filter_conditions.append(f"bid='{bid}'")
        return {"clause": f"({' AND '.join(conditions)})", "filter": ' AND '.join(filter_conditions)}
    else:
        raise ValueError(f"Invalid memelang format: {query}")

def meme_clause(query, filters):
    # Handle OR (|) conditions by generating complete SELECT statements and combining them with UNION
    if '|' in query:
        clauses = query.split('|')
        sql_clauses = [
            f"SELECT m.* FROM mem m {meme_clause(clause.strip(), sub_filters := [])}"
            + (f" WHERE {' OR '.join(group_filters(sub_filters))}" if sub_filters else "")
            for clause in clauses
        ]
        return " UNION ".join(sql_clauses)

    # Handle AND (&) conditions by generating JOIN with GROUP BY and HAVING
    if '&' in query:
        clauses = query.split('&')
        having_conditions = []
        for clause in clauses:
            result = meme_parse(clause.strip())
            having_conditions.append(f"SUM(CASE WHEN {result['clause']} THEN 1 ELSE 0 END) > 0")
            if result['filter']:
                filters.append(f"({result['filter']})")
        return f"JOIN (SELECT aid FROM mem GROUP BY aid HAVING {' AND '.join(having_conditions)}) AS aids ON m.aid = aids.aid"

    # Direct parsing when no nested expressions or logical operators are present
    result = meme_parse(query)
    if result['filter']:
        filters.append(f"({result['filter']})")
    return f"WHERE {result['clause']}"

def group_filters(filters):
    rid_values = []
    bid_values = []
    complex_filters = []

    for filter_condition in filters:
        if rid_match := re.match(r"^\(rid='([A-Za-z0-9]+)'\)$", filter_condition):
            rid_values.append(rid_match.group(1))
        elif bid_match := re.match(r"^\(bid='([A-Za-z0-9]+)'\)$", filter_condition):
            bid_values.append(bid_match.group(1))
        else:
            complex_filters.append(filter_condition)  # Complex terms like (rid='letter' AND bid='ord')

    grouped = []
    if rid_values:
        grouped.append(f"m.rid IN ('{'',''.join(rid_values)}')")
    if bid_values:
        grouped.append(f"m.bid IN ('{'',''.join(bid_values)}')")

    return grouped + complex_filters

# Test function
def meme2sql_test():
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
        ".admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok",
        ".admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok"
    ]

    for query in queries:
        try:
            generated_sql = meme2sql(query)
            print(f"Query: {query}")
            print(f"Generated SQL: {generated_sql}\n")
        except ValueError as e:
            print(f"Error: {e}\n")

# Run tests
meme2sql_test()
