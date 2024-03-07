-- 1.parameter list
-- 1.1.coupon id
local voucherId = ARGV[1]
-- 1.2.user id
local userId = ARGV[2]
-- 1.3.order id
local orderId = ARGV[3]

-- 2.Data key
-- 2.1.Inventory key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.order key
local orderKey = 'seckill:order:' .. voucherId

-- 3.script function
-- 3.1.Determine whether inventory is sufficient, get stockKey
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2.Inventory shortage，return 1
    return 1
end
-- 3.2.Determine whether the user places an order SISMEMBER orderKey userId
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 3.3.exist，It indicates that the order is repeated，return 2
    return 2
end
-- 3.4.Deduct inventory incrby stockKey -1
redis.call('incrby', stockKey, -1)
-- 3.5.Place an order（save user）sadd orderKey userId
redis.call('sadd', orderKey, userId)

-- 3.6.Send a message to the stream queue， XADD stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0