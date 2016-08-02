exoplayer-ima
===

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://api.bintray.com/packages/satorufujiwara/maven/exoplayer-ima/images/download.svg)](https://bintray.com/satorufujiwara/maven/exoplayer-ima/_latestVersion)

*This library is experimental. API and all codes will be changed without notice*

[Google Interactive Media Ads (IMA) SDK](https://developers.google.com/interactive-media-ads/docs/sdks/android/)'s wrapper for using with [ExoPlayer](https://github.com/google/ExoPlayer) and TextureView.

This library depends on [exoplayer-textureview](https://github.com/satorufujiwara/exoplayer-textureview)(0.6.8+)

# Features
* Request and track VAST ads.

# Gradle

```groovy
repositories {
    jcenter()
}
dependencies {
    compile 'jp.satorufujiwara:exoplayer-ima:0.2.3'
    compile 'jp.satorufujiwara:exoplayer-textureview:0.6.8'
    compile 'com.google.android.exoplayer:exoplayer:r1.5.9'
    compile 'com.google.ads.interactivemedia.v3:interactivemedia:3.2.1'
}
```

# Usage

```java
@Override
public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    videoTexturePresenter = new VideoTexturePresenter(videoTextureView);
    videoTexturePresenter.onCreate();
    adPlayerController = AdPlayerController.builder(getActivity())
                    .setVideoTexturePresenter(videoTexturePresenter)
                    .setAdUiContainer(adUiContainer)
                    .create();

    // request and play VAST ad.
    adPlayerController.requestAndPlayAds("VAST ad url.");
}

@Override
public void onResume() {
    super.onResume();
    adPlayerController.resume();
}

@Override
public void onPause() {
    adPlayerController.pause();
    super.onPause();
}

@Override
public void onDestroyView() {
    videoTexturePresenter.release();
    videoTexturePresenter.onDestroy();
    super.onDestroyView();
}
```

License
-------

    Copyright 2015 Satoru Fujiwara

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

ExoPlayer.

    Copyright (C) 2014 The Android Open Source Project
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
