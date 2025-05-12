package com.nowcoder.community.util;

import com.nowcoder.community.entity.User;
import org.springframework.stereotype.Component;

/**
 * HostHolder 使用 ThreadLocal 存储当前线程的用户信息，
 * 用于替代HttpSession，避免并发问题。
 */
@Component
public class HostHolder {

    private ThreadLocal<User> users = new ThreadLocal<>();

    /** 保存用户信息到当前线程 */
    public void setUser(User user) {
        users.set(user);
    }

    /** 获取当前线程的用户信息 */
    public User getUser() {
        return users.get();
    }

    /** 清除当前线程的用户信息，防止内存泄漏 */
    public void clear() {
        users.remove();
    }

}
