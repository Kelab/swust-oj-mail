package org.kelab.framework.mail;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.SimpleEmail;

import java.util.List;

/**
 * 发送邮件
 * 提供了两种格式的邮件，使用ApacheMail作为底层进行发送
 * 接收目标两种类作为参数，并会对内容是否能发送进行检查
 * @author JirathLiu
 * @date 2021/1/31
 * @description:
 */
public class BasicMailSender implements MailSender {

    protected boolean shouldCheck(){
        return true;
    }
    private boolean doSendHtml(HtmlMail mail) {
        try {
            String host = mail.getHost();
            String username = mail.getUsername();
            String password = mail.getPassword();
            String sender = mail.getSender();
            String from = sender + "<" + username + ">";
            HtmlEmail email = new HtmlEmail();
            email.setHostName(host);
            email.setAuthenticator(new DefaultAuthenticator(username, password));
            email.setSSLOnConnect(true);
            email.setCharset("UTF-8");
            email.setFrom(from);
            email.setSubject(mail.getSubject());
            email.setHtmlMsg(mail.getContent());
            List<String> addressList = mail.getAddresses();
            for (String address : addressList) {
                email.addTo(address);
            }
            email.send();
            return true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
    }
    private boolean doSendSimpleMail(SimpleMail mail) {
        try {
            String host = mail.getHost();
            String username = mail.getUsername();
            String password = mail.getPassword();
            String sender = mail.getSender();
            String from = sender + "<" + username + ">";
            SimpleEmail email = new SimpleEmail();
            email.setHostName(host);
            email.setAuthenticator(new DefaultAuthenticator(username, password));
            email.setSSLOnConnect(true);
            email.setCharset("UTF-8");
            email.setFrom(from);
            email.setSubject(mail.getSubject());
            email.setMsg(mail.getContent());
            List<String> addressList = mail.getAddresses();
            for (String address : addressList) {
                email.addTo(address);
            }
            email.send();
            return true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean send(Mail mail) {
        if (shouldCheck() && !mail.canSend()) {
            return false;
        }
        if (mail instanceof HtmlMail) {
            return doSendHtml((HtmlMail) mail);
        } else if (mail instanceof SimpleMail) {
            return doSendSimpleMail((SimpleMail) mail);
        }else {
            throw new IllegalArgumentException("Unsupported kind of org.kelab.framework.mail.");
        }

    }
}