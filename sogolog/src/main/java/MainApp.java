import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class MainApp {

    public static String path = "/home/hadoop/Desktop/sogolog-master-master/sogolog-master-master/sogolog-master-master/SogouQ.sample";

    public static void main(String[] args) throws IOException {
        MyRedis redis = new MyRedis();
        Scanner scanner = new Scanner(System.in);
        Analyze analyze = new Analyze();
        Statistic statistic = new Statistic();
        int opt;
        System.out.println("请选择要使用的数据库:");
        System.out.println("1. MongoDB");
        System.out.println("2. HBase");
        int dbChoice = scanner.nextInt();
        scanner.nextLine(); // 消耗换行符

        while (true) {
            System.out.print(
                            "*************************\n"+
                            "* 1: 加载数据至数据库\n"+
                            "* 2: 联合搜索\n" +
                            "* 3: RDD联合搜索\n" +
                            "* 4: 时段流量统计\n" +
                            "* 5: 用户使用频率统计\n" +
                            "* 6: 访问行为统计\n" +
                            "* 0: 退出\n"+
                            "*************************\n"
            );
            opt = scanner.nextInt();
            if (opt == 0) {
                break;
            }
            switch (opt) {
                case 1: {
                    if (dbChoice == 1) {
                        MongodbUtil.readFileAndInsertLogs(path);
                        File file = new File(path);
                        if (!file.exists()) {
                            System.out.println("文件不存在，请检查路径：" + path);
                            return;
                        }

                        System.out.println("数据已加载至 MongoDB！");
                    } else {
                        Analyze.createTable(Analyze.tableNameStr, new String[]{new String(Analyze.FAMILY_NAME)}, true);
                        analyze.file2Hbase();
                        System.out.println("数据已加载至 HBase！");
                    }
                    break;
                }

                case 2: {  // 使用不同条件进行联合搜索
                    System.out.print("请输入查询条件([startTime|endTime]+[userID]+[keyword]+[URL])\n>");
                    String input = scanner.next(); // 获取用户输入
                    Logger.logInfo("用户输入的查询条件: " + input);

                    List<String> list;

                    // 查询Redis中是否有对应缓存
                    list = redis.getSearchResult(input);
                    if (list == null) { // Redis中没有缓存数据
                        Logger.logInfo("缓存未命中，执行数据库查询...");
                        System.out.println("redis最近20条搜索记录中没有这个搜索，执行数据库查询...");
                        if (dbChoice == 1) {
                            list = MongodbUtil.query(AppUtil.getQueryTerms(input)); // MongoDB 查询
                        } else {
                            list = analyze.scan(AppUtil.getQueryTerms(input)); // HBase 查询
                        }

                        // 存储新的查询结果到 Redis
                        redis.storeSearchResult(input, list); // 缓存查询结果

                        int fnum = list.size();

                        // 检查查询结果数量
                        if (list.size() > 100) {
                            System.out.println("搜索结果超过100条，显示前100条记录：");
                            list = list.subList(0, 100); // 截取前100条记录
                        }

                        list.forEach(System.out::println); // 输出查询结果
                        System.out.println("查询结果数量(未截取): " + fnum);
                        Logger.logInfo("查询结果数量(未截取): " + fnum);

                        System.out.println("查询结果数量: " + list.size()); // 输出总数，注意这里的总数是截取后的
                        Logger.logInfo("查询结果数量: " + list.size());

                    } else {
                        System.out.println("从缓存中加载查询结果:");
                        list.forEach(System.out::println);
                        System.out.println("total: " + list.size());
                        Logger.logInfo("从缓存中获取到查询结果，总数: " + list.size());
                    }
                    break;
                }
                case 3: { // RDD联合搜索
                    SparkRDD sparkrdd = new SparkRDD();
                    System.out.print("请输入查询条件([startTime|endTime]+[userID]+[keyword]+[URL])\n>");
                    String input = scanner.next();
                    List<String> list = sparkrdd.queryByRDD(AppUtil.getQueryTerms(input)); // 执行 RDD 查询
                   // redis.storeDataInRedis(list);

                    // 检查查询结果数量
                    if (list.size() > 100) {
                        System.out.println("搜索结果超过100条，显示前100条记录：");
                        list = list.subList(0, 100); // 截取前100条记录
                    }

                    list.forEach(System.out::println); // 输出查询结果
                    System.out.println("total: " + list.size()); // 输出总数，注意这里的总数是截取后的
                    break;
                }
                case 4: {
                    System.out.print("请输入以hh:mm:ss格式输入起始时间和结束时间(startTime|endTime)\n>");
                    String input = scanner.next();
                    QueryTerms queryTerms = AppUtil.getStartTimeEndTime(input);
                    System.out.println(queryTerms);
                    Map<String, Object> result = statistic.flowStatisticsByTime(queryTerms.getStartTime(), queryTerms.getEndTime());

                    // 检查结果数量
                    if (result.size() > 100) {
                        System.out.println("结果超过100条，显示前100条记录：");
                        result = result.entrySet().stream()
                                .limit(100) // 限制为100条
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    }
                    System.out.println(AppUtil.obj2Json(result));
                    break;
                }
                case 5: {
                    Map<String, Long> countByUser = statistic.countByUser();

                    // 检查结果数量
                    if (countByUser.size() > 100) {
                        System.out.println("结果超过100条，显示前100条记录：");
                        countByUser = countByUser.entrySet().stream()
                                .limit(100) // 限制为100条
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    }
                    System.out.println(AppUtil.obj2Json(countByUser));
                    break;
                }
                case 6: {
                    Map<String, Long> statisticsMap = statistic.accessBehaviorStatistics();

                    // 检查结果数量
                    if (statisticsMap.size() > 100) {
                        System.out.println("结果超过100条，显示前100条记录：");
                        statisticsMap = statisticsMap.entrySet().stream()
                                .limit(100) // 限制为100条
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    }
                    System.out.println(AppUtil.obj2Json(statisticsMap));
                    break;
                }
            }
        }
        scanner.close(); // 关闭扫描器
    }
}
