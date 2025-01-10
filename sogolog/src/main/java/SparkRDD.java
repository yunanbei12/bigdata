import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SparkRDD {

    private static final String path = "/home/hadoop/Desktop/sogolog-master-master/sogolog-master-master/sogolog-master-master/SogouQ.sample";

    /**
     * 基于大数据计算技术的条件查询: 使用RDD算子，实现类似于HBASE的六个字段条件搜索。
     *
     * @param queryTerms 查询条件
     * @return 查询结果集
     */
    public List<String> queryByRDD(QueryTerms queryTerms) {
        Logger.logInfo("开始执行查询，查询条件: " + queryTerms.toString());

        JavaRDD<String[]> rdd = null;
        try {
            //根据传入的 QueryTerms 对象定义的查询条件，逐步对 RDD 应用过滤操作（filter）
            rdd = Analyze.sc.textFile(path).map(s -> s.split("\\s"));

            if (StringUtils.isNotBlank(queryTerms.getStartTime())) {
                Logger.logInfo("应用开始时间过滤: " + queryTerms.getStartTime());
                rdd = rdd.filter(line -> line[Analyze.VISIT_TIME_INDEX].compareTo(queryTerms.getStartTime()) >= 0);
            }
            if (StringUtils.isNotBlank(queryTerms.getEndTime())) {
                Logger.logInfo("应用结束时间过滤: " + queryTerms.getEndTime());
                rdd = rdd.filter(line -> line[Analyze.VISIT_TIME_INDEX].compareTo(queryTerms.getEndTime()) <= 0);
            }

            if (CollectionUtils.isNotEmpty(queryTerms.getUserIds())) {
                Logger.logInfo("应用用户ID过滤: " + queryTerms.getUserIds());
                rdd = rdd.filter(line -> {
                    for (String uid : queryTerms.getUserIds()) {
                        if (line[Analyze.U_ID_INDEX].contains(uid)) {
                            return true;
                        }
                    }
                    return false;
                });
            }

            if (CollectionUtils.isNotEmpty(queryTerms.getKeyWords())) {
                Logger.logInfo("应用关键词过滤: " + queryTerms.getKeyWords());
                rdd = rdd.filter(line -> {
                    for (String kw : queryTerms.getKeyWords()) {
                        if (line[Analyze.QUERY_WORD_INDEX].contains(kw)) {
                            return true;
                        }
                    }
                    return false;
                });
            }

            if (CollectionUtils.isNotEmpty(queryTerms.getUrls())) {
                Logger.logInfo("应用URL过滤: " + queryTerms.getUrls());
                rdd = rdd.filter(line -> {
                    for (String url : queryTerms.getUrls()) {
                        if (line[Analyze.URL_INDEX].contains(url)) {
                            return true;
                        }
                    }
                    return false;
                });
            }

            final long[] id = {1L};
            //RDD 转换为键值对形式
            JavaPairRDD<Long, String> pairRDD = rdd.mapToPair(lines -> {
                id[0] = id[0] + 1L;
                return new Tuple2<>(id[0], Arrays.toString(lines));
            });

            //将键值对收集到内存中，提取记录内容作为查询结果
            Map<Long, String> collectAsMap = pairRDD.collectAsMap();

            Logger.logInfo("查询完成，共找到 " + collectAsMap.size() + " 条记录");
            return new ArrayList<>(collectAsMap.values());

        } catch (Exception e) {
            Logger.logFailure("查询任务", e.getMessage());
            return new ArrayList<>(); // 返回空列表或处理其他方式
        }
    }
}
