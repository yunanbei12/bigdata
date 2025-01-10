import redis.clients.jedis.Jedis;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.Jedis;
import java.util.*;

public class MyRedis {

    private static final String SEARCH_HISTORY_LIST = "search:history:list"; // 存储查询历史的列表键
    private static final String SEARCH_CACHE_MAP = "search:history:map"; // 存储查询与结果的映射
    private static final int MAX_HISTORY_SIZE = 20; // 缓存最多保存 20 条记录

    // 缓存搜索结果
    public void storeSearchResult(String query, List<String> result) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            // 序列化结果列表为字符串（用分隔符拼接）
            String serializedResult = String.join("|", result);

            // 存储查询结果到哈希结构
            jedis.hset(SEARCH_CACHE_MAP, query, serializedResult);

            // 更新搜索历史列表
            jedis.lrem(SEARCH_HISTORY_LIST, 0, query); // 移除已有的相同查询
            jedis.lpush(SEARCH_HISTORY_LIST, query); // 将新查询放到最前面

            // 确保历史列表不超过指定大小
            if (jedis.llen(SEARCH_HISTORY_LIST) > MAX_HISTORY_SIZE) {
                String oldestQuery = jedis.rpop(SEARCH_HISTORY_LIST); // 删除最老的记录
                jedis.hdel(SEARCH_CACHE_MAP, oldestQuery); // 从缓存中删除对应结果
            }
            System.out.println("缓存成功存储查询：" + query);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 查询缓存中的搜索结果
    public List<String> getSearchResult(String query) {
        try (Jedis jedis = new Jedis("localhost", 6379)) {
            String cachedResult = jedis.hget(SEARCH_CACHE_MAP, query);
            if (cachedResult != null) {
                jedis.lrem(SEARCH_HISTORY_LIST, 0, query); // 更新搜索历史顺序
                jedis.lpush(SEARCH_HISTORY_LIST, query);
                return Arrays.asList(cachedResult.split("\\|")); // 反序列化结果
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // 未找到缓存结果
    }



}

