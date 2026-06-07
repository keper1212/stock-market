-- Give all load-test users enough shares for SELL load tests.
-- This does not change account cash.

INSERT INTO user_stocks (
    user_id,
    stock_code,
    quantity,
    locked_quantity,
    average_cost,
    created_at,
    updated_at
)
SELECT
    selected_users.user_id,
    s.stock_code,
    1000000,
    0,
    s.base_price,
    now(),
    now()
FROM (
    SELECT user_id
    FROM users
    WHERE email LIKE 'loadtest%example.com'
) selected_users
CROSS JOIN stocks s
WHERE s.stock_code IN ('HYU-MOTOR', 'SAM-ELEC', 'SK-HYNIX')
ON CONFLICT (user_id, stock_code)
DO UPDATE SET
    quantity = EXCLUDED.quantity,
    locked_quantity = 0,
    average_cost = EXCLUDED.average_cost,
    updated_at = now();

SELECT
    stock_code,
    COUNT(*) AS holders,
    SUM(quantity) AS total_quantity,
    SUM(locked_quantity) AS total_locked_quantity
FROM user_stocks
WHERE stock_code IN ('HYU-MOTOR', 'SAM-ELEC', 'SK-HYNIX')
GROUP BY stock_code
ORDER BY stock_code;
