package entity;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推特内容实体
 * json.id : tweetId
 * json.text: (unicode) tweetText;
 * json.created_at: 创建时间(new Date("Wed Nov 17 14:39:51 +0000 2021"))
 * json.{extended_entities}.[media].id : 视频ID
 * json.{extended_entities}.[media].media_url : 视频缩略图
 * json.{extended_entities}.[media].{video_info}.[variants] : (list[{bitrate:"分辨率",content_type:"video/mp4",url:"视频地址"}]) 视频集合,取分辨率最高的一个
 * json.user.id: 发布用户ID
 * json.user.name: 发布用户名称
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tweet {
    private String twId; //推文ID
    private String twText;  //推文内容
    private String twTime;  //发推时间
    private String mediaId;  //视频ID
    private String mediaPic; //视频缩略图
    private String mediaUrl; //视频地址
    private String userId;   //发推用户ID
    private String userName; //发推用户名

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}
