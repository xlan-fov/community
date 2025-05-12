# Community 项目 Redis 与 Kafka 应用详解

## 项目背景
- 时间：2024.11 – 2025.03  
- 技术：Spring Boot、MyBatis、Thymeleaf、Redis、Kafka  
- 职责：后端模块开发、数据库表与消息主题设计  

---

## 一、Redis

### 1. 会话管理：登录凭证（Ticket）
- 目的  
  HTTP 无状态，需跨请求/服务器保持登录，会话共享成本高，选 Redis 存储 Ticket。
- 核心流程  
  1) 登录成功后，生成 `LoginTicket`（userId、ticket、status、expired）。  
  2) 写入 Redis：  
     ```java
     // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\service\UserService.java
     redisTemplate.opsForValue().set(
         RedisKeyUtil.getTicketKey(ticket.getTicket()), ticket);
     ```
  3) 服务端下发 Cookie（响应头 Set-Cookie）：  
     ```java
     // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\service\UserService.java
     Cookie cookie = new Cookie("ticket", ticket.getTicket());
     cookie.setPath(contextPath);
     cookie.setMaxAge(expiredSeconds);
     response.addCookie(cookie);
     ```
  4) 浏览器自动携带：  
     - 之后的每次 HTTP 请求，浏览器会在请求头 `Cookie: ticket=xxx` 中自动带上该 ticket，无需前端额外处理。  
  5) 下发 Cookie，拦截器读取并校验：  
     ```java
     // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\controller\interceptor\LoginTicketInterceptor.java
     LoginTicket lt = (LoginTicket) redisTemplate.opsForValue()
         .get(RedisKeyUtil.getTicketKey(cookieTicket));
     if (lt.getStatus()==0 && lt.getExpired().after(new Date())) {
         hostHolder.setUser(userService.findUserById(lt.getUserId()));
     }
     ```
- 配置  
  ```properties
  spring.redis.host=localhost
  spring.redis.port=6379
  spring.redis.database=11
  ```
- 作用  
  1. 维护登录状态：Ticket 相当于 Session ID，客户端通过 Cookie 每次请求携带。  
  2. 用户识别：拦截器读取 Ticket，从 Redis 加载对应的 `LoginTicket` 并获取用户信息。  
  3. 访问控制：保护需登录资源，未携带或失效的 Ticket 无法访问。  
  4. 分布式支持：无状态请求模式下，可在多节点/集群间共享会话数据。

### Ticket 与 Session ID 的区别
- 存储位置  
  Session ID 通常由容器（内存、数据库或专用 Session 服务）管理；Ticket 直接存储在 Redis，应用无需维护会话容器。  
- 可扩展性  
  多实例环境下 Session 需同步或集中存储；Ticket 利用 Redis 天然共享，水平扩展成本低。  
- 过期与失效  
  Session 依赖容器配置，重启或内存回收可能导致丢失；Ticket TTL 可自定义，集群统一管理更可靠。  
- 安全性  
  Session ID 仅为引用；Ticket 可设计为加密或签名的令牌，防篡改且可携带额外元数据。  
- 无状态 vs 有状态  
  使用 Session 时应用服务器需保留用户状态（有状态）；Ticket+Redis 模式下应用服务器保持无状态，易于弹性伸缩。  

### 2. 图形验证码：防刷设计
- 目的  
  防止暴力破解，避免 Session 膨胀与分布式问题。
- 实现  
  1) 生成文本 & 图片，UUID 归属。  
  2) 文本缓存 Redis，TTL 60s：  
     ```java
     // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\controller\LoginController.java
     redisTemplate.opsForValue()
       .set(RedisKeyUtil.getKaptchaKey(owner), text, 60, TimeUnit.SECONDS);
     ```
  3) 登录时从 Redis 获取并比对 `equalsIgnoreCase`。

### 3. 用户信息缓存
- 目的  
  头像、用户名等频繁展示，读 DB 成本高。
- 策略  
  - 读时缓存（缓存不存在则加载+写入，TTL 3600s）  
  - 写时清除（头像、状态更新后 `delete`）
- 代码  
  ```java
  // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\service\UserService.java
  User u = (User) redisTemplate.opsForValue().get(RedisKeyUtil.getUserKey(id));
  if (u==null) {
      u = userMapper.selectById(id);
      redisTemplate.opsForValue()
        .set(RedisKeyUtil.getUserKey(id), u, 3600, TimeUnit.SECONDS);
  }
  ```

### 4. 关注关系缓存（ZSet）
- 目的  
  支持按时间排序分页查询粉丝与关注列表。
- 实现  
  ```java
  // filepath: .../FollowService.java
  redisTemplate.opsForZSet().add(followeeKey, targetId, now);
  redisTemplate.opsForZSet().add(followerKey, userId, now);
  ```
- 面试要点  
  ZSet 排序原理、内存消耗、分页效率。

### 5. 编程式事务与测试
- 事务示例  
  ```java
  // filepath: .../RedisTests.java
  redisTemplate.execute(new SessionCallback() {
      ...existing code...
  });
  ```
- 测试场景：String/Hash/List/Set/ZSet 操作，保证序列化配置正确。

---

## 二、Kafka

### 1. 异步事件发布
- 目的  
  点赞/评论/关注后不阻塞主流程，异步通知解耦、削峰。
- 生产者  
  ```java
  // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\event\EventProducer.java
  kafkaTemplate.send(event.getTopic(), JSONObject.toJSONString(event));
  ```
- 配置  
  ```properties
  spring.kafka.bootstrap-servers=localhost:9092
  spring.kafka.consumer.group-id=community-consumer-group
  spring.kafka.producer.key-serializer=StringSerializer
  spring.kafka.producer.value-serializer=StringSerializer
  ```

### 2. 异步事件消费
- 消费者  
  ```java
  // filepath: d:\JavaCode\community\src\main\java\com\nowcoder\community\event\EventConsumer.java
  @KafkaListener(topics = {TOPIC_COMMENT, TOPIC_LIKE, TOPIC_FOLLOW})
  public void handle(ConsumerRecord record) {
      ...existing code...
  }
  ```
- 面试要点  
  消费者组概念、Offset 管理、幂等处理、消费失败重试方案。

---

## 三、功能模块划分

### 1. 身份认证模块（Authentication）
- 负责：用户注册、登录、激活、凭证校验  
- Java 文件：  
  - controller/LoginController.java  
  - service/UserService.java  
  - controller/interceptor/LoginTicketInterceptor.java  
  - entity/LoginTicket.java  
  - dao/LoginTicketMapper.java  
  - util/CookieUtil.java  
  - util/HostHolder.java  

### 2. 论坛内容模块（Forum）
- 负责：帖子发布、评论查询与存储  
- Java 文件：  
  - dao/DiscussPostMapper.java  
  - entity/DiscussPost.java  
  - dao/CommentMapper.java    (与 comment-mapper.xml 配合)  
  - service/CommentService.java  

### 3. 社交互动模块（Social Interaction）
- 负责：点赞（Like）、关注（Follow）功能及 Redis 缓存操作  
- Java 文件：  
  - util/RedisKeyUtil.java  
  - service/FollowService.java  
  - service/LikeService.java  

### 4. 系统通知模块（Notification）
- 负责：异步消息生产与消费、站内信持久化  
- Java 文件：  
  - event/EventProducer.java  
  - event/EventConsumer.java  
  - service/MessageService.java  
  - dao/MessageMapper.java  
  - entity/Message.java  
  - entity/Event.java  

### 5. 基础设施模块（Infrastructure）
- 负责：拦截器、工具类、邮件、验证码等通用功能  
- Java 文件：  
  - controller/interceptor/AlphaInterceptor.java  
  - util/CommunityUtil.java  
  - util/MailClient.java  
  - controller/LoginController.java  （验证码相关逻辑）  

### 6. 数据访问模块（DAO）
- 负责：MyBatis Mapper 接口与 XML 映射  
- Java 文件：  
  - dao/UserMapper.java  
  - dao/DiscussPostMapper.java  
  - dao/CommentMapper.java  
  - dao/LoginTicketMapper.java  
  - dao/MessageMapper.java  

### 7. 测试模块（Testing）
- 负责：单元测试、Redis/Kafka 手动测试示例  
- Java 文件：  
  - test/RedisTests.java  
  - test/MapperTests.java  
  - test/KafkaTests.java  
  - test/BlockingQueueTests.java  

## 四、Java 文件推荐阅读顺序

1. 启动与配置  
   - CommunityApplication.java（项目入口、Spring Boot 配置）  
   - application.properties（数据库、Redis、Kafka 等核心配置）

2. 工具类与常量  
   - util/CommunityUtil.java（UUID、MD5 等通用方法）  
   - util/RedisKeyUtil.java（Redis Key 生成规则）  
   - util/CookieUtil.java & util/HostHolder.java（Cookie 读取与线程内用户存储）

3. 身份认证与拦截  
   - controller/LoginController.java（注册、激活、登录、验证码）  
   - controller/interceptor/LoginTicketInterceptor.java（Ticket 校验、用户加载）  
   - controller/interceptor/AlphaInterceptor.java（通用拦截器示例）

4. 业务逻辑层  
   - service/UserService.java（用户注册、登录、缓存、激活逻辑）  
   - service/CommentService.java（评论查询与存储）  
   - service/FollowService.java & service/LikeService.java（关注点赞功能）  
   - service/MessageService.java（站内信持久化）

5. 数据访问层  
   - dao/UserMapper.java  
   - dao/DiscussPostMapper.java  
   - dao/CommentMapper.java  
   - dao/LoginTicketMapper.java  
   - dao/MessageMapper.java  
   - 对应的 `src/main/resources/mapper/*.xml` 映射文件

6. 事件驱动  
   - event/EventProducer.java（Kafka 事件发送）  
   - event/EventConsumer.java（Kafka 事件消费、站内信生成）  
   - entity/Event.java

7. 实体类  
   - entity/User.java  
   - entity/DiscussPost.java  
   - entity/LoginTicket.java  
   - entity/Message.java

8. 测试示例  
   - src/test/java/com/nowcoder/community/RedisTests.java  
   - src/test/java/com/nowcoder/community/MapperTests.java  
   - src/test/java/com/nowcoder/community/KafkaTests.java  
   - src/test/java/com/nowcoder/community/BlockingQueueTests.java  

按照以上顺序阅读，可从项目入口到核心业务再到底层存储和测试，逐步理清整体架构和逻辑。