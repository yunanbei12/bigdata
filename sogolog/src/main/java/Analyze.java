import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapred.TableOutputFormat;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapred.JobConf;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Analyze {
    public static MyRedis Redis;
    //定义 HBase 表中的列族和列
    public static final byte[] FAMILY_NAME = Bytes.toBytes("infos");      // 列族
    public final static byte[] VISIT_TIME = Bytes.toBytes("visit_time");  // 访问时间
    public final static byte[] U_ID = Bytes.toBytes("u_id");              // 用户ID
    public final static byte[] QUERY_WORD = Bytes.toBytes("query_word");  // [查询词]
    public final static byte[] RANK = Bytes.toBytes("rank");              // 该URL在返回结果中的排名
    public final static byte[] ORDINAL = Bytes.toBytes("ordinal");        // 用户点击的顺序号
    public final static byte[] URL = Bytes.toBytes("url");                // 用户点击的URL
    // 数组 ["访问时间", "用户ID", "[查询词]", "该URL在返回结果中的排名", "用户点击的顺序号", "用户点击的URL"] 的下标
    public final static int VISIT_TIME_INDEX = 0;   // 访问时间
    public final static int U_ID_INDEX = 1;         // 用户ID
    public final static int QUERY_WORD_INDEX = 2;   // [查询词]
    public final static int RANK_INDEX = 3;         // 该URL在返回结果中的排名
    public final static int ORDINAL_INDEX = 4;      // 用户点击的顺序号
    public final static int URL_INDEX = 5;          // 用户点击的URL

    // 配置信息
    public static String path = "/home/hadoop/Desktop/sogolog-master-master/sogolog-master-master/sogolog-master-master/SogouQ.sample";
    public final static String tableNameStr = "SogoLog";
    public static final String hbaseRootDir = "hdfs://192.168.88.101:8020/hbase";

    public static final JavaSparkContext sc;
    //配置Spark
    static {
        SparkConf sparkConf = new SparkConf()
                .setMaster("local")
                .setAppName("SparkApp");
        sc = new JavaSparkContext(sparkConf);   //Spark 的 Java API，管理RDD操作
    }


    /**
     * 创建表，要求当 HBase 已经存在名为 tableName 的表的时候，先删除原有的表，再创建新的表。
     *
     * @param tableName 表的名称
     * @param fields    存储记录各个域名称的数组
     * @param isCover   是否覆盖
     */
    public static void createTable(String tableName, String[] fields, boolean isCover) {
        TableName createTableName = TableName.valueOf(tableName);
        Configuration hbConf = HBaseConfiguration.create();
        hbConf.set("hbase.rootdir", hbaseRootDir);
        try (Connection hbConn = ConnectionFactory.createConnection(hbConf)) {
            Admin admin = hbConn.getAdmin();
            if (admin.tableExists(createTableName)) {
                System.out.println("table is exists!");
                if (isCover) {
                    admin.disableTable(createTableName);
                    admin.deleteTable(createTableName);
                } else {
                    return;
                }
            }
            HTableDescriptor hTableDescriptor = new HTableDescriptor(createTableName);
            for (String field : fields) {
                hTableDescriptor.addFamily(new HColumnDescriptor(field));
            }
            admin.createTable(hTableDescriptor);
            System.out.println("----------------\ncreate table ok!");
            admin.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * （1）数据清洗部分：实现一个程序，载入文本中的日志数据，
     * 每行作为一条记录，分为访问时间，用户ID，查询词，返回结果排名，顺序号，URL这六个字段（列），
     * 存入HBASE。
     * logger
     */

    public void file2Hbase() {
        Logger.logStart("file2Hbase"); // 记录任务开始

        // 1、spark 读取数据、清洗数据
        // 数据格式：访问时间 用户ID    [查询词]   该URL在返回结果中的排名   用户点击的顺序号    用户点击的URL
        //Spark 的 textFile 方法读取文本数据 将每行按空格拆分为字段数组
        JavaRDD<String[]> rdd = sc.textFile(path).map(s -> s.split("\\s"));

        // 2、HBase Configuration
        Configuration hbConf = HBaseConfiguration.create();
        hbConf.set("hbase.rootdir", hbaseRootDir);

        // 3、JobConf
        JobConf jobConf = new JobConf(hbConf, this.getClass());
        jobConf.setOutputFormat(TableOutputFormat.class);
        jobConf.set(TableOutputFormat.OUTPUT_TABLE, tableNameStr);

        // 4、rdd -> pairRDD
        JavaPairRDD<ImmutableBytesWritable, Put> pairRDD = rdd.mapToPair(line -> {
            byte[] rowKey = Bytes.toBytes(AppUtil.getUUID());
            ImmutableBytesWritable writable = new ImmutableBytesWritable(rowKey);
            Put put = new Put(rowKey);
            put.addColumn(FAMILY_NAME, VISIT_TIME, Bytes.toBytes(line[VISIT_TIME_INDEX]));
            put.addColumn(FAMILY_NAME, U_ID, Bytes.toBytes(line[U_ID_INDEX]));
            put.addColumn(FAMILY_NAME, QUERY_WORD, Bytes.toBytes(line[QUERY_WORD_INDEX]));
            put.addColumn(FAMILY_NAME, RANK, Bytes.toBytes(line[RANK_INDEX]));
            put.addColumn(FAMILY_NAME, ORDINAL, Bytes.toBytes(line[ORDINAL_INDEX]));
            put.addColumn(FAMILY_NAME, URL, Bytes.toBytes(line[URL_INDEX]));

            return new Tuple2<>(writable, put);
        });
        // 5、save as Hbase
        pairRDD.saveAsHadoopDataset(jobConf);
        Logger.logEnd("file2Hbase"); // 记录任务结束
    }

    //数据查询
    public List<String> scan(QueryTerms queryTerms) throws IOException {

        Logger.logStart("scan"); // 记录任务开始

        // 创建 HBase 连接 hbConn 和获取表对象 table，用于后续的扫描操作。
        Configuration hbConf = HBaseConfiguration.create();
        hbConf.set("hbase.rootdir", hbaseRootDir);
        Connection hbConn = ConnectionFactory.createConnection(hbConf);
        Table table = hbConn.getTable(TableName.valueOf(tableNameStr));

        Scan scan = new Scan(); //定义对 HBase 表的扫描操作
        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL); //构建过滤条件，MUST_PASS_ALL 表示所有条件都必须满足（AND）

        if (StringUtils.isNotBlank(queryTerms.getStartTime())) {// infos.visit_time >= startTime
            filterList.addFilter(new SingleColumnValueFilter(FAMILY_NAME, VISIT_TIME,
                    CompareOperator.GREATER_OR_EQUAL, Bytes.toBytes(queryTerms.getStartTime())));
        }
        if (StringUtils.isNotBlank(queryTerms.getEndTime())) {// infos.visit_time <= endTime
            filterList.addFilter(new SingleColumnValueFilter(FAMILY_NAME, VISIT_TIME,
                    CompareOperator.LESS_OR_EQUAL, Bytes.toBytes(queryTerms.getEndTime())));
        }

        if (CollectionUtils.isNotEmpty(queryTerms.getUserIds())) {// infos.u_id in userIds
            FilterList uidFilterList = new FilterList(FilterList.Operator.MUST_PASS_ONE); // OR
            for (String uid : queryTerms.getUserIds()) {
                uidFilterList.addFilter(new SingleColumnValueFilter(FAMILY_NAME, U_ID,
                        CompareOperator.EQUAL, new SubstringComparator(uid)));
            }
            filterList.addFilter(uidFilterList);
        }

        if (CollectionUtils.isNotEmpty(queryTerms.getKeyWords())) {// infos.query_word in keywords
            FilterList kwFilterList = new FilterList(FilterList.Operator.MUST_PASS_ONE);
            for (String kw : queryTerms.getKeyWords()) {
                kwFilterList.addFilter(new SingleColumnValueFilter(FAMILY_NAME, QUERY_WORD,
                        CompareOperator.EQUAL, new SubstringComparator(kw)));
            }
            filterList.addFilter(kwFilterList);
        }

        if (CollectionUtils.isNotEmpty(queryTerms.getUrls())) {// infos.url in urls
            FilterList kwFilterList = new FilterList(FilterList.Operator.MUST_PASS_ONE);
            for (String url : queryTerms.getUrls()) {
                kwFilterList.addFilter(new SingleColumnValueFilter(FAMILY_NAME, URL,
                        CompareOperator.EQUAL, new SubstringComparator(url)));
            }
            filterList.addFilter(kwFilterList);
        }
        scan.setFilter(filterList);
        System.out.println(scan.toJSON());
        List<String> resultList = new ArrayList<>(16);
        try (ResultScanner results = table.getScanner(scan)) {
            for (Result result : results) {
                resultList.add(String.format("[%s, %s, %s, %s, %s, %s]",
                        new String(CellUtil.cloneValue(result.getColumnCells(FAMILY_NAME, VISIT_TIME).get(0)), StandardCharsets.UTF_8),
                        new String(CellUtil.cloneValue(result.getColumnCells(FAMILY_NAME, U_ID).get(0)), StandardCharsets.UTF_8),
                        new String(CellUtil.cloneValue(result.getColumnCells(FAMILY_NAME, QUERY_WORD).get(0)), StandardCharsets.UTF_8),
                        new String(CellUtil.cloneValue(result.getColumnCells(FAMILY_NAME, RANK).get(0)), StandardCharsets.UTF_8),
                        new String(CellUtil.cloneValue(result.getColumnCells(FAMILY_NAME, ORDINAL).get(0)), StandardCharsets.UTF_8),
                        new String(CellUtil.cloneValue(result.getColumnCells(FAMILY_NAME, URL).get(0)), StandardCharsets.UTF_8)
                ));
            }
        }
        Logger.logEnd("scan"); // 记录任务结束

        return resultList;
    }
}
