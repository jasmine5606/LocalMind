-- blindbox.lua
-- 盲盒秒杀：加权随机抽奖
-- ARGV[1]: blindBoxId
-- ARGV[2]: userId
-- ARGV[3]: orderId

local blindBoxId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

-- KEYS
local stockKey = 'blindbox:stock:' .. blindBoxId
local orderKey = 'blindbox:order:' .. blindBoxId
local prizeKey = 'blindbox:prizes:' .. blindBoxId

-- 1. Check blind box stock
local stock = tonumber(redis.call('get', stockKey) or '0')
if stock <= 0 then
    return {-1, ''}
end

-- 2. One per user per blind box
if redis.call('sismember', orderKey, userId) == 1 then
    return {-2, ''}
end

-- 3. Load prize pool: [voucherId1, remain1, weight1, voucherId2, remain2, weight2, ...]
local prizes = redis.call('lrange', prizeKey, 0, -1)
if prizes == nil or #prizes == 0 then
    return {-3, ''}
end

-- 4. Weighted random draw (up to 10 retries for sold-out prizes)
local prizeVoucherId = 0
for attempt = 1, 10 do
    local totalWeight = 0
    for i = 2, #prizes, 3 do
        local remain = tonumber(prizes[i])
        if remain > 0 then
            totalWeight = totalWeight + tonumber(prizes[i + 1])
        end
    end

    if totalWeight == 0 then
        return {-4, ''}
    end

    local rand = math.random(1, totalWeight)
    local cumulative = 0
    for i = 1, #prizes, 3 do
        local vid = prizes[i]
        local remain = tonumber(prizes[i + 1])
        local weight = tonumber(prizes[i + 2])
        if remain > 0 then
            cumulative = cumulative + weight
            if rand <= cumulative then
                local idx = (i - 1) / 3 + 1
                local remainIdx = i + 1
                local newRemain = remain - 1
                prizes[remainIdx] = tostring(newRemain)
                if newRemain <= 0 then
                    -- Mark as sold out in the list
                    prizes[remainIdx] = '0'
                end
                prizeVoucherId = tonumber(vid)
                break
            end
        end
    end

    if prizeVoucherId ~= 0 then
        break
    end
end

if prizeVoucherId == 0 then
    return {-4, ''}
end

-- 5. Deduct stock and record
redis.call('decr', stockKey)
redis.call('sadd', orderKey, userId)
redis.call('del', prizeKey)
for i = 1, #prizes do
    redis.call('rpush', prizeKey, prizes[i])
end

return {0, tostring(prizeVoucherId)}
