SET 'auto.offset.reset' = 'earliest';

CREATE TABLE stockapp_users (
    userid STRING PRIMARY KEY,
    registertime BIGINT,
    regionid STRING,
    gender STRING,
    interests ARRAY<STRING>,
    contactinfo MAP<STRING, STRING>
) WITH (
    KAFKA_TOPIC = 'stockapp.users',
    VALUE_FORMAT = 'AVRO'
);

CREATE STREAM stockapp_trades
WITH (
    KAFKA_TOPIC = 'stockapp.trades',
    VALUE_FORMAT = 'AVRO'
);

CREATE STREAM stockapp_trades_transformed AS
    SELECT
        CAST(price AS DECIMAL(7,2)) * quantity / 100 AS dollar_amount,
        MASK(account, '*', '*', NULL, NULL) AS account_masked,
        symbol,
        userid
    FROM stockapp_trades
    WHERE symbol LIKE '%T'
    EMIT CHANGES;

CREATE STREAM stockapp_trades_transformed_enriched AS
    SELECT s.userid, s.dollar_amount, s.symbol,
           u.regionid, u.interests, u.contactinfo
    FROM stockapp_trades_transformed s
    LEFT JOIN stockapp_users u
        ON s.userid = u.userid
    EMIT CHANGES;

CREATE TABLE stockapp_dollars_by_zip_5_min
    WITH (
        KAFKA_TOPIC = 'stockapp.dollarsbyzip',
        PARTITIONS = 1,
        VALUE_FORMAT = 'JSON'
    ) AS
    SELECT
        contactinfo['zipcode'],
        as_value(contactinfo['zipcode']) AS zipcode,
        SUM(dollar_amount) AS total_dollars
    FROM stockapp_trades_transformed_enriched
    WINDOW TUMBLING (SIZE 5 MINUTES)
    GROUP BY contactinfo['zipcode']
    EMIT CHANGES;
