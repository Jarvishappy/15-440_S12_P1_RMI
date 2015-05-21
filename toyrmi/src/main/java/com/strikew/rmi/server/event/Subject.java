package com.strikew.rmi.server.event;

/**
 * 观察者模式中的Subject，Observer想要关注(订阅)的对象都实现这个接口
 * 比如Skeleton感兴趣的server, thread和worker thread感兴趣的Runnable或Callable
 */
public interface Subject {
    /**
     * State has changed, need to inform Observers
     * Only can be called by Subject instance itself
     * @param arg interesting things will be sent to observers
     */
    public void stateChanged(Object... arg);
}