-- KEYS[1] = seat:{seatNumber}
-- ARGV[1] = "held:{uuid}", ARGV[2] = "paid:{uuid}"
-- 현재 값이 held:{uuid}인 경우에만 paid:{uuid}로 전환하고 TTL 제거
local current = redis.call('GET', KEYS[1])
if current ~= ARGV[1] then
    return 1
end
redis.call('SET', KEYS[1], ARGV[2])
return 0
