
import org.apache.spark.api.java.JavaRDD;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Statistic {

    private static final String path = "/home/hadoop/Desktop/sogolog-master-master/sogolog-master-master/sogolog-master-master/SogouQ.sample";

    /**
     * 时段流量统计: 以`hh:mm:ss`格式输入`起始时间`和`结束时间`，统计这段时间之内的`总搜索次数`、`各个查询词搜索次数`，`各个网站的访问量`。
     *
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @return 段时间之内的`总搜索次数`、`各个查询词搜索次数`，`各个网站的访问量`
     */
    public Map<String, Object> flowStatisticsByTime(String startTime, String endTime) {
        Logger.logInfo("开始进行时段流量统计，起始时间: " + startTime + ", 结束时间: " + endTime);
        Map<String, Object> result = new HashMap<>(3);
        try {
            JavaRDD<String> stringJavaRDD = Analyze.sc.textFile(path);
            JavaRDD<String[]> rdd = stringJavaRDD.map(s -> s.split("\\s"));

            // 过滤时间范围
            rdd = rdd.filter(line -> line[Analyze.VISIT_TIME_INDEX].compareTo(startTime) >= 0 && line[Analyze.VISIT_TIME_INDEX].compareTo(endTime) <= 0);

            long allCount = rdd.count(); // 总搜索次数
            result.put("search", allCount);
            Logger.logInfo("统计到的总搜索次数: " + allCount);

            Map<String, Long> searchCount = rdd
                    .keyBy(line -> line[Analyze.QUERY_WORD_INDEX])
                    .countByKey(); // 各个查询词搜索次数
            result.put("searchCount", searchCount);
            Logger.logInfo("各个查询词统计完成，数量: " + searchCount.size());

            Pattern p = Pattern.compile("[^.]*?\\.(com.cn|net.cn|org.cn|gov.cn|edu.cn|com|cn|cc|tv|biz|edu|gov|info|int|mil|name|net|org|pro|xyz)", Pattern.CASE_INSENSITIVE);

            rdd = rdd.map(line -> {
                String[] split = line[Analyze.URL_INDEX].split("/");
                if (split.length > 0) {
                    line[Analyze.URL_INDEX] = split[0];
                }
                Matcher m = p.matcher(line[Analyze.URL_INDEX]);
                if (m.find()) {
                    line[Analyze.URL_INDEX] = m.group();
                }
                return line;
            }); // 提取一级域名

            Map<String, Long> visitCount = rdd
                    .keyBy(line -> line[Analyze.URL_INDEX])
                    .countByKey(); // 各个网站的访问量

            result.put("visitCount", visitCount);
            Logger.logInfo("各个网站访问量统计完成，数量: " + visitCount.size());

        } catch (Exception e) {
            Logger.logFailure("时段流量统计任务", e.getMessage());
        }
        return result;
    }

    /**
     * 用户使用频率统计: 统计每个用户一天内的搜索次数
     *
     * @return 统计每个用户一天内的搜索次数
     */
    public Map<String, Long> countByUser() {
        Logger.logInfo("开始进行用户使用频率统计");
        Map<String, Long> userCount = new TreeMap<>();
        try {
            JavaRDD<String[]> rdd = Analyze.sc.textFile(path).map(s -> s.split("\\s"));
            userCount = new TreeMap<>(rdd.keyBy(line -> line[Analyze.U_ID_INDEX]).countByKey());
            Logger.logInfo("用户使用频率统计完成，用户数量: " + userCount.size());
        } catch (Exception e) {
            Logger.logFailure("用户使用频率统计任务", e.getMessage());
        }
        return userCount;
    }

    /**
     * 访问行为统计: 根据该页面在搜索结果中的排名(第4字段)，统计不同排名的结果被访问的情况。
     *
     * @return 统计不同排名的结果被访问的情况
     */
    public Map<String, Long> accessBehaviorStatistics() {
        Logger.logInfo("开始进行访问行为统计");
        Map<String, Long> accessCount = new TreeMap<>();
        try {
            JavaRDD<String[]> rdd = Analyze.sc.textFile(path).map(s -> s.split("\\s"));
            accessCount = new TreeMap<>(rdd
                    .keyBy(line -> line[Analyze.QUERY_WORD_INDEX] + "=>" + line[Analyze.RANK_INDEX])
                    .countByKey());
            Logger.logInfo("访问行为统计完成，统计结果数量: " + accessCount.size());
        } catch (Exception e) {
            Logger.logFailure("访问行为统计任务", e.getMessage());
        }
        return accessCount;
    }
}
