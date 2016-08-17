package jp.satorufujiwara.player.ima;

import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;

public interface AdProgressUpdatedListener {

    void onProgressUpdated(VideoProgressUpdate progress);

}
