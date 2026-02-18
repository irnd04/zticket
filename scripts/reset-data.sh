#!/bin/bash
docker exec zticket-mysql mysql -uroot -proot -e "TRUNCATE TABLE zticket.tickets;" 2>/dev/null
docker exec zticket-redis redis-cli FLUSHALL
echo "데이터 초기화 완료"
