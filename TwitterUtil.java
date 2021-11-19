import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.UnicodeUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import entity.Tweet;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TwitterUtil {
    private static Queue<Tweet> queue = new ConcurrentLinkedQueue<>();
    private static ExecutorService exec = Executors.newFixedThreadPool(10);
    private static String token = "";
    private static String key = "";
    private static String secret = "";
    private static String listId = "";
    //twitter list api
    private static String listApi = "https://api.twitter.com/1.1/lists/statuses.json";
    private static String localSource = "D:/tweets/";

    public static void main(String[] args) throws Exception {
        startCache();
    }

    public static void startCache() {
        try {
            List<Tweet> tweets = getListTweets(listId, null, null);
            if (tweets != null && tweets.size() > 0) {
                queue.addAll(tweets);
                while (!queue.isEmpty()) {
                    exec.execute(new Runnable() {
                        @Override
                        public void run() {
                            Tweet tweet = queue.poll();
                            if (tweet != null) {
                                downloadMedia(tweet);
                            }
                        }
                    });
                }
                exec.awaitTermination(10, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 信任任何站点，实现https页面的正常访问
     */
    public static void trustEveryone() {
        try {
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new X509TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    /**
     * 获取推特列表内容
     *
     * @param listId      列表ID
     * @param startListId 最小推文ID
     * @param count       推文数量
     * @return
     */
    public static List<Tweet> getListTweets(String listId, String startListId, Integer count) {
        try {
            if (StringUtils.isEmpty(listId)) return null;
            trustEveryone();
            System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2,SSLv3");
            String baseUrl = listApi + "?list_id=" + listId;
            if (StringUtils.isNotEmpty(startListId)) {
                baseUrl += "&since_id=" + startListId;
            }
            if (count != null && count > 0) {
                baseUrl += "&count" + count;
            }
            URL url = new URL(baseUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setUseCaches(false);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.connect();

            InputStream is = null;
            if (conn.getResponseCode() == 200) {
                is = conn.getInputStream();
            } else if (conn.getResponseCode() == 400) {
                is = conn.getErrorStream();
            }
            if (is == null) return null;
            BufferedReader in = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            JSONArray array = JSONArray.parseArray(sb.toString());
            if (array != null && array.size() > 0) {
                List<Tweet> list = new ArrayList<>();
                for (Object obj : array) {
                    JSONObject json = (JSONObject) obj;
                    Tweet tweet = formaterTweet(json);
                    if (tweet != null) {
                        list.add(tweet);
                    }
                }
                return list;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 推文内容解析
     * json.id : tweetId
     * json.text: (unicode) tweetText;
     * json.created_at: 创建时间(new Date("Wed Nov 17 14:39:51 +0000 2021"))
     * json.{extended_entities}.[media].id : 视频ID
     * json.{extended_entities}.[media].media_url : 视频缩略图
     * json.{extended_entities}.[media].{video_info}.[variants] : (list[{bitrate:"分辨率",content_type:"video/mp4",url:"视频地址"}]) 视频集合,取分辨率最高的一个
     * json.user.id: 发布用户ID
     * json.user.name: 发布用户名称
     *
     * @param json
     * @return
     */
    private static Tweet formaterTweet(JSONObject json) {
        try {
            if (json == null || !json.containsKey("extended_entities")) return null;
            Tweet tweet = new Tweet();
            tweet.setTwId(json.getString("id"));
            tweet.setTwText(UnicodeUtil.toString(json.getString("text")));
            tweet.setTwTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(json.getString("created_at"))));
            tweet.setMediaId(json.getJSONObject("extended_entities").getJSONArray("media").getJSONObject(0).getString("id"));
            tweet.setMediaPic(json.getJSONObject("extended_entities").getJSONArray("media").getJSONObject(0).getString("media_url"));
            if (json.getJSONObject("extended_entities").getJSONArray("media").size() > 1 || !json.getJSONObject("extended_entities").getJSONArray("media").getJSONObject(0).containsKey("video_info"))
                return null;
            //获取分辨率最高的视频地址
            List<JSONObject> mediaz = json.getJSONObject("extended_entities").getJSONArray("media").getJSONObject(0).getJSONObject("video_info").getJSONArray("variants").toJavaList(JSONObject.class);
            List<JSONObject> medias = mediaz.stream().filter(t -> t.getString("content_type").equals("video/mp4")).sorted((t1, t2) -> {
                return t2.getInteger("bitrate") - t1.getInteger("bitrate");
            }).collect(Collectors.toList());
            tweet.setMediaUrl(medias.get(0).getString("url"));
            tweet.setUserId(json.getJSONObject("user").getString("id"));
            tweet.setUserName(json.getJSONObject("user").getString("name"));
            return tweet;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 下载推文文字,视频和缩略图
     *
     * @param tweet
     */
    private static void downloadMedia(Tweet tweet) {
        try {
            String tweetFile = localSource + "/" + tweet.getTwId() + "/" + tweet.getTwId();
            //保存缩略图
            if (!FileUtil.exist(tweetFile + ".jpg")) {
                saveToFile(tweet.getMediaPic(), tweetFile + ".jpg");
            }
            //保存视频
            if (!FileUtil.exist(tweetFile + ".mp4")) {
                saveToFile(tweet.getMediaUrl(), tweetFile + ".mp4");
            }
            //保存推文内容
            if (!FileUtil.exist(tweetFile + ".txt")) {
                FileUtil.writeString(tweet.getTwText(), tweetFile + ".txt", Charset.forName("utf-8"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * HTTP 获取下载文件
     *
     * @param destUrl  网络文件地址
     * @param fileName 目标文件地址
     * @return
     */
    public static boolean saveToFile(String destUrl, String fileName) {
        FileOutputStream fos = null;
        BufferedInputStream bis = null;
        HttpURLConnection httpUrl = null;
        URL url = null;
        byte[] buf = new byte[1024];
        int size = 0;
        boolean isflag = false;

        try {
            if (StringUtils.isEmpty(destUrl) || StringUtils.isEmpty(fileName)) {
                return false;
            }
            trustEveryone();
            System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
            // 建立链接
            url = new URL(URLDecoder.decode(destUrl, "UTF-8"));
            httpUrl = (HttpURLConnection) url.openConnection();
            // 连接指定的资源
            httpUrl.connect();
            // 获取网络输入流
            bis = new BufferedInputStream(httpUrl.getInputStream());
            // 建立文件
            File file = new File(fileName);
            if (!file.getParentFile().exists()) {
                new File(fileName).getParentFile().mkdirs();
            }
            fos = new FileOutputStream(fileName);
            System.out.println("正在获取链接[" + destUrl + "]的内容...将其保存为文件[" + fileName + "]");
            // 保存文件
            while ((size = bis.read(buf)) != -1) {
                fos.write(buf, 0, size);
            }

            isflag = true;
        } catch (Exception e) {
            System.out.println("目标文件:" + fileName + ",资源路径:" + destUrl + "下载异常:" + e.getMessage());
            e.printStackTrace();
            isflag = false;
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
                if (bis != null) {
                    bis.close();
                }
                if (httpUrl != null) {
                    httpUrl.disconnect();
                }
            } catch (Exception e) {
            }
        }
        return isflag;
    }
}
