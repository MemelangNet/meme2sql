/* 
ant.admire:amsterdam=0
*/
SELECT * FROM mem WHERE (aid='ant' AND rid='admire' AND bid='amsterdam' AND qnt=0);

/*
ant.believe:cairo
*/
SELECT * FROM mem WHERE (aid='ant' AND rid='believe' AND bid='cairo' AND qnt=1);

/*
ant.believe
*/
SELECT * FROM mem WHERE (aid='ant' AND rid='believe' AND qnt=1);

/*
ant
*/
SELECT * FROM mem WHERE (aid='ant' AND qnt=1);

/*
.admire
*/
SELECT * FROM mem WHERE (rid='admire' AND qnt=1);

/*
:amsterdam
*/
SELECT * FROM mem WHERE (bid='amsterdam' AND qnt=1);

/*
.letter #= 2
*/
SELECT * FROM mem WHERE (rid='letter' AND qnt=2);

/*
.letter > 2
*/
SELECT * FROM mem WHERE (rid='letter' AND qnt > 2);

/*
.letter >= 2
*/
SELECT * FROM mem WHERE (rid='letter' AND qnt >= 2);

/*
.letter < 2
*/
SELECT * FROM mem WHERE (rid='letter' AND qnt < 2);

/*
.letter <= 2
*/
SELECT * FROM mem WHERE (rid='letter' AND qnt <= 2);


/* OR JUNCTIONS */

/*
ant | :cairo
*/
SELECT * FROM mem WHERE (aid='ant' AND qnt=1) OR (bid='cairo' AND qnt=1);

/*
.admire | .believe
*/
SELECT * FROM mem WHERE (rid='admire' AND qnt=1) OR (rid='believe' AND qnt=1);

/*
.admire | .believe | .letter > 2
*/
SELECT * FROM mem WHERE (rid='admire' AND qnt=1) OR (rid='believe' AND qnt=1) OR (rid='letter' AND qnt > 2);


/* AND JUNCTIONS */

/*
.discover & .explore
*/
SELECT * FROM mem WHERE (
	aid IN (
		SELECT aid FROM mem WHERE (rid='discover' AND qnt=1)
		INTERSECT
		SELECT aid FROM mem WHERE (rid='explore' AND qnt=1)
	) AND (
		(rid='discover' AND qnt=1) OR
		(rid='explore' AND qnt=1)
	)
);


/*
.admire & .believe & .letter < 5
*/
SELECT * FROM mem WHERE (
	aid IN (
		SELECT aid FROM mem WHERE (rid='admire' AND qnt=1)
		INTERSECT
		SELECT aid FROM mem WHERE (rid='believe' AND qnt=1)
		INTERSECT
		SELECT aid FROM mem WHERE (rid='letter' AND qnt<5)
	) AND (
		(rid='admire' AND qnt=1) OR
		(rid='believe' AND qnt=1) OR
		(rid='letter' AND qnt<5)
	)
);


/* AND-OR */

/*
.discover & .explore:amsterdam | :cairo
*/
SELECT * FROM mem WHERE (
	(
		aid IN (
			SELECT aid FROM mem WHERE (rid='discover' AND qnt=1)
			INTERSECT
			SELECT aid FROM mem WHERE (rid='explore' AND bid='amsterdam' AND qnt=1)
		) AND (
			(rid='discover' AND qnt=1) OR
			(rid='explore' AND bid='amsterdam' AND qnt=1)
		)
	)
	OR
	(bid='cairo' AND qnt=1)
);


/*
.admire & .explore & :amsterdam | .letter:ord < 2 | :cairo
*/
SELECT * FROM mem WHERE (
	(
		aid IN (
			SELECT aid FROM mem WHERE (rid='admire' AND qnt=1)
			INTERSECT
			SELECT aid FROM mem WHERE (rid='explore' AND qnt=1)
			INTERSECT
			SELECT aid FROM mem WHERE (bid='amsterdam' AND qnt=1)
		) AND (
			(rid='admire' AND qnt=1) OR
			(rid='explore' AND qnt=1) OR
			(bid='amsterdam' AND qnt=1)
		)
	)
	OR
	(rid='letter' AND bid='ord' AND qnt<2)
	OR
	(bid='cairo' AND qnt=1)
);