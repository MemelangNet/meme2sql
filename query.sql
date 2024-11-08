/* 
ant.admire:amsterdam #= 0
*/
SELECT * FROM meme WHERE (aid='ant' AND rid='admire' AND bid='amsterdam' AND qnt=0);

/*
ant.believe:cairo
*/
SELECT * FROM meme WHERE (aid='ant' AND rid='believe' AND bid='cairo' AND qnt=1);

/*
ant.believe
*/
SELECT * FROM meme WHERE (aid='ant' AND rid='believe' AND qnt=1);

/*
ant
*/
SELECT * FROM meme WHERE (aid='ant' AND qnt=1);

/*
.admire
*/
SELECT * FROM meme WHERE (rid='admire' AND qnt=1);

/*
:amsterdam
*/
SELECT * FROM meme WHERE (bid='amsterdam' AND qnt=1);

/*
.letter #= 2
*/
SELECT * FROM meme WHERE (rid='letter' AND qnt=2);

/*
.letter > 1.9
*/
SELECT * FROM meme WHERE (rid='letter' AND qnt > 1.9);

/*
.letter >= 2.1
*/
SELECT * FROM meme WHERE (rid='letter' AND qnt >= 2.1);

/*
.letter < 2.2
*/
SELECT * FROM meme WHERE (rid='letter' AND qnt < 2.2);

/*
.letter <= 2.3
*/
SELECT * FROM meme WHERE (rid='letter' AND qnt <= 2.3);


/* OR JUNCTIONS */

/*
ant | :cairo
*/
SELECT * FROM meme WHERE (aid='ant' AND qnt=1) OR (bid='cairo' AND qnt=1);

/*
.admire | .believe
*/
SELECT * FROM meme WHERE (rid='admire' AND qnt=1) OR (rid='believe' AND qnt=1);

/*
.admire | .believe | .letter > 2
*/
SELECT * FROM meme WHERE (rid='admire' AND qnt=1) OR (rid='believe' AND qnt=1) OR (rid='letter' AND qnt > 2);


/* AND JUNCTIONS */

/*
.discover & .explore
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='discover' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='explore' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'discover') OR 
    (m.rid = 'explore')
);

/*
.create & :dubai
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='create' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='dubai' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'create') OR 
    (m.bid = 'dubai')
);


/*
.admire & .believe & .letter:ord < 5
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='believe' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='letter' AND bid='ord' AND qnt<5) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'believe') OR 
    (m.rid = 'letter' AND m.bid = 'ord')
);



/* ANDNOT JUNCTIONS */

/*
.discover &! .explore
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='discover' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='explore' AND qnt=1) THEN 1 ELSE 0 END) = 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'discover') OR 
    (m.rid = 'explore')
);

/*
.create &! :dubai
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='create' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='dubai' AND qnt=1) THEN 1 ELSE 0 END) = 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'create') OR 
    (m.bid = 'dubai')
);


/*
.admire &! .believe & .letter:ord < 5
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='believe' AND qnt=1) THEN 1 ELSE 0 END) = 0
        AND SUM(CASE WHEN (rid='letter' AND bid='ord' AND qnt<5) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'believe') OR 
    (m.rid = 'letter' AND m.bid = 'ord')
);

/*
.admire &! .believe &! .letter:ord < 5
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='believe' AND qnt=1) THEN 1 ELSE 0 END) = 0
        AND SUM(CASE WHEN (rid='letter' AND bid='ord' AND qnt<5) THEN 1 ELSE 0 END) = 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'believe') OR 
    (m.rid = 'letter' AND m.bid = 'ord')
);

/*
.admire &! .believe &! .letter:ord < 5 & .explore
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='believe' AND qnt=1) THEN 1 ELSE 0 END) = 0
        AND SUM(CASE WHEN (rid='letter' AND bid='ord' AND qnt<5) THEN 1 ELSE 0 END) = 0
        AND SUM(CASE WHEN (rid='explore' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'believe') OR 
    (m.rid = 'letter' AND m.bid = 'ord') OR 
    (m.rid = 'explore')
);


/* AND-OR */

/*
.discover & .explore:amsterdam | :cairo
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='discover' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='explore' AND bid='amsterdam' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'discover') OR 
    (m.rid='explore' AND m.bid = 'amsterdam')
)
UNION
SELECT * FROM meme WHERE (bid='cairo' AND qnt=1);


/*
.admire & .explore & :amsterdam | .letter:ord < 2 | :bangkok
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='explore' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='amsterdam' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'explore') OR 
    (m.bid = 'amsterdam')
)
UNION
SELECT * FROM meme WHERE (rid='letter' AND bid='ord' AND qnt<2)
UNION
SELECT * FROM meme WHERE (bid='bangkok' AND qnt=1);


/*
.admire & .explore & :amsterdam | .letter:ord < 2 & :bangkok
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='explore' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='amsterdam' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'explore') OR 
    (m.bid = 'amsterdam')
)
UNION
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='letter' AND bid='ord' AND qnt<2) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='bangkok' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'letter' AND m.bid='ord') OR 
    (m.bid = 'bangkok')
);


/*
.admire & .explore &! :bangkok | .letter:ord < 3 & :amsterdam
*/
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='admire' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (rid='explore' AND qnt=1) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='bangkok' AND qnt=1) THEN 1 ELSE 0 END) = 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'admire') OR 
    (m.rid = 'explore') OR 
    (m.bid = 'bangkok')
)
UNION
SELECT m.* FROM meme m JOIN (
    SELECT aid FROM meme GROUP BY aid HAVING 
        SUM(CASE WHEN (rid='letter' AND bid='ord' AND qnt<3) THEN 1 ELSE 0 END) > 0
        AND SUM(CASE WHEN (bid='amsterdam' AND qnt=1) THEN 1 ELSE 0 END) > 0
) AS aids ON m.aid = aids.aid
WHERE (
    (m.rid = 'letter' AND m.bid='ord') OR 
    (m.bid = 'amsterdam')
);