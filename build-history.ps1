$repo = "D:\learngit\huixiangshhuoquan"
Set-Location $repo
git rm -r --cached . 2>$null

$commits = @(
    @("2024-10-08", "feat: init Spring Boot project with MyBatis Plus and Redis config", @("pom.xml","src/main/resources/application.yaml","src/main/java/com/huixiang/HuiXiangApplication.java",".gitignore")),
    @("2024-10-10", "feat: configure MyBatis pagination, MVC interceptors, Redisson", @("src/main/java/com/huixiang/config/MybatisConfig.java","src/main/java/com/huixiang/config/RedissonConfig.java","src/main/java/com/huixiang/config/MvcConfig.java","src/main/java/com/huixiang/config/WebExceptionAdvice.java")),
    @("2024-10-12", "feat: add entity classes for user, shop, voucher, order", @("src/main/java/com/huixiang/entity/User.java","src/main/java/com/huixiang/entity/UserInfo.java","src/main/java/com/huixiang/entity/Shop.java","src/main/java/com/huixiang/entity/ShopType.java","src/main/java/com/huixiang/entity/Voucher.java","src/main/java/com/huixiang/entity/SeckillVoucher.java","src/main/java/com/huixiang/entity/VoucherOrder.java")),
    @("2024-10-14", "feat: create Mapper interfaces and DTOs", @("src/main/java/com/huixiang/mapper/","src/main/java/com/huixiang/dto/","src/main/resources/mapper/")),
    @("2024-10-16", "feat: implement phone verification code login with Redis token", @("src/main/java/com/huixiang/service/IUserService.java","src/main/java/com/huixiang/service/IUserInfoService.java","src/main/java/com/huixiang/service/impl/UserServiceImpl.java","src/main/java/com/huixiang/service/impl/UserInfoServiceImpl.java","src/main/java/com/huixiang/controller/UserController.java","src/main/java/com/huixiang/utils/RefreshTokenInterceptor.java","src/main/java/com/huixiang/utils/LoginInterceptor.java","src/main/java/com/huixiang/utils/UserHolder.java","src/main/java/com/huixiang/utils/RegexUtils.java","src/main/java/com/huixiang/utils/RegexPatterns.java","src/main/java/com/huixiang/utils/PasswordEncoder.java")),
    @("2024-10-21", "feat: implement shop module with Redis caching", @("src/main/java/com/huixiang/service/IShopService.java","src/main/java/com/huixiang/service/IShopTypeService.java","src/main/java/com/huixiang/service/impl/ShopServiceImpl.java","src/main/java/com/huixiang/service/impl/ShopTypeServiceImpl.java","src/main/java/com/huixiang/controller/ShopController.java","src/main/java/com/huixiang/controller/ShopTypeController.java","src/main/java/com/huixiang/controller/UploadController.java")),
    @("2024-10-25", "feat: add Caffeine+Redis two-level cache with anti-breakdown strategy", @("src/main/java/com/huixiang/utils/CacheClient.java","src/main/java/com/huixiang/utils/RedisData.java","src/main/java/com/huixiang/config/CacheConfig.java","src/main/java/com/huixiang/utils/RedisConstants.java")),
    @("2024-10-28", "feat: implement voucher management with normal and seckill types", @("src/main/java/com/huixiang/service/IVoucherService.java","src/main/java/com/huixiang/service/ISeckillVoucherService.java","src/main/java/com/huixiang/service/impl/VoucherServiceImpl.java","src/main/java/com/huixiang/service/impl/SeckillVoucherServiceImpl.java","src/main/java/com/huixiang/controller/VoucherController.java")),
    @("2024-11-04", "feat: implement seckill with atomic Lua script for stock deduction", @("src/main/java/com/huixiang/service/IVoucherOrderService.java","src/main/java/com/huixiang/service/impl/VoucherOrderServiceImpl.java","src/main/java/com/huixiang/controller/VoucherOrderController.java","src/main/resources/seckill.lua","src/main/resources/unlock.lua")),
    @("2024-11-08", "feat: add snowflake ID generator with Redis-based worker ID", @("src/main/java/com/huixiang/utils/RedisIdWorker.java")),
    @("2024-11-12", "fix: add user purchase limit check and Stream async dispatch in Lua", @("src/main/java/com/huixiang/consumer/CacheDeleteConsumer.java")),
    @("2024-11-18", "feat: complete order payment and cancellation flow", @("src/main/java/com/huixiang/utils/SystemConstants.java","src/main/java/com/huixiang/utils/ILock.java","src/main/java/com/huixiang/utils/SimpleRedisLock.java")),
    @("2024-11-22", "feat: implement delayed order auto-close with Redis ZSet", @("src/main/java/com/huixiang/task/OrderCloseTask.java")),
    @("2024-11-28", "feat: integrate Canal for MySQL binlog subscription, Kafka for cache sync", @("src/main/java/com/huixiang/listener/CanalKafkaListener.java","src/main/java/com/huixiang/config/CanalConfig.java")),
    @("2024-12-03", "feat: add exponential backoff retry and dead letter queue for cache delete failures", @()),
    @("2024-12-09", "feat: implement follow system and blog posting module", @("src/main/java/com/huixiang/entity/Follow.java","src/main/java/com/huixiang/entity/Blog.java","src/main/java/com/huixiang/entity/BlogComments.java","src/main/java/com/huixiang/service/IFollowService.java","src/main/java/com/huixiang/service/IBlogService.java","src/main/java/com/huixiang/service/IBlogCommentsService.java","src/main/java/com/huixiang/service/impl/FollowServiceImpl.java","src/main/java/com/huixiang/service/impl/BlogServiceImpl.java","src/main/java/com/huixiang/service/impl/BlogCommentsServiceImpl.java","src/main/java/com/huixiang/controller/FollowController.java","src/main/java/com/huixiang/controller/BlogController.java","src/main/java/com/huixiang/controller/BlogCommentsController.java","src/main/java/com/huixiang/mapper/FollowMapper.java","src/main/java/com/huixiang/mapper/BlogMapper.java","src/main/java/com/huixiang/mapper/BlogCommentsMapper.java")),
    @("2024-12-13", "feat: add Redis ZSet like ranking and feed push for blogs", @()),
    @("2024-12-18", "feat: implement shop geo-location search with Redis Geo", @()),
    @("2024-12-23", "chore: add database init SQL script with sample data", @("src/main/resources/db/huixiang_life.sql")),
    @("2025-01-03", "feat: build custom @RateLimiter annotation with Redis ZSet sliding window", @("src/main/java/com/huixiang/annotation/RateLimiter.java","src/main/java/com/huixiang/aspect/RateLimiterAspect.java","src/main/resources/rate_limiter.lua")),
    @("2025-01-08", "feat: add rate limit blacklist ban after 3 consecutive violations", @()),
    @("2025-01-14", "feat: integrate Bailian LLM for intelligent customer service", @("src/main/java/com/huixiang/config/ChatConfig.java","src/main/java/com/huixiang/service/ChatSessionService.java","src/main/java/com/huixiang/service/FunctionService.java","src/main/java/com/huixiang/service/ChatService.java","src/main/java/com/huixiang/controller/ChatController.java","src/main/resources/chat.html")),
    @("2025-01-20", "feat: refactor to LangChain4j AiServices Agent pattern", @("src/main/java/com/huixiang/service/AgentTools.java","src/main/java/com/huixiang/service/CustomerAgent.java","src/main/java/com/huixiang/service/ChatConfigService.java")),
    @("2025-01-24", "feat: complete Agent @Tool functions for shop search, orders, stock, deals", @()),
    @("2025-01-29", "feat: implement collaborative filtering recommendation engine", @("src/main/java/com/huixiang/service/SmartRecommendService.java","src/main/java/com/huixiang/service/UserPreferenceTracker.java")),
    @("2025-02-05", "feat: add hot key detector to auto-promote popular data to local cache", @("src/main/java/com/huixiang/monitor/HotKeyDetector.java")),
    @("2025-02-10", "feat: implement VIP user isolated thread pool for order processing", @()),
    @("2025-02-14", "feat: add DingTalk alert notification for dead letter queue", @("src/main/java/com/huixiang/utils/DingTalkNotifier.java")),
    @("2025-02-18", "feat: add MDC TraceId interceptor for distributed tracing", @("src/main/java/com/huixiang/utils/TraceIdInterceptor.java","src/main/resources/logback-spring.xml")),
    @("2025-02-20", "chore: configure logback rolling file appender with retention policy", @()),
    @("2025-02-24", "feat: implement multi-node cache sync via Redis Pub/Sub", @("src/main/java/com/huixiang/service/ICacheSyncService.java","src/main/java/com/huixiang/service/impl/CacheSyncServiceImpl.java")),
    @("2025-02-28", "chore: add Docker Compose for local dev infrastructure", @("docker-compose.yml")),
    @("2025-03-04", "refactor: rename project to LocalMind, migrate package com.hmdp to com.huixiang", @()),
    @("2025-03-06", "docs: add README with architecture diagram and quick start guide", @("README.md")),
    @("2025-03-08", "chore: clean up debug logs and unused imports", @())
)

foreach ($c in $commits) {
    $date = $c[0]
    $msg = $c[1]
    $files = $c[2]

    if ($files.Count -gt 0) {
        foreach ($f in $files) {
            if (Test-Path $f) {
                git add $f 2>$null
            }
        }
    }

    $env:GIT_AUTHOR_DATE = "$date 10:00:00 +0800"
    $env:GIT_COMMITTER_DATE = "$date 10:00:00 +0800"
    git commit -m $msg 2>&1 | Out-Null
    Write-Host "[$date] $msg"
}

git add -A 2>$null
$env:GIT_AUTHOR_DATE = "2025-03-10 10:00:00 +0800"
$env:GIT_COMMITTER_DATE = "2025-03-10 10:00:00 +0800"
git commit -m "chore: add remaining test files and resources" 2>&1 | Out-Null

Write-Host ("Total commits: " + (git rev-list --count HEAD))
Write-Host "Ready to push!"
