package com.aicommerce.platform.delivery.application;

public enum PlatformWebAccountLane {
    META, GOOGLE;
    private static final ThreadLocal<PlatformWebAccountLane> CURRENT=ThreadLocal.withInitial(()->META);
    public static PlatformWebAccountLane current(){return CURRENT.get();}
    public static void set(PlatformWebAccountLane lane){CURRENT.set(lane==null?META:lane);}
    public static void clear(){CURRENT.remove();}
}
