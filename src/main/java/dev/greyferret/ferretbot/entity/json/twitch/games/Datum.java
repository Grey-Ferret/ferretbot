
package dev.greyferret.ferretbot.entity.json.twitch.games;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Datum {

    @SerializedName("id")
    @Expose
    private String id;
    @SerializedName("name")
    @Expose
    private String name;
    @SerializedName("box_art_url")
    @Expose
    private String boxArtUrl;
}
