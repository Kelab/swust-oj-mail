package org.kelab.framework.util.check;

import java.util.regex.Pattern;

/**
 * @author JirathLiu
 * @date 2020/10/10
 * @description:
 */
public class UrlCheckUtil {
    private static Pattern pattern=Pattern.compile("\\d{1,3}(.\\d{1,3}){3}:\\d+");
    public static boolean isUri(String s){
        if (StringBaseUtil.isStringNull(s)){
            return false;
        }
        if (pattern.matcher(s).matches()){
            return true;
        }
        return false;
    }
}
