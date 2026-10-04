package com.yoursay.platform.mobiletelemetry.service;

import java.util.Set;

/**
 * Every screen and action name the app may report. Anything else collapses to {@code other}, so an
 * old or tampered client can never mint a new metric label.
 *
 * <p>Keep in sync with {@code features/telemetry/vocabulary.ts} in the Expo app.
 */
final class MobileVocabulary {

    static final String OTHER = "other";

    /** expo-router route templates with route groups removed. */
    static final Set<String> SCREENS = Set.of(
            "/",
            "/sign-in",
            "/consent",
            "/usercharacteristics",
            "/create-post",
            "/posts/[postId]",
            "/posts/[postId]/results",
            "/posts/[postId]/unwrapped",
            "/profiles/me",
            "/profiles/[userId]",
            "/profiles/[userId]/connections",
            "/account",
            "/settings",
            "/about/your-say-news");

    static final Set<String> ACTIONS = Set.of(
            "auth.sign_in",
            "auth.sign_out",
            "consent.accept",
            "onboarding.next",
            "onboarding.back",
            "onboarding.submit",
            "feed.topic_select",
            "feed.type_filter",
            "feed.refresh",
            "feed.load_more",
            "feed.compose_open",
            "feed.account_open",
            "feed.post_view",
            "post.topic_select",
            "post.author_open",
            "post.share",
            "post.summary_toggle",
            "post.source_open",
            "post.video_mute_toggle",
            "vote.cast",
            "vote.choice_open",
            "vote.results_open",
            "results.axis_select",
            "results.view_select",
            "results.retry",
            "unwrapped.unwrap",
            "unwrapped.page_next",
            "unwrapped.page_back",
            "unwrapped.follow_up",
            "unwrapped.source_open",
            "create_post.mode_select",
            "create_post.media_pick",
            "create_post.submit",
            "create_post.pepper_generate",
            "profile.follow",
            "profile.unfollow",
            "profile.connections_open",
            "connections.tab_select",
            "account.profile_open",
            "account.settings_open",
            "settings.theme_select");

    private MobileVocabulary() {
    }

    static String screen(String value) {
        return SCREENS.contains(value) ? value : OTHER;
    }

    static String action(String value) {
        return ACTIONS.contains(value) ? value : OTHER;
    }
}
