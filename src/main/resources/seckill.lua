-- =============================================
-- Flash Sale (Seckill) Lua Script
-- =============================================
-- This script atomically handles flash sale operations to prevent 
-- overselling and duplicate orders in high-concurrency scenarios.
--
-- Return Values:
--   0: Success - Stock available, user hasn't ordered before
--   1: Failure - Insufficient stock
--   2: Failure - User has already placed an order for this voucher
-- =============================================

-- 1. Parameter validation and extraction
-- ARGV[1]: Voucher/Product ID for the flash sale item
local voucherId = ARGV[1]
-- ARGV[2]: User ID making the purchase request
local userId = ARGV[2]  
-- ARGV[3]: Pre-generated unique order ID
local orderId = ARGV[3]

-- 2. Redis key construction for data access
-- Stock counter key: tracks remaining inventory for this voucher
local stockKey = 'seckill:stock:' .. voucherId
-- Order tracking key: set of user IDs who have already ordered this voucher
local orderKey = 'seckill:order:' .. voucherId

-- 3. Core business logic execution
-- 3.1. Stock availability check
-- Get current stock count and verify it's positive
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- Stock is depleted or doesn't exist
    return 1
end

-- 3.2. Duplicate order prevention
-- Check if this user has already placed an order for this voucher
if(redis.call('sismember', orderKey, userId) == 1) then
    -- User already has an order for this voucher
    return 2
end

-- 3.3. Atomic inventory deduction
-- Decrement stock by 1 using atomic operation
redis.call('incrby', stockKey, -1)

-- 3.4. Order tracking registration  
-- Add user to the set of users who have ordered this voucher
redis.call('sadd', orderKey, userId)

-- 3.5. Asynchronous order processing
-- Send order details to Redis Stream for background processing
-- This enables decoupling of order validation from order creation
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

-- Return success code
return 0