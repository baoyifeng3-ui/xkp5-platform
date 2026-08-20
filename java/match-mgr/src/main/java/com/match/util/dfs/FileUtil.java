package com.match.util.dfs;

import java.io.*;
import java.net.URL;
import java.util.Base64;
import java.util.UUID;

public class FileUtil {


    public static File getNetUrlHttp(String netUrl) {
        //对本地文件命名
        File file = null;

        URL urlfile;
        InputStream inStream = null;
        OutputStream os = null;
        try {
            file = File.createTempFile("net_url", String.valueOf(UUID.randomUUID()) + ".tif");
            //下载
            urlfile = new URL(netUrl);
            inStream = urlfile.openStream();
            os = new FileOutputStream(file);

            int bytesRead = 0;
            byte[] buffer = new byte[8192];
            while ((bytesRead = inStream.read(buffer, 0, 8192)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("远程图片获取错误：" + netUrl);
        } finally {
            try {
                if (null != os) {
                    os.close();
                }
                if (null != inStream) {
                    inStream.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return file;
    }


    public static StringBuffer httpToBase64(String imgOld) {
        try {
            File file = FileUtil.getNetUrlHttp(imgOld);

            Base64.Encoder encoder = Base64.getEncoder();

            FileInputStream inputStream = null;

            inputStream = new FileInputStream(file);


            int available = inputStream.available();
            byte[] bytes = new byte[available];
            inputStream.read(bytes);

            String base64Str = encoder.encodeToString(bytes);
            return new StringBuffer(base64Str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
