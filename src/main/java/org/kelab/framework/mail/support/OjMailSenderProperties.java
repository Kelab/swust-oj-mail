package org.kelab.framework.mail.support;

import lombok.Data;
import org.kelab.framework.util.logger.LogAbility;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 无论是否开启高压模式，都需要进行配置，目的是为了开启高压时能正常运行
 * 邮箱配置
 * spring cloud 在刷新配置时，会重新构建一个bean，
 * 所以需要将热刷新的配置单独拉出来，使用时应该使用get来获取
 * 允许在配置文件中配置发信邮箱（多级），
 * 配置项目：
 * - 是否开启高压处理（降级与削峰）
 * - 备选邮箱
 * - 线程存活时间
 * - 报警长度
 * - 错误监控时间间隔
 * - 连续错误次数
 * @author jirath
 * @date 2021/1/31 上午11:03
 * @description:
 */
@Component
@RefreshScope
@Data
@ConfigurationProperties(prefix = "oj.org.kelab.framework.mail",ignoreInvalidFields = true)
public class OjMailSenderProperties implements InitializingBean, LogAbility {
    private LocalDateTime localDateTime=LocalDateTime.now();
    private Boolean enableStable=true;
    private List<MailAccount> mailList;
    private Integer taskRunNum=1;
    private Integer maxTaskNum= 1;
    private Duration threadKeepLiveTime = Duration.ofMinutes(5);
    private Integer maxErrTime=2;
    private Duration recheckTime = Duration.ofSeconds(2);

    @Data
    public static class MailAccount {
        String host;
        String username;
        String password;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (mailList==null||mailList.isEmpty()) {
            LOGGER.warn("No org.kelab.framework.mail set, oj org.kelab.framework.mail can't be used.");
        }
    }

}
