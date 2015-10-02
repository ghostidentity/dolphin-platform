package com.canoo.dolphin.server.event.impl;

import com.canoo.dolphin.server.context.DolphinContext;
import com.canoo.dolphin.server.context.DolphinContextHandler;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

public class DolphinSessionHandlerCleaner implements HttpSessionListener {

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        System.out.println("");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        for(DolphinContext dolphinContext : DolphinContextHandler.getContexts(se.getSession())) {
            DolphinEventBusImpl.getInstance().unsubscribeSession(dolphinContext.getId());
        }
    }
}