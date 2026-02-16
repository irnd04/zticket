-- KEYS = [seat:{seatNumber}]
-- ARGV[1] = "held:{uuid}", ARGV[2] = "paid:{uuid}"
-- 현재 값이 held:{uuid}인 경우에만 paid:{uuid}로 전환하고 TTL 제거
for i = 1, #KEYS do
    local current = redis.call('GET', KEYS[i])
    if current ~= ARGV[1] then
        return i
    end
end
for i = 1, #KEYS do
    redis.call('SET', KEYS[i], ARGV[2])
    redis.call('PERSIST', KEYS[i])
end
return 0
