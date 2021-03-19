package org.kelab.framework.util.check;

import org.springframework.web.multipart.MultipartFile;

/**
 * @author JirathLiu
 * @date 2021/1/23
 * @description:
 */
public class FileCheckUtil {

    public enum SizeUnit{
        B,K,M,G
    }
    /**
     * 判断文件大小
     *
     * @param len
     *            文件长度
     * @param size
     *            限制大小
     * @param unit
     *            限制单位（B,K,M,G）
     * @return
     */
    public static boolean checkFileSize(Long len, int size,SizeUnit unit) {
        double fileSize = 0;
        if (unit== SizeUnit.B) {
            fileSize = (double) len;
        } else if (unit== SizeUnit.K) {
            fileSize = (double) len / 1024;
        } else if (unit== SizeUnit.M) {
            fileSize = (double) len / 1048576;
        } else if (unit== SizeUnit.G) {
            fileSize = (double) len / 1073741824;
        }
        if (fileSize > size) {
            return false;
        }
        return true;
    }
    public static boolean checkFileSize(MultipartFile file, int size, SizeUnit unit) {
        return checkFileSize(file.getSize(), size, unit);
    }
}
