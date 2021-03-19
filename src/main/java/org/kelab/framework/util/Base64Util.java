package org.kelab.framework.util;

import java.util.Base64;

/**
 * @author JirathLiu
 * @date 2021/1/15
 * @description:
 */
public class Base64Util {
    public static String encode(String amb){
        return new String(Base64.getEncoder().encode(amb.getBytes()));
    }
    public static String decode(String amb){
        return new String(Base64.getDecoder().decode(amb));
    }
}
