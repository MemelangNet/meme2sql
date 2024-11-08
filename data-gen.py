import os
import csv

COUNT = 5

As = [
    'ant', 'bat', 'cat', 'deer', 'eagle', 'fish', 'goat', 'hawk', 'ibex', 'jackal',
    'kangaroo', 'lion', 'mink', 'newt', 'owl', 'pig', 'quail', 'rat', 'seal', 'tiger',
    'urchin', 'viper', 'wolf', 'xerus', 'yak', 'zebra'
]

Rs = [
    'admire', 'believe', 'create', 'discover', 'explore', 'feel', 'gather', 'help', 
    'imagine', 'join', 'know', 'learn', 'measure', 'notice', 'observe', 'perform', 
    'question', 'reflect', 'support', 'transform', 'understand', 'visualize', 'wonder', 
    'xerox', 'yield', 'zoom'
]

Bs = [
    'amsterdam', 'bangkok', 'cairo', 'dubai', 'edinburgh', 'florence', 'geneva', 
    'helsinki', 'istanbul', 'jakarta', 'kyoto', 'lisbon', 'mumbai', 'nairobi', 
    'oslo', 'prague', 'quito', 'rome', 'sydney', 'tokyo', 'ulaanbaatar', 'vienna', 
    'warsaw', 'xian', 'yokohama', 'zurich'
]

# Remove old files if they exist
for filename in ['data.csv', 'data.mem', 'data.sql']:
    if os.path.exists(filename):
        os.remove(filename)

# Open files for writing
with open('data.csv', 'w', newline='') as csv_file, \
     open('data.mem', 'w') as mem_file, \
     open('data.sql', 'w') as sql_file:

    csv_writer = csv.writer(csv_file, delimiter='\t')
    mem_writer = mem_file.write
    sql_writer = sql_file.write

    # SQL setup
    sql_writer("DROP TABLE IF EXISTS mem;\n")
    sql_writer("CREATE TABLE mem (aid varchar(255), rid varchar(255), bid varchar(255), qnt DECIMAL(20,6));\n")
    sql_writer("CREATE UNIQUE INDEX arb ON mem (aid, rid, bid);\n")
    sql_writer("CREATE INDEX rid ON mem (rid);\n")
    sql_writer("CREATE INDEX bid ON mem (bid);\n")

    last_animal = None
    for a in range(COUNT):
        animal = As[a]
        aq = a + 1

        # Write connections between animals
        if last_animal:
            csv_writer.writerow([last_animal, 'next', animal, 1])
            mem_writer(f"{last_animal}.next:{animal}=1;\n")
            sql_writer(f"INSERT INTO mem VALUES ('{last_animal}', 'next', '{animal}', 1);\n")

        last_animal = animal

        # Write animal letter and ordinal
        csv_writer.writerow([animal, 'letter', 'ord', aq])
        mem_writer(f"{animal}.letter:ord={aq};\n")
        sql_writer(f"INSERT INTO mem VALUES ('{animal}', 'letter', 'ord', {aq});\n")

        # Create relations between animals, verbs, and cities
        for r in range(COUNT):
            verb = Rs[r]
            for b in range(COUNT):
                city = Bs[b]
                q = 0 if (a == r or b == r) else 1
                csv_writer.writerow([animal, verb, city, q])
                mem_writer(f"{animal}.{verb}:{city}={q};\n")
                sql_writer(f"INSERT INTO mem VALUES ('{animal}', '{verb}', '{city}', {q});\n")
