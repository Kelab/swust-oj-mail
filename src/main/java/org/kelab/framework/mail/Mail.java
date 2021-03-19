package org.kelab.framework.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.kelab.framework.util.check.MailCheckUtil;
import org.kelab.framework.util.check.StringBaseUtil;

import java.util.List;

/**
 * 一个基础的邮件任务
 * @author JirathLiu
 * @date 2021/1/31
 * @description:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Mail {
    protected List<String> addresses;
    protected String subject;
    protected String content;
    protected String host ;
    protected String username ;
    protected String password ;
    protected String sender ;

    public boolean isLegal() {
        if (addresses==null||addresses.isEmpty()) {
            return false;
        }
        for (String s : addresses) {
            if (! MailCheckUtil.isMail(s)) {
                return false;
            }
        }
        return !StringBaseUtil.isStringListNull(subject,content,sender);
    }
    public boolean canSend(){
        return isLegal()
                && !StringBaseUtil.isStringListNull(host,username,password);
    }
}
