package jp.satorufujiwara.player.ima;

import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.CompanionAdSlot;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import jp.satorufujiwara.player.Player;
import jp.satorufujiwara.player.VideoSource;
import jp.satorufujiwara.player.VideoTexturePresenter;
import jp.satorufujiwara.player.assets.AssetsVideoSource;

public class AdPlayerController {

    private boolean isAdDisplayed = false;
    private boolean isAdPlaying = false;
    private final VideoAdPlayer videoAdPlayer;
    private final ContentProgressProvider contentProgressProvider;
    private final ViewGroup adUiContainer;
    private final ImaSdkFactory sdkFactory;
    private final AdsLoader adsLoader;
    private final ViewGroup companionContainer;
    private final int companionWidth;
    private final int companionHeight;
    private final List<VideoAdPlayer.VideoAdPlayerCallback> adCallbacks = new ArrayList<>(1);
    private final List<AdEvent.AdEventListener> adEventListeners = new ArrayList<>();
    private final List<AdErrorEvent.AdErrorListener> adErrorListeners = new ArrayList<>();
    private final List<AdProgressUpdatedListener> adProgressListeners = new ArrayList<>();
    private AdsManager adsManager;
    private OnResumeContentListener resumeContentListener;
    private OnPauseContentListener pauseContentListener;

    public static Builder builder(final Context context) {
        return new Builder(context);
    }

    AdPlayerController(final Context context, final String language,
            final String userAgent, final VideoTexturePresenter videoTexturePresenter,
            final ViewGroup adUiContainer, final ViewGroup companionContainer,
            final int companionWidth, final int companionHeight) {
        this.adUiContainer = adUiContainer;
        this.companionContainer = companionContainer;
        this.companionWidth = companionWidth;
        this.companionHeight = companionHeight;
        videoTexturePresenter.addOnStateChangedListener(
                new VideoTexturePresenter.OnStateChangedListener() {
                    @Override
                    public void onStateChanged(boolean playWhenReady, int playbackState) {
                        if (!isAdDisplayed) {
                            return;
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                                callback.onEnded();
                            }
                            return;
                        }
                        if (playbackState != Player.STATE_READY) {
                            return;
                        }
                        if (playWhenReady) {
                            for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                                callback.onPlay();
                            }
                        } else {
                            for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                                callback.onPause();
                            }
                        }
                    }
                });
        videoTexturePresenter.addOnErrorListener(new VideoTexturePresenter.OnErrorListener() {
            @Override
            public void onError(Exception e) {
                if (!isAdDisplayed) {
                    return;
                }
                for (VideoAdPlayer.VideoAdPlayerCallback callback : adCallbacks) {
                    callback.onError();
                }
            }
        });
        videoAdPlayer = new VideoAdPlayer() {
            @Override
            public void playAd() {
                isAdDisplayed = true;
                videoTexturePresenter.play();
            }

            @Override
            public void loadAd(String url) {
                isAdDisplayed = true;
                videoTexturePresenter.setSource(createVideoSourceFrom(url, userAgent));
                videoTexturePresenter.prepare();
            }

            @Override
            public void stopAd() {
                videoTexturePresenter.stop();
                videoTexturePresenter.release();
            }

            @Override
            public void pauseAd() {
                videoTexturePresenter.pause();
            }

            @Override
            public void resumeAd() {
                playAd();
            }

            @Override
            public void addCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
                adCallbacks.add(videoAdPlayerCallback);
            }

            @Override
            public void removeCallback(VideoAdPlayerCallback videoAdPlayerCallback) {
                adCallbacks.remove(videoAdPlayerCallback);
            }

            @Override
            public VideoProgressUpdate getAdProgress() {
                if (!isAdDisplayed || videoTexturePresenter.getDuration() <= 0) {
                    return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
                }
                VideoProgressUpdate progress = new VideoProgressUpdate(
                        videoTexturePresenter.getCurrentPosition(),
                        videoTexturePresenter.getDuration());
                for (AdProgressUpdatedListener l : adProgressListeners) {
                    l.onProgressUpdated(progress);
                }
                return progress;
            }
        };
        contentProgressProvider = new ContentProgressProvider() {
            @Override
            public VideoProgressUpdate getContentProgress() {
                if (isAdDisplayed || videoTexturePresenter.getDuration() <= 0) {
                    return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
                }
                return new VideoProgressUpdate(videoTexturePresenter.getCurrentPosition(),
                        videoTexturePresenter.getDuration());
            }
        };

        ImaSdkSettings imaSdkSettings = new ImaSdkSettings();
        imaSdkSettings.setLanguage(language);
        sdkFactory = ImaSdkFactory.getInstance();
        adsLoader = sdkFactory.createAdsLoader(context, imaSdkSettings);
        adsLoader.addAdErrorListener(new AdErrorEvent.AdErrorListener() {
            /**
             * An event raised when there is an error loading or playing ads.
             */
            @Override
            public void onAdError(AdErrorEvent adErrorEvent) {
                requestResumeContent();
            }
        });
        adsLoader.addAdsLoadedListener(new AdsLoader.AdsLoadedListener() {
            /**
             * An event raised when ads are successfully loaded from the ad server via AdsLoader.
             */
            @Override
            public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
                adsManager = adsManagerLoadedEvent.getAdsManager();
                adsManager.addAdErrorListener(new AdErrorEvent.AdErrorListener() {
                    /**
                     * An event raised when there is an error loading or playing ads.
                     */
                    @Override
                    public void onAdError(AdErrorEvent adErrorEvent) {
                        requestResumeContent();
                    }
                });
                adsManager.addAdEventListener(new AdEvent.AdEventListener() {
                    @Override
                    public void onAdEvent(AdEvent adEvent) {
                        switch (adEvent.getType()) {
                            case LOADED:
                                adsManager.start();
                                break;
                            case CONTENT_PAUSE_REQUESTED:
                                requestPauseContent();
                                break;
                            case CONTENT_RESUME_REQUESTED:
                                requestResumeContent();
                                break;
                            case PAUSED:
                                isAdPlaying = false;
                                break;
                            case RESUMED:
                                isAdPlaying = true;
                                break;
                            case ALL_ADS_COMPLETED:
                                if (adsManager != null) {
                                    adsManager.destroy();
                                    adsManager = null;
                                }
                                break;
                            default:
                                break;
                        }
                    }
                });
                for (AdEvent.AdEventListener l : adEventListeners) {
                    adsManager.addAdEventListener(l);
                }
                for (AdErrorEvent.AdErrorListener l : adErrorListeners) {
                    adsManager.addAdErrorListener(l);
                }
                adsManager.init();
            }
        });
    }

    public void requestAndPlayAds(final String adTagUrl) {
        if (TextUtils.isEmpty(adTagUrl)) {
            requestResumeContent();
            return;
        }
        completeAd();

        final AdDisplayContainer container = sdkFactory.createAdDisplayContainer();
        container.setPlayer(videoAdPlayer);
        container.setAdContainer(adUiContainer);

        if (companionContainer != null) {
            CompanionAdSlot companionAdSlot = sdkFactory.createCompanionAdSlot();
            companionAdSlot.setContainer(companionContainer);
            companionAdSlot.setSize(companionWidth, companionHeight);
            ArrayList<CompanionAdSlot> companionAdSlots = new ArrayList<>();
            companionAdSlots.add(companionAdSlot);
            container.setCompanionSlots(companionAdSlots);
        }

        final AdsRequest request = sdkFactory.createAdsRequest();
        request.setAdTagUrl(adTagUrl);
        request.setAdDisplayContainer(container);
        request.setContentProgressProvider(contentProgressProvider);

        adsLoader.requestAds(request);
    }

    public boolean isAdPlaying() {
        return isAdPlaying;
    }

    public void pause() {
        if (adsManager != null && isAdDisplayed) {
            adsManager.pause();
        }
    }

    public void resume() {
        if (adsManager != null && isAdDisplayed) {
            adsManager.resume();
        }
    }

    public VideoAdPlayer getVideoAdPlayer() {
        return videoAdPlayer;
    }

    public void addAdEventListener(final AdEvent.AdEventListener l) {
        adEventListeners.add(l);
        if (adsManager != null) {
            adsManager.addAdEventListener(l);
        }
    }

    public void addAdErrorListener(final AdErrorEvent.AdErrorListener l) {
        adErrorListeners.add(l);
        adsLoader.addAdErrorListener(l);
        if (adsManager != null) {
            adsManager.addAdErrorListener(l);
        }
    }

    public void removeAdEventListener(final AdEvent.AdEventListener l) {
        adEventListeners.remove(l);
        if (adsManager != null) {
            adsManager.removeAdEventListener(l);
        }
    }

    public void removeAdErrorListener(final AdErrorEvent.AdErrorListener l) {
        adErrorListeners.remove(l);
        adsLoader.removeAdErrorListener(l);
        if (adsManager != null) {
            adsManager.removeAdErrorListener(l);
        }
    }

    public void addAdProgressUpdatedListener(final AdProgressUpdatedListener l) {
        adProgressListeners.add(l);
    }

    public void removeAdProgressUpdatedListener(final AdProgressUpdatedListener l) {
        adProgressListeners.remove(l);
    }

    public void setOnResumeContentListener(final OnResumeContentListener l) {
        resumeContentListener = l;
    }

    public void setOnPauseContentListener(final OnPauseContentListener l) {
        pauseContentListener = l;
    }

    private void completeAd() {
        if (adsManager != null) {
            adsManager.destroy();
        }
        adsLoader.contentComplete();
    }

    private VideoSource createVideoSourceFrom(String url, String userAgent) {
        return AssetsVideoSource
                .newBuilder(Uri.parse(url), userAgent)
                .bufferSegmentSize(16 * 1024)
                .bufferSegmentCount(128)
                .build();
    }

    private void requestResumeContent() {
        isAdDisplayed = false;
        isAdPlaying = false;
        if (resumeContentListener != null) {
            resumeContentListener.onResumeContentRequested();
        }
    }

    private void requestPauseContent() {
        isAdPlaying = true;
        if (pauseContentListener != null) {
            pauseContentListener.onPauseContentRequested();
        }
    }

    public interface OnResumeContentListener {

        void onResumeContentRequested();
    }

    public interface OnPauseContentListener {

        void onPauseContentRequested();
    }

    public static class Builder {

        private final Context context;
        private VideoTexturePresenter videoTexturePresenter;
        private ViewGroup adUiContainer;
        private String language = "en";
        private String userAgent = "UserAgent";
        private ViewGroup companionContainer;
        private int companionWidth;
        private int companionHeight;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setVideoTexturePresenter(final VideoTexturePresenter videoTexturePresenter) {
            this.videoTexturePresenter = videoTexturePresenter;
            return this;
        }

        public Builder setAdUiContainer(final ViewGroup adUiContainer) {
            this.adUiContainer = adUiContainer;
            return this;
        }

        public Builder setLanguage(final String language) {
            this.language = language;
            return this;
        }

        public Builder setUserAgent(final String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder setCompanionContainer(final ViewGroup companionContainer) {
            this.companionContainer = companionContainer;
            return this;
        }

        public Builder setCompanionSize(final int companionWidth, final int companionHeight) {
            this.companionWidth = companionWidth;
            this.companionHeight = companionHeight;
            return this;
        }

        public AdPlayerController create() {
            if (context == null) {
                throw new RuntimeException("Context must not be null.");
            }
            if (videoTexturePresenter == null) {
                throw new RuntimeException("VideoTexturePresenter must not be null.");
            }
            if (adUiContainer == null) {
                throw new RuntimeException("AdUiContainer must not be null.");
            }
            return new AdPlayerController(context, language, userAgent, videoTexturePresenter,
                    adUiContainer, companionContainer, companionWidth, companionHeight);
        }

    }

}