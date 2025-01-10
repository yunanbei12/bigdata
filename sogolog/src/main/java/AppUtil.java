import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 应用工具类
 */
public class AppUtil {
    /**
     * 将字符串解析成 QueryTerms
     *
     * @param str 输入字符串，格式：[startTime][|endTime]+[userID]+[keyword]+[URL]
     * @return 一个 QueryTerms 对象
     */
    public static QueryTerms getQueryTerms(String str){
        System.out.println("QueryString==>"+str);
        String[] items = {"","","",""};
        int beginIndex=0;
        int count=0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i)=='+') {
                items[count]=str.substring(beginIndex, i);
                beginIndex=i+1;
                count++;
            }
        }
        items[count]=str.substring(beginIndex);

        System.out.println(Arrays.toString(items));

        QueryTerms queryTerms = new QueryTerms();
        String[] item1s = items[0].split("\\|");
        if(item1s.length>0) {
            queryTerms.setStartTime(item1s[0]);
        }
        if(item1s.length>1) {
            queryTerms.setEndTime(item1s[1]);
        }
        String[] item2s = items[1].split("\\|");
        if(item2s.length>0){
            queryTerms.setUserIds(Arrays.stream(item2s).filter(s -> s.length()>0).distinct().collect(Collectors.toList()));
        }
        String[] item3s = items[2].split("\\|");
        if(item3s.length>0){
            queryTerms.setKeyWords(Arrays.stream(item3s).filter(s -> s.length()>0).distinct().collect(Collectors.toList()));
        }
        if(!"#".equals(items[3])){
            String[] item4s = items[3].split("\\|");
            if(item4s.length>0){
                queryTerms.setUrls(Arrays.stream(item4s).filter(s -> s.length()>0).distinct().collect(Collectors.toList()));
            }
        }

        return queryTerms;
    }

    /**
     * 仅将字符串解析成 QueryTerms 中的 startTime和 endTime
     *
     * @param str str 输入字符串，格式：startTime|endTime
     * @return 一个 QueryTerms 对象，其中除startTime和 endTime外，其他属性均为null
     */
    public static QueryTerms getStartTimeEndTime(String str){
        String[] item1s = str.split("\\|");
        QueryTerms queryTerms = new QueryTerms();
        if(item1s.length>0) {
            queryTerms.setStartTime(item1s[0]);
        }
        if(item1s.length>1) {
            queryTerms.setEndTime(item1s[1]);
        }
        return queryTerms;
    }

    /**
     * 获取UUID字符串，去除其中的“-”
     *
     * @return UUID 字符串
     */
    public static String getUUID(){
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * 封装jackson的对象转JSON字符功能
     *
     * @param obj Java对象
     * @return JSON字符串
     */
    public static String obj2Json(Object obj){
        ObjectMapper mapper = new ObjectMapper();
        String res = null;
        try {
            res = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return res;
    }
}
