import java.io.Serializable;
import java.util.List;
import java.util.StringJoiner;

/**
 * 封装查询条件
 */
public class QueryTerms implements Serializable {
    private static final long serialVersionUID = 430581180267482563L;
    // 访问时间范围
    private String startTime;
    private String endTime;
    // 用户ID
    private List<String> userIds;
    // [查询词]
    private List<String> keyWords;
    // 用户点击的URL
    private List<String> urls;
    public QueryTerms(String startTime, String endTime, List<String> userIds, List<String> keyWord, List<String> url) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.userIds = userIds;
        this.keyWords = keyWord;
        this.urls = url;
    }
    public QueryTerms() {
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }

    public List<String> getKeyWords() {
        return keyWords;
    }

    public void setKeyWords(List<String> keyWords) {
        this.keyWords = keyWords;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", QueryTerms.class.getSimpleName() + "[", "]")
                .add("startTime='" + startTime + "'")
                .add("endTime='" + endTime + "'")
                .add("userId=" + userIds)
                .add("keyWord=" + keyWords)
                .add("url=" + urls)
                .toString();
    }
}
