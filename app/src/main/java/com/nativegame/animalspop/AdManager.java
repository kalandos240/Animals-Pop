package com.nativegame.animalspop;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoader;

/** Opt-in rewarded ads. Each impression owns its listener and settles once. */
public final class AdManager {
    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private RewardedAdLoader loader;
    private RewardedAd ad;
    private AdRewardListener listener;
    private boolean initialized, loading, showing, destroyed;

    public AdManager(Activity activity) {
        this.activity = activity;
        YandexAds.initialize(activity, () -> main.post(() -> {
            if (destroyed) return;
            loader = new RewardedAdLoader(activity);
            initialized = true;
            requestAd();
        }));
    }

    public void requestAd() {
        if (!initialized || loading || showing || ad != null || destroyed
                || BuildConfig.REWARDED_AD_UNIT_ID.isEmpty()) return;
        loading = true;
        loader.loadAd(new AdRequest.Builder(BuildConfig.REWARDED_AD_UNIT_ID).build(),
                new RewardedAdLoadListener() {
                    @Override public void onAdLoaded(RewardedAd loaded) {
                        loading = false;
                        if (!destroyed) ad = loaded;
                    }
                    @Override public void onAdFailedToLoad(AdRequestError error) {
                        loading = false;
                        // Retry only on explicit user action; never loop failed requests.
                    }
                });
    }

    public boolean showRewardAd() {
        if (destroyed || showing || ad == null || listener == null
                || activity.isFinishing() || activity.isDestroyed()) return false;
        final RewardedAd current = ad;
        final AdRewardListener recipient = listener;
        ad = null;
        showing = true;
        current.setAdEventListener(new RewardedAdEventListener() {
            private boolean earned, settled;
            @Override public void onAdShown() { }
            @Override public void onAdClicked() { }
            @Override public void onAdImpression(ImpressionData data) { }
            @Override public void onRewarded(Reward reward) { earned = true; }
            @Override public void onAdDismissed() { finish(); }
            @Override public void onAdFailedToShow(AdError error) { finish(); }
            private void finish() {
                if (settled) return;
                settled = true;
                current.setAdEventListener(null);
                showing = false;
                // Defer until showRewardAd returns and the caller has paused the game.
                main.post(() -> {
                    if (destroyed) return;
                    if (earned) recipient.onEarnReward(); else recipient.onLossReward();
                    requestAd();
                });
            }
        });
        current.show(activity);
        return true;
    }

    public void setListener(AdRewardListener listener) { this.listener = listener; }

    public void destroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (ad != null) ad.setAdEventListener(null);
        ad = null;
        loader = null;
        listener = null;
    }

    public interface AdRewardListener {
        void onEarnReward();
        void onLossReward();
    }
}
