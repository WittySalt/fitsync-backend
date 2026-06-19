package com.fitsync.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fitsync.analytics")
public class AnalyticsProperties {

    private Trends trends = new Trends();

    public Trends getTrends() {
        return trends;
    }

    public void setTrends(Trends trends) {
        this.trends = trends == null ? new Trends() : trends;
    }

    public static class Trends {

        private MaxRangeDays maxRangeDays = new MaxRangeDays();

        public MaxRangeDays getMaxRangeDays() {
            return maxRangeDays;
        }

        public void setMaxRangeDays(MaxRangeDays maxRangeDays) {
            this.maxRangeDays = maxRangeDays == null ? new MaxRangeDays() : maxRangeDays;
        }
    }

    public static class MaxRangeDays {

        private int day = 370;
        private int week = 1095;
        private int month = 1830;

        public int getDay() {
            return day;
        }

        public void setDay(int day) {
            this.day = day;
        }

        public int getWeek() {
            return week;
        }

        public void setWeek(int week) {
            this.week = week;
        }

        public int getMonth() {
            return month;
        }

        public void setMonth(int month) {
            this.month = month;
        }
    }
}
