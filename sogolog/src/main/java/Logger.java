import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private static final String LOG_FILE_PATH = "/home/hadoop/Desktop/sogolog-master-master/sogolog-master-master/sogolog-master-master/logger.txt"; // 日志文件路径

    // 日期时间格式化
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 记录开始日志
    public static void logStart(String taskName) {
        String logMessage = String.format("[%s] Task Started: %s", LocalDateTime.now().format(formatter), taskName);
        writeLog(logMessage);
    }
    // 记录结束日志
    public static void logEnd(String taskName) {
        String logMessage = String.format("[%s] Task Ended: %s", LocalDateTime.now().format(formatter), taskName);
        writeLog(logMessage);
    }

    // 记录任务失败日志
    public static void logFailure(String taskName, String errorMessage) {
        String logMessage = String.format("[%s] ERROR: Task Failed: %s - %s",
                LocalDateTime.now().format(formatter), taskName, errorMessage);
        writeLog(logMessage);
    }
    // 记录信息日志
    public static void logInfo(String message) {
        String logMessage = String.format("[%s] INFO: %s", LocalDateTime.now().format(formatter), message);
        writeLog(logMessage);
    }
    // 写入日志文件
    private static synchronized void writeLog(String message) { // 确保线程安全
        try {
            Files.write(Paths.get(LOG_FILE_PATH), (message + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace(); // 可以考虑用其他方式记录异常
        }
    }
}
