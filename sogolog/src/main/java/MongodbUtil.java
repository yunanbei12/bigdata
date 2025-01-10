import com.mongodb.Block;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class MongodbUtil {
    private static final String DATABASE_NAME = "MongoDataBase";
    private static final String COLLECTION_NAME = "admin";

    private static final MongoClient mongoClient;
    private static final MongoDatabase database;
    private static final MongoCollection<Document> collection;
    //MongDB连接配置
    static {
        // 使用 MongoClients 创建 MongoClient 实例
        mongoClient = MongoClients.create("mongodb://localhost:27017");//创建了一个 MongoDB 客户端
        database = mongoClient.getDatabase(DATABASE_NAME);             //通过客户端获取名为 MongoDataBase 的数据库
        collection = database.getCollection(COLLECTION_NAME);
    }

    // 插入日志数据
    public static void insertLog(String[] logData) {
        Document doc = new Document()       //创建MongoDB文档Document
                .append("visit_time", logData[Analyze.VISIT_TIME_INDEX])
                .append("u_id", logData[Analyze.U_ID_INDEX])
                .append("query_word", logData[Analyze.QUERY_WORD_INDEX])
                .append("rank", logData[Analyze.RANK_INDEX])
                .append("ordinal", logData[Analyze.ORDINAL_INDEX])
                .append("url", logData[Analyze.URL_INDEX]);
        collection.insertOne(doc);          //将文档插入集合中
    }
    // 读取文件并插入日志数据
    public static void readFileAndInsertLogs(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 使用正则表达式将多个空格分隔开
                String[] logData = line.trim().split("\\s+"); // 按照空格分隔
                if (logData.length >= 5) { // 确保读取到正确数量的字段
                    insertLog(logData);
                } else {
                    System.out.println("无效数据行: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // 查询日志数据
    public static List<String> query(QueryTerms queryTerms) {
        List<Bson> filters = new ArrayList<>();

        // 添加时间范围过滤条件
        if (StringUtils.isNotBlank(queryTerms.getStartTime())) {
            filters.add(Filters.gte("visit_time", queryTerms.getStartTime()));
        }
        if (StringUtils.isNotBlank(queryTerms.getEndTime())) {
            filters.add(Filters.lte("visit_time", queryTerms.getEndTime()));
        }

        // 添加用户ID过滤条件
        if (CollectionUtils.isNotEmpty(queryTerms.getUserIds())) {
            List<Bson> userFilters = new ArrayList<>();
            for (String uid : queryTerms.getUserIds()) {
                userFilters.add(Filters.regex("u_id", Pattern.quote(uid)));
            }
            filters.add(Filters.or(userFilters));
        }

        // 添加查询关键词过滤条件
        if (CollectionUtils.isNotEmpty(queryTerms.getKeyWords())) {
            List<Bson> keywordFilters = new ArrayList<>();
            for (String keyword : queryTerms.getKeyWords()) {
                keywordFilters.add(Filters.regex("query_word", Pattern.quote(keyword)));
            }
            filters.add(Filters.or(keywordFilters));
        }

        // 添加URL过滤条件
        if (CollectionUtils.isNotEmpty(queryTerms.getUrls())) {
            List<Bson> urlFilters = new ArrayList<>();
            for (String url : queryTerms.getUrls()) {
                urlFilters.add(Filters.regex("url", Pattern.quote(url)));
            }
            filters.add(Filters.or(urlFilters));
        }

        // 构建最终的过滤条件
        Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

        // 查询并返回结果
        List<String> results = new ArrayList<>();
        collection.find(filter).forEach((Block<? super Document>) doc ->
                results.add(String.format("[%s, %s, %s, %s, %s, %s]",
                        doc.getString("visit_time"),
                        doc.getString("u_id"),
                        doc.getString("query_word"),
                        doc.getString("rank"),
                        doc.getString("ordinal"),
                        doc.getString("url")
                ))
        );
        return results;
    }

    // 删除集合
    public static void dropCollection() {
        collection.drop();
    }
}
