package org.kelab.framework.mail;


import org.kelab.framework.util.logger.LogAbility;

/**
 * 邮箱接口
 * @author JirathLiu
 * @date 2021/1/31
 * @description:
 */
public interface MailSender extends LogAbility {
    boolean send(Mail mail);
}
