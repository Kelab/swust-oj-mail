package org.kelab.framework.util;

/**
 * @author JirathLiu
 * @date 2020/10/7
 * @description:
 */
public class ParameterException extends RuntimeException{
    public ParameterException() {
    }

    public ParameterException(String message) {
        super(message);
    }

    public ParameterException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParameterException(Throwable cause) {
        super(cause);
    }

    public ParameterException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
