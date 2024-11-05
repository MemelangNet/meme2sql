import re

def meme2sql(memelang):
    memelang = memelang.strip()

    # Base case: Handle the atomic "a.r:b=q" pattern
    match = re.match(r'^(\w*)\.?(\w*):?(\w*)?([<>=#]*)?(\d*)$', memelang)
    if match:
        aid = match.group(1) or None
        rid = match.group(2) or None
        bid = match.group(3) or None
        operator = match.group(4).replace('#=', '=') if match.group(4) else '='
        qnt = match.group(5) or '1'

        conditions = []
        if aid:
            conditions.append(f"aid='{aid}'")
        if rid:
            conditions.append(f"rid='{rid}'")
        if bid:
            conditions.append(f"bid='{bid}'")
        conditions.append(f"qnt {operator} {qnt}")

        return f"SELECT * FROM mem WHERE ({' AND '.join(conditions)})"

    # Recursive case: Handle OR (|) and AND (&) junctions
    if '|' in memelang:
        # Split by '|' for OR conditions
        parts = memelang.split('|')
        subqueries = [meme2sql(part) for part in parts]
        combined = ' OR '.join(f"({re.sub(r'^SELECT \* FROM mem WHERE ', '', query)})" for query in subqueries)
        return f"SELECT * FROM mem WHERE ({combined})"

    elif '&' in memelang:
        # Split by '&' for AND conditions
        parts = memelang.split('&')
        subqueries = [meme2sql(part) for part in parts]

        # Extract aid conditions using INTERSECT for AND
        intersect_conditions = ' INTERSECT '.join(
            f"SELECT aid FROM mem WHERE {re.sub(r'^SELECT \* FROM mem WHERE ', '', query)}" for query in subqueries
        )
        combined = ' OR '.join(re.sub(r'^SELECT \* FROM mem WHERE ', '', query) for query in subqueries)

        return f"SELECT * FROM mem WHERE (\n    aid IN ({intersect_conditions})\n    AND ({combined})\n)"

    return ""

# Sample usage
queries = [
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
]

for query in queries:
    print(f"/* {query} */")
    print(meme2sql(query))
    print()
