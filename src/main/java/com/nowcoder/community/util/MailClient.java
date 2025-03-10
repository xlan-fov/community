package com.nowcoder.community.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

/*
标记一个 Java 类为 Spring 管理的组件。当 Spring 容器启动时，
会自动扫描并加载所有标记了 @Component 的类，将其实例化并管理其生命周期。
*/
@Component
public class MailClient {
    private static final Logger logger = LoggerFactory.getLogger(MailClient.class);

    //自动装配 Spring 容器中的 Bean。Spring 会自动查找与字段类型匹配的 Bean，并将其注入到该字段中。
    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public void sendMail(String to,String subject,String content) {

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message);
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content,true);
        } catch (MessagingException e) {
            logger.error("发送邮件失败:"+e.getMessage());
        }
    }
}
