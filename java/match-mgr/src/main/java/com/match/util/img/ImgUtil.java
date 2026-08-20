package com.match.util.img;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImgUtil {

    /**
     * 	将tiff图片转化为jpg，生成新的文件
     * @param oldPath	原图片的全路径
     * @param newPath	生成新的图片的全路径
     */

    public static void tiffToJpg(String oldPath,String newPath) {
        try {
            BufferedImage bufferegImage= ImageIO.read(new File(oldPath));
            ImageIO.write(bufferegImage,"jpg",new File(newPath));//可以是png等其它图片格式

        }catch(IOException e) {
            e.printStackTrace();
        }
    }
}
