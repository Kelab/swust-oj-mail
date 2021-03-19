package org.kelab.framework.util.check;

/**
 * @author JirathLiu
 * @date 2020/10/7
 * @description:
 */
public class StringBaseUtil {
    public static boolean isStringNull(String s){
        if (s==null || s.trim().length()==0){
            return true;
        }else {
            return false;
        }
    }
    public static boolean isStringListNull(String ...s){
        for (String toVerify: s){
            if (isStringNull(toVerify)){
                return true;
            }
        }
        return false;
    }
}
