# DEP#05-Redis服务

## 任务目标

提供缓存、限流、分布式锁和短期任务状态存储能力。

## 本次实现

- 在 `docker-compose.yml` 中添加 Redis 7.2 Alpine 服务。
- 默认密码为 `lexiflow`。
- 在 `application-dev.yml` 中配置 Redis 连接。

## 验证方式

```bash
docker compose up -d redis
```

后续业务模块实现 RedisTemplate、限流和锁能力。

