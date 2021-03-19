package org.kelab.framework.util.check;

import java.util.regex.Pattern;

/**
 * @author JirathLiu
 * @date 2021/1/31
 * @description:
 */
public class MailCheckUtil {
    private static final Pattern MAIL_PATTERN = Pattern.compile("^([A-Za-z0-9_\\-\\.\\u4e00-\\u9fa5])+\\@([A-Za-z0-9_\\-\\.])+\\.([A-Za-z]{2,8})$");

    public static boolean isMail(String amb) {
        return MAIL_PATTERN.matcher(amb).matches();
    }
}
