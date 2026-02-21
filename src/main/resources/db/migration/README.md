# Flyway Migrations

## Naming Convention

```
V{version}__{description}.sql
```

- `V1__initial_schema.sql` — 초기 스키마
- 버전은 순차 증가: `V2__`, `V3__`, ...
- 언더스코어 2개(`__`)로 버전과 설명 구분

## Notes

- `event_publication` 테이블은 Spring Modulith Event Publication Registry가 사용
- `serialized_event`는 JSON 저장을 위해 `VARCHAR(4000)`으로 설정 (Hibernate 기본값 255 대신)
- 적용된 마이그레이션은 절대 수정하지 말 것 (체크섬 불일치로 앱 시작 실패)
