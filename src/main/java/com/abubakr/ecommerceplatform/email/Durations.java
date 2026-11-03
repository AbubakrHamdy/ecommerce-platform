package com.abubakr.ecommerceplatform.email;

import java.time.Duration;

final class Durations {

    private Durations() {
    }

    // "30 minutes", "1 minute", "20 seconds": configured windows aren't always whole minutes.
    static String humanize(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes >= 1) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long seconds = Math.max(duration.toSeconds(), 1);
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
