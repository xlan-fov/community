package com.nowcoder.community.dao;


import com.nowcoder.community.entity.User;
import org.apache.ibatis.annotations.Mapper;

/*
MyBatis 框架提供的注解，用于标记一个接口为 MyBatis 的映射器接口。
MyBatis 会根据接口中的方法定义生成对应的 SQL 语句，并将其与数据库交互。
 */
@Mapper
public interface UserMapper {

    User selectById(int id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);

    int updateStatus(int id,int status);

    int updateHeader(int id,String headerUrl);

    int updatePassword(int id,String password);
}
