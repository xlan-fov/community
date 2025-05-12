package com.nowcoder.community.service;

import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

/**
 * LikeService 基于Redis实现实体点赞与取消点赞，
 * 并统计实体点赞数及用户获赞总数。
 */
@Service
public class LikeService {

    @Autowired
    private RedisTemplate redisTemplate;

    /** 点赞或取消点赞操作 */
    public void like(int userId, int entityType, int entityId, int entityUserId) {
        //使用redisTemplate.execute方法执行一个事务性的操作。SessionCallback用于定义事务内的操作
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                //entityLikeKey：存储实体点赞用户的集合键。
                //userLikeKey：存储用户获得点赞数量的键。
                String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
                String userLikeKey = RedisKeyUtil.getUserLikeKey(entityUserId);
                //检查当前用户是否已经在点赞集合中
                boolean isMember = operations.opsForSet().isMember(entityLikeKey, userId);
                //开始一个事务，之后的操作会放入事务队列中
                operations.multi();
                //如果用户已经点赞（isMember为true），则从集合中移除用户，并减少用户点赞计数。
                //如果用户未点赞（isMember为false），则将用户添加到集合中，并增加用户点赞计数。
                //
                if (isMember) {
                    operations.opsForSet().remove(entityLikeKey, userId);
                    operations.opsForValue().decrement(userLikeKey);
                } else {
                    operations.opsForSet().add(entityLikeKey, userId);
                    operations.opsForValue().increment(userLikeKey);
                }

                return operations.exec();
            }
        });
    }

    /** 查询实体的点赞数量 */
    public long findEntityLikeCount(int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        return redisTemplate.opsForSet().size(entityLikeKey);
    }

    /** 查询用户对实体的点赞状态(0或1) */
    public int findEntityLikeStatus(int userId, int entityType, int entityId) {
        String entityLikeKey = RedisKeyUtil.getEntityLikeKey(entityType, entityId);
        return redisTemplate.opsForSet().isMember(entityLikeKey, userId) ? 1 : 0;
    }

    /** 查询某用户获得的点赞总数 */
    public int findUserLikeCount(int userId) {
        String userLikeKey = RedisKeyUtil.getUserLikeKey(userId);
        Integer count = (Integer) redisTemplate.opsForValue().get(userLikeKey);
        return count == null ? 0 : count.intValue();
    }

}
