package com.match.controller;

import cn.fastword.word.WordFile07Writer;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.match.entity.AnswerSheet;
import com.match.entity.Subject;
import com.match.entity.SubjectType;
import com.match.entity.User;
import com.match.service.TestPaperService;
import com.match.service.impl.UserServiceImpl;
import com.match.util.img.ConverUtils;
import com.match.util.dfs.FastDFSClient;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * <p>
 *  导出word
 * </p>
 *
 * @author jy
 * @since 2022-09-20
 */
@RestController
@RequestMapping("/export")
@Api
public class DocController {
    @Autowired
    TestPaperService testPaperService;
    @Autowired
    UserServiceImpl userService;








    @GetMapping("doc")
    public void doc(HttpServletResponse response,User user, Subject subject ) throws Exception {
        user.setUserId(StpUtil.getLoginIdAsInt());
        User user1 = userService.getById(user.getUserId());
        WordFile07Writer writer = new WordFile07Writer();

       JSONArray jsonArray = testPaperService.getSubject(user,subject);



        writer.addHeader("“中育数据杯”图像识别数据处理比赛赛题", "");



       for (int i = 0; i < jsonArray.size(); i++) {

            Object subject2 = jsonArray.getJSONObject(i).get("subject");

            ObjectMapper objectMapper2 = new ObjectMapper();
            Subject sub1 = objectMapper2.convertValue(subject2,Subject.class);

            Object answerSheet = jsonArray.getJSONObject(i).get("answerSheet");

            ObjectMapper objectMapper3 = new ObjectMapper();
            AnswerSheet AnswerSheet = objectMapper3.convertValue(answerSheet,AnswerSheet.class);

           if (sub1.getModularName()!=null){
               writer.addParagraphRows(sub1.getModularName());
           }
           if (sub1.getSubjectIdentification()!=null){
               writer.addParagraphRows(sub1.getSubjectIdentification());
           }
            writer.addParagraphRows(sub1.getSubjectName());
            writer.addParagraphRows("答：" + formatAnswer(sub1, AnswerSheet));
            if (SubjectType.PRACTICAL.getCode().equals(sub1.getSubjectType())
                    && AnswerSheet != null && AnswerSheet.getAnswerImg() != null) {
                String[] split = AnswerSheet.getAnswerImg().split(",");
                for (String image : split) {
                    if (image.isEmpty()) {
                        continue;
                    }
                    String imageData = image.startsWith("/files/")
                            || image.startsWith("http://")
                            || image.startsWith("https://")
                            ? ConverUtils.netSourceToBase64(FastDFSClient.getServerAccessUrl(image), "GET")
                            : image;
                    if (imageData == null || imageData.isEmpty()) {
                        continue;
                    }
                    File picture = ConverUtils.base64ToFile(imageData);
                    BufferedImage sourceImg = ImageIO.read(new FileInputStream(picture));
                    writer.addPicture(picture, 100,
                            (int) (sourceImg.getHeight() * (100.0f / sourceImg.getWidth())));
                }
            }




        }





        String docuemntFile = writer.getDocumentFile(user1.getUserName(),System.getProperty("java.io.tmpdir")
        ); // 返回值为文档所在存储完整路径
        downloadLocal(response,docuemntFile,user1.getUserName());

    }

    private String formatAnswer(Subject subject, AnswerSheet answerSheet) {
        if (answerSheet == null || SubjectType.PRACTICAL.getCode().equals(subject.getSubjectType())) {
            return "";
        }
        String answer = answerSheet.getAnswerText();
        if (answer == null) {
            return "";
        }
        if (SubjectType.MULTIPLE_CHOICE.getCode().equals(subject.getSubjectType())) {
            try {
                return String.join("、", JSON.parseArray(answer, String.class));
            } catch (Exception ignored) {
                return answer;
            }
        }
        return answer;
    }






    public void downloadLocal(HttpServletResponse response,String docuemntFile,String name) throws FileNotFoundException {
        // 下载本地文件
        String fileName = name+".doc".toString(); // 文件的默认保存名
        // 读到流中
        InputStream inStream = new FileInputStream(docuemntFile);// 文件的存放路径
        // 设置输出的格式
        response.reset();
        response.setContentType("bin");
        response.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        // 循环取出流中的数据
        byte[] b = new byte[100];
        int len;
        try {
            while ((len = inStream.read(b)) > 0)
                response.getOutputStream().write(b, 0, len);
            inStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}
